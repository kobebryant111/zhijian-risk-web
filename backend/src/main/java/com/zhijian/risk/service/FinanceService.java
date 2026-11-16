package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhijian.risk.client.LlmClient;
import com.zhijian.risk.domain.FinanceReport;
import com.zhijian.risk.domain.Partner;
import com.zhijian.risk.engine.FinanceCore;
import com.zhijian.risk.mapper.FinanceReportMapper;
import com.zhijian.risk.mapper.PartnerMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SettingsService settings;
    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public FinanceService(FinanceReportMapper repo, PartnerMapper partnerRepo,
                          SettingsService settings, LlmClient llm) {
        this.repo = repo; this.partnerRepo = partnerRepo;
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
        if (partnerId != null) {
            Partner p = partnerRepo.findByIdAndOrgId(partnerId, orgId).orElse(null);
            if (p != null) {
                if (name.isBlank()) name = p.getName();
                if (p.getPartnerType() != null && !p.getPartnerType().isBlank()) type = p.getPartnerType();
            }
        }
        String document = str(body, "documentText", "");
        if (document.isBlank() && !(body.get("metrics") instanceof Map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请上传财报附件");
        }
        if (name.isBlank()) name = "待识别";
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
        Long boundPartner = partnerId;
        pool.submit(() -> run(orgId, rid, boundPartner, subj, ptype, period, doc, metricsIn));
        return r;
    }

    private void run(Long orgId, Long id, Long partnerId, String subject, String type, String period, String document, Object metricsIn) {
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
                int points = document.codePointCount(0, document.length());
                String doc = points > 24000 ? document.substring(0, document.offsetByCodePoints(0, 24000)) : document;
                String ans = llm.chat(
                        settings.get(orgId, SettingsService.LLM_BASE, ""),
                        settings.get(orgId, SettingsService.LLM_KEY, ""),
                        settings.get(orgId, SettingsService.LLM_MODEL, "gpt-4o-mini"),
                        List.of(
                                Map.of("role", "system", "content", FinanceCore.EXTRACT_SYSTEM),
                                Map.of("role", "user", "content", doc)
                        ),
                        150,
                        2
                );
                metrics = FinanceCore.parseExtract(ans);
                sourceKind = "text";
            }
            String name = "待识别".equals(subject) ? "" : subject;
            String ptype = type == null || type.isBlank() ? "loan" : type;
            String per = period == null ? "" : period.trim();
            if (name.isBlank()) name = strOf(metrics.get("partnerName"), "");
            if (per.isBlank()) per = strOf(metrics.get("period"), "");
            String extractedType = strOf(metrics.get("partnerType"), "").toLowerCase();
            if (allowedType(extractedType) && (ptype.isBlank()
                    || (partnerId == null && "loan".equals(ptype) && "guarantee".equals(extractedType)))) {
                ptype = extractedType;
            }
            if (name.isBlank()) throw new IllegalStateException("未能识别机构名称，请手动填写后再分析");
            if (per.isBlank()) per = "报告期";
            FinanceCore.Scored scored = FinanceCore.score(metrics, ptype, name, per);
            replaceSamePeriod(orgId, id, name, per);
            List<FinanceReport> history = repo.findByOrgIdOrderByCreatedAtDesc(orgId);
            List<String> peers = peerLines(history, ptype, per, name, scored);
            List<String> series = seriesLines(history, name, per, scored);
            List<String> related = relatedFlags(orgId, partnerId, name);
            String md = FinanceCore.markdown(name, ptype, per, scored, related, peers, series, sourceKind);
            List<Map<String, Object>> dims = new ArrayList<>();
            for (FinanceCore.Dimension d : scored.dimensions()) {
                dims.add(Map.of("name", d.dimension(), "grade", d.grade(), "score", d.score(), "comment", d.comment()));
            }
            String savedName = name;
            String savedType = ptype;
            String savedPeriod = per;
            repo.findById(id).ifPresent(r -> {
                r.setPartnerName(savedName);
                r.setPartnerType(savedType);
                r.setPeriod(savedPeriod);
                repo.save(r);
            });
            patch(id, 100, "done", scored.summary(), scored.overall(), md,
                    mapper.writeValueAsString(scored.metrics()), mapper.writeValueAsString(dims));
        } catch (Exception e) {
            log.error("finance run failed id={}", id, e);
            patch(id, 100, "failed", RunFailure.summary("分析", e), "F", null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> peers(Long orgId, String type, String period) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<FinanceReport> matched = new ArrayList<>();
        for (FinanceReport report : repo.findByOrgIdOrderByCreatedAtDesc(orgId)) {
            if (!"done".equals(report.getStatus())) continue;
            if (type != null && !type.equals(report.getPartnerType())) continue;
            if (period != null && !period.equals(report.getPeriod())) continue;
            if (!seen.add(report.getPartnerName())) continue;
            matched.add(report);
        }
        matched.sort((a, b) -> Integer.compare(scoreOf(b), scoreOf(a)));
        int rank = 1;
        for (FinanceReport report : matched) {
            if (rank > 15) break;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("partnerName", report.getPartnerName());
            row.put("grade", report.getGrade());
            row.put("score", scoreOf(report));
            row.put("reportId", report.getId());
            rows.add(row);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> series(Long orgId, String partnerName) {
        Map<String, FinanceReport> latest = new LinkedHashMap<>();
        for (FinanceReport report : repo.findByOrgIdOrderByCreatedAtDesc(orgId)) {
            if (!"done".equals(report.getStatus()) || !partnerName.equals(report.getPartnerName())) continue;
            latest.putIfAbsent(report.getPeriod() == null ? "" : report.getPeriod(), report);
        }
        List<FinanceReport> rows = new ArrayList<>(latest.values());
        rows.sort(Comparator.comparing(r -> r.getPeriod() == null ? "" : r.getPeriod()));
        if (rows.size() > 8) rows = rows.subList(rows.size() - 8, rows.size());
        List<Map<String, Object>> out = new ArrayList<>();
        for (FinanceReport report : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", report.getPeriod());
            row.put("grade", report.getGrade());
            row.put("score", scoreOf(report));
            row.put("reportId", report.getId());
            try {
                Map<?, ?> metrics = mapper.readValue(report.getMetricsJson(), Map.class);
                row.put("assetLiabilityRatio", FinanceCore.num(metrics, "assetLiabilityRatio"));
                row.put("netMargin", FinanceCore.num(metrics, "netMargin"));
                row.put("fcf", FinanceCore.num(metrics, "fcf"));
            } catch (Exception ignored) {
                row.put("assetLiabilityRatio", null);
                row.put("netMargin", null);
                row.put("fcf", null);
            }
            out.add(row);
        }
        return out;
    }

    private void replaceSamePeriod(Long orgId, Long currentId, String name, String period) {
        for (FinanceReport report : repo.findByOrgIdOrderByCreatedAtDesc(orgId)) {
            if (currentId.equals(report.getId())) continue;
            if (name.equals(report.getPartnerName()) && period.equals(report.getPeriod())) {
                repo.deleteById(report.getId());
            }
        }
    }

    private List<String> peerLines(List<FinanceReport> history, String type, String period, String self, FinanceCore.Scored current) {
        List<FinanceReport> peers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FinanceReport r : history) {
            if (!"done".equals(r.getStatus())) continue;
            if (!type.equals(r.getPartnerType()) || !period.equals(r.getPeriod())) continue;
            if (!seen.add(r.getPartnerName())) continue;
            peers.add(r);
        }
        if (seen.add(self)) {
            FinanceReport selfRow = new FinanceReport();
            selfRow.setPartnerName(self);
            selfRow.setPartnerType(type);
            selfRow.setPeriod(period);
            selfRow.setGrade(current.overall());
            selfRow.setStatus("done");
            try { selfRow.setMetricsJson(mapper.writeValueAsString(current.metrics())); } catch (Exception ignored) {}
            peers.add(selfRow);
        }
        peers.sort((a, b) -> Integer.compare(scoreOf(b), scoreOf(a)));
        List<String> lines = new ArrayList<>();
        int rank = 1;
        for (FinanceReport r : peers) {
            if (rank > 12) break;
            lines.add("| " + rank + " | " + r.getPartnerName() + " | " + r.getGrade() + " | " + scoreOf(r) + " |");
            rank++;
        }
        return lines;
    }

    private List<String> seriesLines(List<FinanceReport> history, String name, String period, FinanceCore.Scored current) {
        Map<String, FinanceReport> latest = new LinkedHashMap<>();
        List<FinanceReport> rows = new ArrayList<>();
        for (FinanceReport r : history) {
            if (name.equals(r.getPartnerName()) && "done".equals(r.getStatus())) rows.add(r);
        }
        rows.sort(Comparator.comparing(FinanceReport::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        for (FinanceReport r : rows) latest.putIfAbsent(r.getPeriod() == null ? "" : r.getPeriod(), r);
        if (!latest.containsKey(period)) {
            FinanceReport selfRow = new FinanceReport();
            selfRow.setPartnerName(name);
            selfRow.setPeriod(period);
            selfRow.setGrade(current.overall());
            selfRow.setStatus("done");
            selfRow.setCreatedAt(LocalDateTime.now());
            try { selfRow.setMetricsJson(mapper.writeValueAsString(current.metrics())); } catch (Exception ignored) {}
            latest.put(period, selfRow);
        }
        List<FinanceReport> ordered = new ArrayList<>(latest.values());
        ordered.sort(Comparator.comparing(r -> r.getPeriod() == null ? "" : r.getPeriod()));
        if (ordered.size() > 8) ordered = ordered.subList(ordered.size() - 8, ordered.size());
        List<String> lines = new ArrayList<>();
        for (FinanceReport r : ordered) {
            Double al = null, nm = null, fcf = null;
            try {
                Map<?, ?> m = mapper.readValue(r.getMetricsJson(), Map.class);
                al = FinanceCore.num(m, "assetLiabilityRatio");
                nm = FinanceCore.num(m, "netMargin");
                fcf = FinanceCore.num(m, "fcf");
            } catch (Exception ignored) {}
            lines.add("| " + r.getPeriod() + " | " + scoreOf(r) + " | " + r.getGrade() + " | "
                    + fmt1(al) + " | " + fmt1(nm) + " | " + fmt2(fcf) + " |");
        }
        return lines;
    }

    private List<String> relatedFlags(Long orgId, Long partnerId, String name) {
        Partner partner = partnerId == null ? null : partnerRepo.findByIdAndOrgId(partnerId, orgId).orElse(null);
        if (partner == null) {
            for (Partner candidate : partnerRepo.findByOrgIdOrderByUpdatedAtDesc(orgId)) {
                if (name.equals(candidate.getName())) { partner = candidate; break; }
            }
        }
        if (partner == null) {
            return List.of("未绑定机构名单：无法自动穿透股东/关联方，请先在机构名单维护关联方。");
        }
        return List.of("关联方名单为空：建议补充股东/关联企业以便穿透核查。");
    }

    private static boolean allowedType(String type) {
        return switch (type) {
            case "loan", "guarantee", "traffic", "payment", "data", "collection", "interbank", "ops" -> true;
            default -> false;
        };
    }

    private int scoreOf(FinanceReport r) {
        try {
            Map<?, ?> m = mapper.readValue(r.getMetricsJson(), Map.class);
            Double s = FinanceCore.num(m, "riskScore");
            return s == null ? 0 : s.intValue();
        } catch (Exception e) { return 0; }
    }

    private String fmt1(Double v) { return v == null ? "—" : String.format("%.1f%%", v); }
    private String fmt2(Double v) { return v == null ? "—" : String.format("%.2f", v); }

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
