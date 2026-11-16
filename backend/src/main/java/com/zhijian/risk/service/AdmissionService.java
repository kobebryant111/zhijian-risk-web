package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhijian.risk.client.BaiduSearchClient;
import com.zhijian.risk.client.LlmClient;
import com.zhijian.risk.client.McpProbe;
import com.zhijian.risk.domain.AdmissionReview;
import com.zhijian.risk.domain.Partner;
import com.zhijian.risk.domain.RiskClue;
import com.zhijian.risk.engine.AdmissionCore;
import com.zhijian.risk.engine.SearchCore;
import com.zhijian.risk.mapper.AdmissionReviewMapper;
import com.zhijian.risk.mapper.PartnerMapper;
import com.zhijian.risk.mapper.RiskClueMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 准入官异步评审。证据来自本地线索、上传材料、百度搜索和已启用的企查查、天眼查。
 */
@Service
public class AdmissionService {
    private static final Logger log = LoggerFactory.getLogger(AdmissionService.class);

    private final AdmissionReviewMapper repo;
    private final RiskClueMapper clueRepo;
    private final PartnerMapper partners;
    private final SettingsService settings;
    private final BaiduSearchClient baidu;
    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public AdmissionService(AdmissionReviewMapper repo, RiskClueMapper clueRepo, PartnerMapper partners,
                            SettingsService settings, BaiduSearchClient baidu, LlmClient llm) {
        this.repo = repo; this.clueRepo = clueRepo; this.partners = partners;
        this.settings = settings; this.baidu = baidu; this.llm = llm;
    }

    /**
     * 列表查询。
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public List<AdmissionReview> list(Long orgId) {
        log.debug("list orgId={}", orgId);

        return repo.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    /**
     * 按 ID 查询详情。
     * @param orgId orgId
     * @param id id
     */
    @Transactional(readOnly = true)
    public AdmissionReview get(Long orgId, Long id) {
        log.debug("get orgId={}, id={}", orgId, id);

        return repo.findByIdAndOrgId(id, orgId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "准入评审不存在"));
    }

    /**
     * 启动异步任务。
     * @param orgId orgId
     * @param body body
     */
    @Transactional
    public AdmissionReview start(Long orgId, Map<String, Object> body) {
        log.info("start orgId={}", orgId);

        String name = str(body, "partnerName", "");
        if (name.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "主体名称不能为空");
        AdmissionReview r = new AdmissionReview();
        r.setOrgId(orgId);
        r.setPublicId("ADM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        r.setPartnerName(name);
        r.setPartnerType(str(body, "partnerType", "loan"));
        r.setScenario(str(body, "scenario", "准入"));
        r.setConclusion("评审中");
        r.setSummary("准入核验任务已启动…");
        r.setStatus("running");
        r.setProgress(8);
        r.setEvidenceIncomplete(true);
        r.setEvidenceSources(str(body, "evidenceSources", ""));
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        r = repo.save(r);
        Long rid = r.getId();
        String type = r.getPartnerType();
        String scenario = r.getScenario();
        String materials = str(body, "materialsText", "");
        pool.submit(() -> run(orgId, rid, name, type, scenario, materials));
        return r;
    }

    private void run(Long orgId, Long id, String name, String type, String scenario, String materials) {
        log.info("admission run start orgId={}, id={}, name={}", orgId, id, name);
        try {
            patch(id, 20, "running", "核验主体公开信息与上传材料…", null, null, null, null, null, true);
            List<AdmissionCore.Evidence> docs = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            for (RiskClue c : clueRepo.findByOrgIdOrderByCreatedAtDesc(orgId)) {
                boolean hit = (c.getPartnerName() != null && (c.getPartnerName().contains(name) || name.contains(c.getPartnerName())));
                if (!hit) continue;
                docs.add(new AdmissionCore.Evidence("本地线索",
                        (c.getTitle() == null ? "" : c.getTitle()) + " " + (c.getSummary() == null ? "" : c.getSummary()) + " " + (c.getLegalBasis() == null ? "" : c.getLegalBasis()),
                        c.getSourceUrl() == null ? "" : c.getSourceUrl(), c.getLevel()));
            }
            if (docs.stream().anyMatch(d -> "本地线索".equals(d.source()))) sources.add("本地线索");
            if (materials != null && !materials.isBlank()) {
                boolean finance = materials.contains("财报") || materials.contains("审计报告") || materials.contains("资产负债表")
                        || materials.contains("利润表") || materials.contains("营业收入") || materials.contains("净利润");
                String label = finance ? "财报附件" : "上传材料";
                String text = materials.codePointCount(0, materials.length()) > 40000
                        ? materials.substring(0, materials.offsetByCodePoints(0, 40000)) : materials;
                docs.add(new AdmissionCore.Evidence(label, text, "", 2));
                sources.add(label);
            }
            String baiduKey = settings.get(orgId, SettingsService.BAIDU_KEY, "");
            boolean baiduOn = "true".equalsIgnoreCase(settings.get(orgId, SettingsService.BAIDU_ENABLED, "false")) && !baiduKey.isBlank();
            if (baiduOn) {
                List<String> aliases = AdmissionCore.aliases(name);
                SearchCore.SearchOpts opts = SearchCore.lastSemiyear(8);
                int calls = 0;
                String baiduErr = null;
                for (String q : SearchCore.admissionQueries(name)) {
                    if (calls++ >= SearchCore.MAX_CALLS_PER_OPERATION) break;
                    BaiduSearchClient.SearchResult result = baidu.searchDetailed(baiduKey, q, opts);
                    if (result.error() != null) {
                        if (baiduErr == null) baiduErr = result.error();
                        continue;
                    }
                    int kept = 0;
                    for (BaiduSearchClient.RawRef h : result.hits()) {
                        String text = h.title() + " " + h.content();
                        if (!AdmissionCore.webHitUsable(h.title(), h.url(), text, aliases)) continue;
                        kept++;
                        int level = switch (SearchCore.credibility(h.url())) { case "A" -> 1; case "B" -> 2; default -> 3; };
                        docs.add(new AdmissionCore.Evidence("全网搜", text, h.url(), level));
                    }
                    if (kept > 0 && !sources.contains("全网搜")) sources.add("全网搜");
                }
                if (baiduErr != null) {
                    docs.add(new AdmissionCore.Evidence("全网搜", "全网搜提示：" + clip(baiduErr), "", 3));
                }
            }
            patch(id, 55, "running", "正在查询企查查、天眼查…", null, null, null, null, null, true);
            String query = registryQuery(orgId, name);
            pullEnterprise(id, "企查查", settings.get(orgId, SettingsService.QCC_ENABLED, "false"),
                    settings.get(orgId, SettingsService.QCC_KEY, ""), McpProbe.QCC_COMPANY, query, name, docs, sources, true);
            pullEnterprise(id, "天眼查", settings.get(orgId, SettingsService.TYC_ENABLED, "false"),
                    settings.get(orgId, SettingsService.TYC_KEY, ""), McpProbe.TYC, query, name, docs, sources, false);
            AdmissionCore.Review review = AdmissionCore.review(name, type, scenario, docs, sources);
            String aiNotes = "";
            String summary = review.summary();
            if (settings.llmReady(orgId)) {
                try {
                    aiNotes = llm.chat(
                            settings.get(orgId, SettingsService.LLM_BASE, ""),
                            settings.get(orgId, SettingsService.LLM_KEY, ""),
                            settings.get(orgId, SettingsService.LLM_MODEL, "gpt-4o-mini"),
                            List.of(
                                    Map.of("role", "system", "content", AdmissionCore.NOTES_SYSTEM),
                                    Map.of("role", "user", "content", AdmissionCore.notesUser(name, type, scenario, review, docs))
                            )
                    );
                    if (!aiNotes.isBlank()) {
                        summary = summary + "\nAI 辅助：" + (aiNotes.length() > 120 ? aiNotes.substring(0, 120) + "…" : aiNotes);
                    }
                } catch (Exception e) {
                    aiNotes = "AI 辅助未完成：" + e.getMessage();
                }
            }
            String markdown = AdmissionCore.opinion(name, type, usccOf(orgId, name), scenario, review, summary, aiNotes, docs);
            List<Map<String, Object>> items = new ArrayList<>();
            for (AdmissionCore.Item i : review.items()) {
                items.add(Map.of("id", i.id(), "color", i.color(), "item", i.title(), "legalBasis", i.legalBasis(),
                        "triggered", i.triggered(), "result", i.triggered() ? "hit" : "pass", "detail", i.evidence()));
            }
            patch(id, 100, "done", summary, review.conclusion(), markdown, mapper.writeValueAsString(items),
                    mapper.writeValueAsString(review.remediation()), aiNotes, review.evidenceIncomplete());
            final String src = String.join(",", sources);
            repo.findById(id).ifPresent(r -> { r.setEvidenceSources(src); repo.save(r); });
            log.info("admission run done id={} sources={}", id, src);
        } catch (Exception e) {
            log.error("admission run failed id={}", id, e);
            patch(id, 100, "failed", RunFailure.summary("评审", e), "待核验", null, null, null, null, true);
        }
    }

    private void patch(Long id, int progress, String status, String summary, String conclusion,
                       String markdown, String items, String remediation, String aiNotes, Boolean incomplete) {
        repo.findById(id).ifPresent(r -> {
            r.setProgress(progress);
            r.setStatus(status);
            if (summary != null) r.setSummary(summary);
            if (conclusion != null) r.setConclusion(conclusion);
            if (markdown != null) r.setMarkdown(markdown);
            if (items != null) r.setItemsJson(items);
            if (remediation != null) r.setRemediationJson(remediation);
            if (aiNotes != null) r.setAiNotes(aiNotes);
            if (incomplete != null) r.setEvidenceIncomplete(incomplete);
            r.setUpdatedAt(LocalDateTime.now());
            repo.save(r);
        });
    }

    /** 企查查必须带 Bearer。天眼查先用裸 Key，失败再换 Bearer。失败只记一条说明，不让整单评审失败。 */
    private void pullEnterprise(Long id, String label, String enabled, String key, String url, String query, String name,
                                List<AdmissionCore.Evidence> docs, List<String> sources, boolean bearerOnly) {
        if (!"true".equalsIgnoreCase(enabled) || key == null || key.isBlank()) return;
        String raw = key.trim();
        try {
            String text = McpProbe.lookup(url, bearerOnly ? bearer(raw) : raw, query, name);
            rememberEnterprise(label, text, docs, sources);
            return;
        } catch (RuntimeException first) {
            if (bearerOnly || raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
                log.warn("admission {} skipped id={} reason={}", label, id, first.getMessage());
                rememberEnterpriseMiss(label, first.getMessage(), docs, sources);
                return;
            }
            try {
                String text = McpProbe.lookup(url, bearer(raw), query, name);
                rememberEnterprise(label, text, docs, sources);
            } catch (RuntimeException second) {
                log.warn("admission {} skipped id={} reason={}", label, id, second.getMessage());
                rememberEnterpriseMiss(label, second.getMessage(), docs, sources);
            }
        }
    }

    private static void rememberEnterprise(String label, String text, List<AdmissionCore.Evidence> docs, List<String> sources) {
        String source = "企查查".equals(label) ? "企查查·company" : label;
        docs.add(new AdmissionCore.Evidence(source, text, "", 1));
        if (!sources.contains(label)) sources.add(label);
    }

    private static void rememberEnterpriseMiss(String label, String reason, List<AdmissionCore.Evidence> docs, List<String> sources) {
        docs.add(new AdmissionCore.Evidence(label, label + "补证未完成（已跳过，不阻塞报告）：" + clip(reason), "", 3));
        String missed = label + "未完成";
        if (!sources.contains(missed)) sources.add(missed);
    }

    private String registryQuery(Long orgId, String name) {
        String uscc = usccOf(orgId, name);
        return uscc.isBlank() ? name : uscc;
    }

    private String usccOf(Long orgId, String name) {
        for (Partner partner : partners.findByOrgIdOrderByUpdatedAtDesc(orgId)) {
            if (partner.getName() != null && partner.getName().trim().equals(name)) {
                String uscc = partner.getUscc() == null ? "" : partner.getUscc().trim();
                if (!uscc.isBlank()) return uscc;
            }
        }
        return "";
    }

    private static String bearer(String key) {
        return key.regionMatches(true, 0, "Bearer ", 0, 7) ? key : "Bearer " + key;
    }

    private static String clip(String text) {
        if (text == null) return "";
        String trimmed = text.replace('\n', ' ').trim();
        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180);
    }

    private String str(Map<String, Object> body, String k, String def) {
        Object v = body.get(k);
        return v == null ? def : String.valueOf(v).trim();
    }
}
