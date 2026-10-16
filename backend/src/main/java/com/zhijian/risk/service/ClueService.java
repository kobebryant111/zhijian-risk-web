package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.RiskClue;
import com.zhijian.risk.mapper.RiskClueMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 风险线索台账：查询、登记、状态跟进与统计。
 */
@Service
public class ClueService {
    private static final Logger log = LoggerFactory.getLogger(ClueService.class);

    private final RiskClueMapper repo;

    public ClueService(RiskClueMapper repo) { this.repo = repo; }

    /**
     * 列表查询。
     * @param orgId orgId
     * @param level level
     * @param status status
     * @param q q
     */
    @Transactional(readOnly = true)
    public List<RiskClue> list(Long orgId, Integer level, String status, String q) {
        log.debug("list orgId={}, level={}, status={}, q={}", orgId, level, status, q);

        return repo.findByOrgIdOrderByCreatedAtDesc(orgId).stream().filter(c -> {
            if (level != null && c.getLevel() != level) return false;
            if (status != null && !status.isBlank() && !status.equals(c.getStatus())) return false;
            if (q != null && !q.isBlank()) {
                String qq = q.toLowerCase();
                String hay = (nullToEmpty(c.getTitle()) + " " + nullToEmpty(c.getPartnerName()) + " " + nullToEmpty(c.getSummary())).toLowerCase();
                if (!hay.contains(qq)) return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    /**
     * 按 ID 查询详情。
     * @param orgId orgId
     * @param id id
     */
    @Transactional(readOnly = true)
    public RiskClue get(Long orgId, Long id) {
        log.debug("get orgId={}, id={}", orgId, id);

        return repo.findByIdAndOrgId(id, orgId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "线索不存在"));
    }

    /**
     * 创建资源。
     * @param orgId orgId
     * @param body body
     */
    @Transactional
    public RiskClue create(Long orgId, Map<String, Object> body) {
        log.info("create orgId={}", orgId);

        RiskClue c = new RiskClue();
        c.setOrgId(orgId);
        c.setPublicId("CLUE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        c.setPartnerId(body.get("partnerId") == null ? null : Long.valueOf(String.valueOf(body.get("partnerId"))));
        c.setPartnerName(str(body, "partnerName", "未命名主体"));
        c.setPartnerType(str(body, "partnerType", ""));
        c.setLevel(body.get("level") == null ? 3 : Integer.parseInt(String.valueOf(body.get("level"))));
        c.setTitle(str(body, "title", "未命名线索"));
        c.setSummary(str(body, "summary", ""));
        c.setEventDate(str(body, "eventDate", LocalDate.now().toString()));
        c.setOwner(str(body, "owner", "合作风控"));
        c.setProgress(body.get("progress") == null ? 0 : Integer.parseInt(String.valueOf(body.get("progress"))));
        c.setStatus(str(body, "status", "待复核"));
        c.setSourceSystem(str(body, "sourceSystem", "manual"));
        c.setSourceUrl(str(body, "sourceUrl", ""));
        c.setCredibility(str(body, "credibility", "C"));
        c.setRuleId(str(body, "ruleId", ""));
        c.setLegalBasis(str(body, "legalBasis", ""));
        c.setCreatedAt(LocalDateTime.now());
        return repo.save(c);
    }

    /**
     * 更新资源。
     * @param orgId orgId
     * @param id id
     * @param body body
     */
    @Transactional
    public RiskClue update(Long orgId, Long id, Map<String, Object> body) {
        log.info("update orgId={}, id={}", orgId, id);

        RiskClue c = get(orgId, id);
        if (body.get("status") != null) c.setStatus(String.valueOf(body.get("status")));
        if (body.get("progress") != null) c.setProgress(Integer.parseInt(String.valueOf(body.get("progress"))));
        if (body.get("owner") != null) c.setOwner(String.valueOf(body.get("owner")));
        if (body.get("summary") != null) c.setSummary(String.valueOf(body.get("summary")));
        if (body.get("level") != null) c.setLevel(Integer.parseInt(String.valueOf(body.get("level"))));
        return repo.save(c);
    }

    /**
     * 线索统计。
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public Map<String, Object> stats(Long orgId) {
        log.debug("stats orgId={}", orgId);

        List<RiskClue> all = repo.findByOrgIdOrderByCreatedAtDesc(orgId);
        Map<String, Long> byLevel = all.stream().collect(Collectors.groupingBy(c -> "L" + c.getLevel(), Collectors.counting()));
        Map<String, Long> byStatus = all.stream().collect(Collectors.groupingBy(c -> c.getStatus() == null ? "未知" : c.getStatus(), Collectors.counting()));
        Map<String, Long> byType = all.stream().collect(Collectors.groupingBy(c -> {
            String t = c.getPartnerType();
            return t == null || t.isBlank() ? "其他" : t;
        }, Collectors.counting()));
        long pending = all.stream().filter(c -> "待复核".equals(c.getStatus())).count();
        long high = all.stream().filter(c -> c.getLevel() <= 2).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("pending", pending);
        m.put("highOrCritical", high);
        m.put("byLevel", byLevel);
        m.put("byStatus", byStatus);
        m.put("byType", byType);
        return m;
    }

    private String str(Map<String, Object> body, String k, String def) {
        Object v = body.get(k);
        return v == null ? def : String.valueOf(v).trim();
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }
}
