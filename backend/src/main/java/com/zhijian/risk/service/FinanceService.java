package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhijian.risk.client.LlmClient;
import com.zhijian.risk.domain.FinanceReport;
import com.zhijian.risk.domain.Partner;
import com.zhijian.risk.domain.RiskClue;
import com.zhijian.risk.engine.FinanceCore;
import com.zhijian.risk.engine.RuleCatalog;
import com.zhijian.risk.mapper.FinanceReportMapper;
import com.zhijian.risk.mapper.PartnerMapper;
import com.zhijian.risk.mapper.RiskClueMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 经营哨异步分析：评级、指标与经营报告。
 */
@Service
public class FinanceService {
    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    private final FinanceReportMapper repo;
    private final PartnerMapper partnerRepo;
    private final RiskClueMapper clueRepo;
    private final SettingsService settings;
    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public FinanceService(FinanceReportMapper repo, PartnerMapper partnerRepo, RiskClueMapper clueRepo,
                          SettingsService settings, LlmClient llm) {
        this.repo = repo; this.partnerRepo = partnerRepo; this.clueRepo = clueRepo;
        this.settings = settings; this.llm = llm;
    }

    /**
     * 列表查询。
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public List<FinanceReport> list(Long orgId) {
        log.debug("list orgId={}", orgId);

        return repo.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    /**
     * 按 ID 查询详情。
     * @param orgId orgId
     * @param id id
     */
    @Transactional(readOnly = true)
    public FinanceReport get(Long orgId, Long id) {
        log.debug("get orgId={}, id={}", orgId, id);

        return repo.findByIdAndOrgId(id, orgId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "经营报告不存在"));
    }

    /**
     * 启动异步任务。
     * @param orgId orgId
     * @param body body
     */
    @Transactional
    public FinanceReport start(Long orgId, Map<String, Object> body) {
        log.info("start orgId={}", orgId);

        String name = str(body, "partnerName", "");
        Long partnerId = body.get("partnerId") == null ? null : Long.valueOf(String.valueOf(body.get("partnerId")));
        String type = str(body, "partnerType", "loan");
        if (name.isBlank() && partnerId != null) {
            Partner p = partnerRepo.findByIdAndOrgId(partnerId, orgId).orElse(null);
            if (p != null) { name = p.getName(); type = p.getPartnerType(); }
        }
        if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "请指定经营分析主体");
        String document = str(body, "documentText", "");
        if (document.isBlank() && !(body.get("metrics") instanceof Map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请粘贴财报或审计报告正文");
        }
        FinanceReport r = new FinanceReport();
        r.setOrgId(orgId);
        r.setPartnerName(name);
        r.setPartnerType(type);
        r.setPeriod(str(body, "period", ""));
        r.setGrade("B");
        r.setStatus("running");
        r.setProgress(10);
        r.setSummary("经营透视任务已启动…");
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        r = repo.save(r);
        Long rid = r.getId();
        String subj = name;
        String ptype = type;
        String period = r.getPeriod();
        String doc = document;
        Object metricsIn = body.get("metrics");
        pool.submit(() -> run(orgId, rid, subj, ptype, period, doc, metricsIn));
        return r;
    }

    private void run(Long orgId, Long id, String subject, String type, String period, String document, Object metricsIn) {
        log.info("finance run start orgId={}, id={}, subject={}", orgId, id, subject);
        try {
            patch(id, 30, "running", "正在从财报正文抽取指标…", null, null, null, null);
            Map<String, Object> metrics;
            String sourceKind;
            if (metricsIn instanceof Map<?, ?> given) {
                metrics = new LinkedHashMap<>();
                given.forEach((k, v) -> metrics.put(String.valueOf(k), v));
                sourceKind = "metrics";
            } else {
                if (!settings.llmReady(orgId)) {
                    throw new IllegalStateException("LLM 未就绪，无法解析财报附件；请先在设置页配置大模型");
                }
                String doc = document.length() > 24000 ? document.substring(0, document.offsetByCodePoints(0, Math.min(24000, document.codePointCount(0, document.length())))) : document;
                String ans = llm.chat(
                        settings.get(orgId, SettingsService.LLM_BASE, ""),
                        settings.get(orgId, SettingsService.LLM_KEY, ""),
                        settings.get(orgId, SettingsService.LLM_MODEL, "gpt-4o-mini"),
                        List.of(
                                Map.of("role", "system", "content", FinanceCore.EXTRACT_SYSTEM),
                                Map.of("role", "user", "content", doc)
                        )
                );
                metrics = FinanceCore.parseExtract(ans);
                sourceKind = "text";
            }
            String name = strOf(metrics.get("partnerName"), subject);
            String ptype = strOf(metrics.get("partnerType"), type);
            String per = period == null || period.isBlank() ? strOf(metrics.get("period"), LocalDate.now().getYear() + "全年") : period;
            FinanceCore.Scored scored = FinanceCore.score(metrics, ptype, name, per);
            List<FinanceReport> history = repo.findByOrgIdOrderByCreatedAtDesc(orgId);
            List<String> peers = peerLines(history, ptype, per, name);
            List<String> series = seriesLines(history, name, scored);
            List<String> related = relatedFlags(orgId, name);
            String md = FinanceCore.markdown(name, ptype, per, scored, related, peers, series, sourceKind);
            List<Map<String, Object>> dims = new ArrayList<>();
            for (FinanceCore.Dimension d : scored.dimensions()) {
                dims.add(Map.of("name", d.dimension(), "grade", d.grade(), "score", d.score(), "comment", d.comment()));
            }
            repo.findById(id).ifPresent(r -> {
                r.setPartnerName(name);
                r.setPartnerType(ptype);
                r.setPeriod(per);
                repo.save(r);
            });
            patch(id, 100, "done", scored.summary(), scored.overall(), md,
                    mapper.writeValueAsString(scored.metrics()), mapper.writeValueAsString(dims));
        } catch (Exception e) {
            log.error("finance run failed id={}", id, e);
            patch(id, 100, "failed", "分析失败: " + e.getMessage(), "F", null, null, null);
        }
    }

    private List<String> peerLines(List<FinanceReport> history, String type, String period, String self) {
        List<String> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<FinanceReport> peers = new ArrayList<>();
        for (FinanceReport r : history) {
            if (!type.equals(r.getPartnerType()) || !period.equals(r.getPeriod())) continue;
            if (!"done".equals(r.getStatus())) continue;
            if (!seen.add(r.getPartnerName())) continue;
            peers.add(r);
        }
        peers.sort((a, b) -> Integer.compare(scoreOf(b), scoreOf(a)));
        int rank = 1;
        for (FinanceReport r : peers) {
            if (rank > 12) break;
            lines.add("| " + rank + " | " + r.getPartnerName() + " | " + r.getGrade() + " | " + scoreOf(r) + " |");
            rank++;
        }
        if (lines.stream().noneMatch(l -> l.contains("| " + self + " |"))) {
            // 当前报告尚未落库，排名在保存后的下一次报告里补齐。
        }
        return lines;
    }

    private List<String> seriesLines(List<FinanceReport> history, String name, FinanceCore.Scored current) {
        List<FinanceReport> rows = new ArrayList<>();
        for (FinanceReport r : history) {
            if (name.equals(r.getPartnerName()) && "done".equals(r.getStatus())) rows.add(r);
        }
        rows.sort(Comparator.comparing(r -> r.getPeriod() == null ? "" : r.getPeriod()));
        List<String> lines = new ArrayList<>();
        for (FinanceReport r : rows) {
            Double al = null, nm = null, fcf = null;
            try {
                Map<?, ?> m = mapper.readValue(r.getMetricsJson(), Map.class);
                al = FinanceCore.num(m, "assetLiabilityRatio");
                nm = FinanceCore.num(m, "netMargin");
                fcf = FinanceCore.num(m, "fcf");
            } catch (Exception ignored) {}
            lines.add("| " + r.getPeriod() + " | " + scoreOf(r) + " | " + r.getGrade() + " | "
                    + fmt(al, "%") + " | " + fmt(nm, "%") + " | " + fmt(fcf, "") + " |");
        }
        lines.add("| " + strOf(current.metrics().get("period"), "") + " | " + current.riskScore() + " | " + current.overall()
                + " | " + fmt(FinanceCore.num(current.metrics(), "assetLiabilityRatio"), "%")
                + " | " + fmt(FinanceCore.num(current.metrics(), "netMargin"), "%")
                + " | " + fmt(FinanceCore.num(current.metrics(), "fcf"), "") + " |");
        return lines;
    }

    private List<String> relatedFlags(Long orgId, String name) {
        List<String> flags = new ArrayList<>();
        flags.add("机构名单未登记关联方字段：建议在机构资料中补充股东/关联企业后再做穿透。");
        int n = 0;
        for (RiskClue c : clueRepo.findByOrgIdOrderByCreatedAtDesc(orgId)) {
            if (c.getLevel() > 2) continue;
            if (c.getPartnerName() != null && c.getPartnerName().contains(name)) {
                flags.add("本机构存在 1/2 级线索：" + c.getTitle());
                if (++n >= 3) break;
            }
        }
        return flags;
    }

    private int scoreOf(FinanceReport r) {
        try {
            Map<?, ?> m = mapper.readValue(r.getMetricsJson(), Map.class);
            Double s = FinanceCore.num(m, "riskScore");
            return s == null ? 0 : s.intValue();
        } catch (Exception e) { return 0; }
    }

    private String fmt(Double v, String suffix) { return v == null ? "—" : String.format("%.2f%s", v, suffix); }

    private String strOf(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? def : s;
    }

    private void patch(Long id, int progress, String status, String summary, String grade, String md, String metrics, String dims) {
        repo.findById(id).ifPresent(r -> {
            r.setProgress(progress);
            r.setStatus(status);
            if (summary != null) r.setSummary(summary);
            if (grade != null) r.setGrade(grade);
            if (md != null) r.setMarkdown(md);
            if (metrics != null) r.setMetricsJson(metrics);
            if (dims != null) r.setDimensionsJson(dims);
            r.setUpdatedAt(LocalDateTime.now());
            repo.save(r);
        });
    }

    private String str(Map<String, Object> body, String k, String def) {
        Object v = body.get(k);
        return v == null ? def : String.valueOf(v).trim();
    }
}
