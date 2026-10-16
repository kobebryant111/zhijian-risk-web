package com.zhijian.risk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhijian.risk.client.BaiduSearchClient;
import com.zhijian.risk.client.LlmClient;
import com.zhijian.risk.domain.Partner;
import com.zhijian.risk.domain.RiskClue;
import com.zhijian.risk.domain.WhistleReport;
import com.zhijian.risk.engine.RuleCatalog;
import com.zhijian.risk.engine.SearchCore;
import com.zhijian.risk.mapper.PartnerMapper;
import com.zhijian.risk.mapper.RiskClueMapper;
import com.zhijian.risk.mapper.RiskRuleMapper;
import com.zhijian.risk.mapper.WhistleReportMapper;
import com.zhijian.risk.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 风险吹哨：千帆自然语言检索、规则定级、本次跑批报告。
 */
@Service
public class WhistleService {
    private static final Logger log = LoggerFactory.getLogger(WhistleService.class);
    private static final String CURSOR_KEY = "whistle.batchCursor";
    private static final String AI_SYSTEM = """
            你是消费金融「合作机构风险吹哨」分析员。根据全网检索到的公开网页信息，判断是否构成对该合作机构的风险信号，并给出四级预警。

            分级标准（必须遵守）：
            1级：重大违法违规、监管行政处罚/立案、恶性舆情或群体事件，需立即处置上报
            2级：明确违规或较大负面（如暴力催收、严重投诉爆发、失信执行等），需限期处置
            3级：需持续关注的风险苗头或一般负面报道
            4级：弱相关或信息价值有限，可留档跟踪
            若不相关、无法核实指向该机构、或纯广告软文：relevant=false，level 可填 0

            硬约束：
            - 只依据给定材料，禁止编造未出现的事实、链接或处罚文号
            - 必须逐条输出，key 与输入完全一致
            - 只输出 JSON 数组，不要 markdown 代码围栏，不要额外说明

            输出项字段：
            [{"key":"...","relevant":true,"level":1,"analysis":"50-120字研判","risk_point":"风险点简称","legal_basis":"可引用的法规/监管口径，未知则写「待人工复核」"}]""";

    private final WhistleReportMapper repo;
    private final PartnerMapper partnerRepo;
    private final RiskClueMapper clueRepo;
    private final RiskRuleMapper ruleRepo;
    private final SettingsService settings;
    private final BaiduSearchClient baidu;
    private final LlmClient llm;
    private final RuleCatalog rules;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public WhistleService(WhistleReportMapper repo, PartnerMapper partnerRepo, RiskClueMapper clueRepo,
                          RiskRuleMapper ruleRepo, SettingsService settings, BaiduSearchClient baidu,
                          LlmClient llm, RuleCatalog rules) {
        this.repo = repo;
        this.partnerRepo = partnerRepo;
        this.clueRepo = clueRepo;
        this.ruleRepo = ruleRepo;
        this.settings = settings;
        this.baidu = baidu;
        this.llm = llm;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<WhistleReport> list(Long orgId) {
        return repo.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional(readOnly = true)
    public WhistleReport get(Long orgId, Long id) {
        return repo.findByIdAndOrgId(id, orgId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "报告不存在"));
    }

    @Transactional
    public WhistleReport startJob(Long orgId, Map<String, Object> body) {
        String kind = normalizeKind(str(body, "kind", "daily"));
        String onDate = str(body, "onDate", "");
        Long partnerId = body.get("partnerId") == null || String.valueOf(body.get("partnerId")).isBlank()
                ? null : Long.valueOf(String.valueOf(body.get("partnerId")));
        boolean batchAll = partnerId == null || "true".equalsIgnoreCase(str(body, "batchAll", "false"));
        List<Partner> targets;
        String partnerName;
        if (batchAll && partnerId == null) {
            targets = partnerRepo.findByOrgIdOrderByUpdatedAtDesc(orgId);
            if (targets.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请先导入合作机构名单后再跑批");
            partnerName = "全量机构（" + targets.size() + "）";
        } else {
            Partner partner = partnerRepo.findByIdAndOrgId(partnerId, orgId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "所选机构不在当前名单中，请重新选择"));
            targets = List.of(partner);
            partnerName = partner.getName();
            batchAll = false;
        }
        LocalDate asOf = onDate.isBlank() ? LocalDate.now() : LocalDate.parse(onDate);
        boolean dated = !onDate.isBlank();
        String period = SearchCore.windowHint(kind, asOf, dated);
        WhistleReport r = new WhistleReport();
        r.setOrgId(orgId);
        r.setKind(kind);
        r.setPeriod(period);
        r.setTitle(("weekly".equals(kind) ? "风险吹哨周报 · " : "风险吹哨日报 · ") + period);
        r.setPartnerName(partnerName);
        r.setStatus("running");
        r.setProgress(5);
        r.setSummary("任务已启动，正在按时间窗检索公开信息…");
        r.setMarkdown("");
        r.setBatchStatsJson("{}");
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        r = repo.save(r);
        Long rid = r.getId();
        boolean persistCursor = batchAll && !dated;
        pool.submit(() -> runJob(orgId, rid, kind, asOf, dated, persistCursor, targets));
        return r;
    }

    private void runJob(Long orgId, Long reportId, String kind, LocalDate asOf, boolean dated,
                        boolean persistCursor, List<Partner> targets) {
        if (!running.add(reportId)) return;
        try {
            update(reportId, 15, "running", "正在按检索窗拼装问句…", null, null, null);
            boolean useBaidu = "true".equalsIgnoreCase(settings.get(orgId, SettingsService.BAIDU_ENABLED, "false"))
                    && "true".equalsIgnoreCase(settings.get(orgId, SettingsService.BAIDU_IN_BATCH, "true"));
            String baiduKey = settings.get(orgId, SettingsService.BAIDU_KEY, "");
            SearchCore.SearchOpts opts = SearchCore.windowFor(kind, asOf, dated);
            int cursor = 0;
            if (persistCursor) {
                try { cursor = Integer.parseInt(settings.get(orgId, CURSOR_KEY, "0")); } catch (Exception ignored) { cursor = 0; }
            }
            List<SearchCore.SearchJob> jobs = useBaidu && !baiduKey.isBlank()
                    ? SearchCore.plan(targets, cursor, opts) : List.of();
            List<String> queries = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            Map<Long, List<SearchCore.WebHit>> hitsByPartner = new HashMap<>();
            int calls = 0;
            for (SearchCore.SearchJob job : jobs) {
                queries.add(job.query());
                calls++;
                BaiduSearchClient.SearchResult result = baidu.searchDetailed(baiduKey, job.query(), job.opts());
                if (result.error() != null) {
                    errors.add("baidu @ [" + job.partners().get(0).getName() + "]: " + result.error());
                    continue;
                }
                for (BaiduSearchClient.RawRef raw : result.hits()) {
                    if (!SearchCore.keepSearchHit(raw.title())) continue;
                    String body = raw.content() == null ? "" : raw.content();
                    String summary = body.codePointCount(0, body.length()) > 180
                            ? body.substring(0, body.offsetByCodePoints(0, Math.min(180, body.codePointCount(0, body.length())))) + "…"
                            : body;
                    String event = raw.date() == null || raw.date().isBlank() ? "日期不详" : raw.date();
                    SearchCore.WebHit hit = new SearchCore.WebHit(raw.title(), raw.url(), summary, body, event, SearchCore.credibility(raw.url()));
                    String text = raw.title() + " " + summary + " " + body;
                    for (Partner p : job.partners()) {
                        if (SearchCore.textMentions(text, SearchCore.watchTerms(p))) {
                            hitsByPartner.computeIfAbsent(p.getId() == null ? -1L : p.getId(), k -> new ArrayList<>());
                            List<SearchCore.WebHit> list = hitsByPartner.get(p.getId() == null ? -1L : p.getId());
                            if (list.stream().noneMatch(h -> h.url().equals(hit.url()))) list.add(hit);
                        }
                    }
                }
            }
            if (persistCursor && !targets.isEmpty()) {
                int covered = jobs.size();
                int next = covered >= targets.size() ? 0 : Math.floorMod(cursor + covered, targets.size());
                settings.set(orgId, CURSOR_KEY, String.valueOf(next));
            }

            update(reportId, 55, "running", "正在按规则定级…", null, null, null);
            List<RuleCatalog.EffectiveRule> effective = rules.effective(ruleRepo.findByOrgIdOrderByUpdatedAtDesc(orgId));
            Map<String, AiGrade> ai = gradeAi(orgId, targets, hitsByPartner, errors);
            List<RiskClue> existing = clueRepo.findByOrgIdOrderByCreatedAtDesc(orgId);
            List<RiskClue> batchClues = new ArrayList<>();
            int rawHits = 0, matched = 0, upserted = 0;
            for (Partner p : targets) {
                List<SearchCore.WebHit> hits = hitsByPartner.getOrDefault(p.getId() == null ? -1L : p.getId(), List.of());
                rawHits += hits.size();
                List<String> terms = SearchCore.watchTerms(p);
                for (SearchCore.WebHit hit : hits) {
                    String text = hit.title() + " " + hit.summary() + " " + hit.body();
                    String key = (p.getId() == null ? p.getName() : p.getId()) + "|" + hit.url() + "|" + clip(hit.title(), 80);
                    AiGrade g = ai.get(key);
                    RiskClue clue = null;
                    if (g != null) {
                        if (!g.relevant) continue;
                        clue = clueFromAi(orgId, p, hit, g);
                    } else if (rules.dualMatch(text, terms)) {
                        RuleCatalog.Grade grade = rules.grade(p.getPartnerType(), text, effective);
                        if (grade != null) clue = clueFromRule(orgId, p, hit, grade);
                        else clue = clueUnconfirmed(orgId, p, hit);
                    } else {
                        clue = clueUnconfirmed(orgId, p, hit);
                    }
                    matched++;
                    RiskClue saved = dedupe(existing, clue);
                    if (saved.getId() == null) {
                        clueRepo.save(saved);
                        existing.add(saved);
                        upserted++;
                    }
                    batchClues.add(saved);
                }
            }
            batchClues.sort((a, b) -> {
                int c = Integer.compare(a.getLevel(), b.getLevel());
                return c != 0 ? c : String.valueOf(b.getEventDate()).compareTo(String.valueOf(a.getEventDate()));
            });
            int l1 = (int) batchClues.stream().filter(c -> c.getLevel() == 1).count();
            int l2 = (int) batchClues.stream().filter(c -> c.getLevel() == 2).count();
            int l3 = (int) batchClues.stream().filter(c -> c.getLevel() == 3).count();
            int l4 = (int) batchClues.stream().filter(c -> c.getLevel() == 4).count();
            String period = SearchCore.windowHint(kind, asOf, dated);
            String title = ("weekly".equals(kind) ? "风险吹哨周报 · " : "风险吹哨日报 · ") + period;
            String scope = targets.size() == 1 ? "指定 1 家" : "全部 " + targets.size() + " 家";
            if (calls > 0) scope = scope + " · 全网检索" + calls + "次、覆盖" + jobs.size() + "家";
            String summary = String.format("本次跑批命中 %d 条（L1=%d / L2=%d / L3=%d / L4=%d）；扫描 %d 家机构，其中新增 %d 条。",
                    batchClues.size(), l1, l2, l3, l4, targets.size(), upserted);
            String markdown = render(kind, title, period, summary, targets.size(), rawHits, matched, upserted, l1, l2, queries, errors, batchClues);
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("partnersScanned", targets.size());
            stats.put("rawHits", rawHits);
            stats.put("matched", matched);
            stats.put("cluesUpserted", upserted);
            stats.put("level1", l1);
            stats.put("level2", l2);
            stats.put("level3", l3);
            stats.put("level4", l4);
            stats.put("scopeNote", scope + " · " + period);
            stats.put("searchQueries", queries);
            stats.put("sourceErrors", errors);
            Map<String, Object> levels = new LinkedHashMap<>();
            levels.put("clueCount", batchClues.size());
            levels.put("level1", l1);
            levels.put("level2", l2);
            levels.put("level3", l3);
            levels.put("level4", l4);
            levels.put("title", title);
            levels.put("period", period);
            update(reportId, 100, "done", summary, markdown, mapper.writeValueAsString(stats), levels);
        } catch (Exception e) {
            log.error("whistle runJob failed reportId={}", reportId, e);
            update(reportId, 100, "failed", "任务失败: " + e.getMessage(), null, null, null);
        } finally {
            running.remove(reportId);
        }
    }

    private Map<String, AiGrade> gradeAi(Long orgId, List<Partner> targets, Map<Long, List<SearchCore.WebHit>> hits, List<String> errors) {
        Map<String, AiGrade> out = new HashMap<>();
        if (!settings.llmReady(orgId)) {
            errors.add("LLM 未就绪：全网搜结果将回退规则定级");
            return out;
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Partner p : targets) {
            for (SearchCore.WebHit hit : hits.getOrDefault(p.getId() == null ? -1L : p.getId(), List.of())) {
                if (payload.size() >= 12) break;
                String key = (p.getId() == null ? p.getName() : p.getId()) + "|" + hit.url() + "|" + clip(hit.title(), 80);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", key);
                row.put("partner", p.getName());
                row.put("partnerType", RuleCatalog.partnerTypeLabel(p.getPartnerType()));
                row.put("title", hit.title());
                row.put("summary", hit.summary());
                row.put("url", hit.url());
                row.put("published", hit.eventTime());
                row.put("credibility", hit.credibility());
                row.put("content", clip(hit.body(), 360));
                payload.add(row);
            }
        }
        if (payload.isEmpty()) return out;
        try {
            String ans = llm.chat(
                    settings.get(orgId, SettingsService.LLM_BASE, ""),
                    settings.get(orgId, SettingsService.LLM_KEY, ""),
                    settings.get(orgId, SettingsService.LLM_MODEL, "gpt-4o-mini"),
                    List.of(
                            Map.of("role", "system", "content", AI_SYSTEM),
                            Map.of("role", "user", "content", "请对以下 " + payload.size() + " 条全网搜结果逐条研判并分级：\n" + mapper.writeValueAsString(payload))
                    )
            );
            int start = ans.indexOf('[');
            int end = ans.lastIndexOf(']');
            if (start < 0 || end <= start) throw new IllegalStateException("AI 未返回 JSON 数组");
            for (JsonNode n : mapper.readTree(ans.substring(start, end + 1))) {
                String key = n.path("key").asText("").trim();
                if (key.isEmpty()) continue;
                int level = Math.min(4, n.path("level").asInt(0));
                boolean relevant = n.path("relevant").asBoolean(false) && level >= 1 && level <= 4;
                out.put(key, new AiGrade(relevant, relevant ? level : 0,
                        clip(n.path("analysis").asText(""), 200),
                        n.path("risk_point").asText("").isBlank() ? "全网舆情风险" : clip(n.path("risk_point").asText(""), 40),
                        n.path("legal_basis").asText("").isBlank() ? "AI研判，待人工复核" : clip(n.path("legal_basis").asText(""), 80)));
            }
        } catch (Exception e) {
            errors.add("AI 研判失败，已回退规则定级: " + e.getMessage());
        }
        return out;
    }

    private RiskClue clueFromAi(Long orgId, Partner p, SearchCore.WebHit hit, AiGrade ai) {
        RiskClue c = base(orgId, p, hit);
        c.setLevel(ai.level);
        c.setSummary("【AI·" + ai.riskPoint + "】" + (ai.analysis.isBlank() ? hit.summary() : ai.analysis));
        c.setStatus(ai.level <= 2 ? "待复核" : "跟踪中");
        c.setRuleId("AI-TAVILY");
        c.setLegalBasis(ai.legalBasis);
        return c;
    }

    private RiskClue clueFromRule(Long orgId, Partner p, SearchCore.WebHit hit, RuleCatalog.Grade grade) {
        RiskClue c = base(orgId, p, hit);
        c.setLevel(grade.level());
        c.setSummary(hit.summary());
        c.setStatus(grade.level() <= 2 ? "待复核" : "跟踪中");
        c.setRuleId(grade.ruleId());
        c.setLegalBasis(grade.legalBasis());
        return c;
    }

    private RiskClue clueUnconfirmed(Long orgId, Partner p, SearchCore.WebHit hit) {
        RiskClue c = base(orgId, p, hit);
        c.setLevel(4);
        c.setSummary("【检索命中】" + hit.summary());
        c.setStatus("跟踪中");
        c.setRuleId("HIT-KEEP");
        c.setLegalBasis("待人工复核");
        return c;
    }

    private RiskClue base(Long orgId, Partner p, SearchCore.WebHit hit) {
        RiskClue c = new RiskClue();
        c.setOrgId(orgId);
        c.setPublicId("W" + LocalDate.now().format(DateTimeFormatter.ofPattern("MMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8));
        c.setPartnerId(p.getId());
        c.setPartnerName(p.getName());
        c.setPartnerType(RuleCatalog.partnerTypeLabel(p.getPartnerType()));
        c.setTitle(hit.title());
        c.setEventDate(hit.eventTime());
        c.setOwner("合作风控");
        c.setProgress(0);
        c.setSourceSystem("baidu");
        c.setSourceUrl(hit.url());
        c.setCredibility(hit.credibility());
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private RiskClue dedupe(List<RiskClue> existing, RiskClue clue) {
        for (RiskClue old : existing) {
            if (old.getSourceUrl() != null && old.getSourceUrl().equals(clue.getSourceUrl())
                    && old.getTitle() != null && old.getTitle().equals(clue.getTitle())
                    && old.getPartnerName() != null && old.getPartnerName().equals(clue.getPartnerName())) {
                return old;
            }
        }
        return clue;
    }

    private String render(String kind, String title, String period, String summary, int scanned, int raw, int matched,
                          int upserted, int l1, int l2, List<String> queries, List<String> errors, List<RiskClue> clues) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");
        md.append("- 统计周期：").append(period).append('\n');
        md.append("- 生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        md.append("- 摘要：").append(summary).append("\n\n");
        md.append("## 本次跑批\n\n| 扫描机构 | 原始命中 | 匹配 | 新增线索 | 新增 L1 | 新增 L2 |\n|---|---|---|---|---|---|\n| ")
                .append(scanned).append(" | ").append(raw).append(" | ").append(matched).append(" | ")
                .append(upserted).append(" | ").append(l1).append(" | ").append(l2).append(" |\n\n");
        md.append("### 全网检索词\n\n");
        if (queries.isEmpty()) md.append("未发出检索（请检查是否已配置并启用百度千帆搜索）。\n\n");
        else for (int i = 0; i < queries.size(); i++) md.append(i + 1).append(". `").append(queries.get(i).replace('`', '\'')).append("`\n");
        md.append('\n');
        if (!errors.isEmpty()) {
            md.append("### 数据源提示\n\n");
            for (String e : errors.subList(0, Math.min(10, errors.size()))) md.append("- ").append(e).append('\n');
            md.append('\n');
        }
        List<RiskClue> l12 = clues.stream().filter(c -> c.getLevel() <= 2).toList();
        List<RiskClue> rest = clues.stream().filter(c -> c.getLevel() >= 3).toList();
        md.append("## 1/2 级即时处置\n\n");
        md.append(l12.isEmpty() ? "本期无 1/2 级待关注线索。\n\n" : table(l12));
        if ("weekly".equals(kind)) {
            List<RiskClue> l3 = clues.stream().filter(c -> c.getLevel() == 3).toList();
            List<RiskClue> l4 = clues.stream().filter(c -> c.getLevel() == 4).toList();
            md.append("## 3 级周报主体\n\n").append(l3.isEmpty() ? "本期无 3 级线索。\n\n" : table(l3));
            md.append("## 4 级附录\n\n").append(l4.isEmpty() ? "本期无 4 级线索。\n\n" : table(l4));
        } else {
            md.append("## 3/4 级跟踪\n\n").append(rest.isEmpty() ? "本期无 3/4 级线索。\n\n" : table(rest));
        }
        md.append("---\n*由智鉴风控官自动生成，核心定级以规则引擎为准，请人工复核 1/2 级。*\n");
        return md.toString();
    }

    private String table(List<RiskClue> clues) {
        StringBuilder s = new StringBuilder("| 线索ID | 机构 | 类型 | 等级 | 摘要 | 来源 | 可信度 | 事件时间 | 状态 | 规则 | 法规依据 |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (RiskClue c : clues) {
            s.append("| ").append(nz(c.getPublicId())).append(" | ").append(nz(c.getPartnerName())).append(" | ")
                    .append(nz(c.getPartnerType())).append(" | L").append(c.getLevel()).append(" | ")
                    .append(nz(c.getTitle()).replace('|', '/')).append(" — ").append(nz(c.getSummary()).replace('|', '/').replace('\n', ' '))
                    .append(" | ").append(nz(c.getSourceSystem())).append(" | ").append(nz(c.getCredibility())).append(" | ")
                    .append(nz(c.getEventDate())).append(" | ").append(nz(c.getStatus())).append(" | ")
                    .append(nz(c.getRuleId())).append(" | ").append(nz(c.getLegalBasis()).replace('|', '/')).append(" |\n");
        }
        return s.append('\n').toString();
    }

    private void update(Long id, int progress, String status, String summary, String md, String statsJson, Map<String, Object> levels) {
        repo.findById(id).ifPresent(r -> {
            r.setProgress(progress);
            r.setStatus(status);
            if (summary != null) r.setSummary(summary);
            if (md != null) r.setMarkdown(md);
            if (statsJson != null) r.setBatchStatsJson(statsJson);
            if (levels != null) {
                if (levels.get("clueCount") != null) r.setClueCount(((Number) levels.get("clueCount")).intValue());
                if (levels.get("level1") != null) r.setLevel1(((Number) levels.get("level1")).intValue());
                if (levels.get("level2") != null) r.setLevel2(((Number) levels.get("level2")).intValue());
                if (levels.get("level3") != null) r.setLevel3(((Number) levels.get("level3")).intValue());
                if (levels.get("level4") != null) r.setLevel4(((Number) levels.get("level4")).intValue());
                if (levels.get("title") != null) r.setTitle(String.valueOf(levels.get("title")));
                if (levels.get("period") != null) r.setPeriod(String.valueOf(levels.get("period")));
            }
            r.setUpdatedAt(LocalDateTime.now());
            repo.save(r);
        });
    }

    private String normalizeKind(String kind) {
        if ("weekly".equalsIgnoreCase(kind) || "monthly".equalsIgnoreCase(kind)) return "weekly";
        return "daily";
    }

    private String str(Map<String, Object> body, String k, String def) {
        Object v = body.get(k);
        return v == null ? def : String.valueOf(v).trim();
    }

    private String nz(String s) { return s == null ? "" : s; }

    private String clip(String s, int n) {
        if (s == null) return "";
        return s.codePointCount(0, s.length()) <= n ? s : s.substring(0, s.offsetByCodePoints(0, n)) + "…";
    }

    private record AiGrade(boolean relevant, int level, String analysis, String riskPoint, String legalBasis) {}
}
