package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.Partner;
import com.zhijian.risk.domain.RiskClue;
import com.zhijian.risk.domain.WhistleReport;
import com.zhijian.risk.mapper.PartnerMapper;
import com.zhijian.risk.mapper.RiskClueMapper;
import com.zhijian.risk.mapper.WhistleReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 风险总览聚合：健康分、趋势、近期线索与报告。
 */
@Service
public class DashboardService {
    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final RiskClueMapper clueRepo;
    private final PartnerMapper partnerRepo;
    private final WhistleReportMapper whistleRepo;
    private final ClueService clueService;
    private final RuleService ruleService;

    public DashboardService(RiskClueMapper clueRepo, PartnerMapper partnerRepo,
                            WhistleReportMapper whistleRepo, ClueService clueService, RuleService ruleService) {
        this.clueRepo = clueRepo;
        this.partnerRepo = partnerRepo;
        this.whistleRepo = whistleRepo;
        this.clueService = clueService;
        this.ruleService = ruleService;
    }

    /**
     * 聚合风险总览指标。
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long orgId) {
        log.debug("overview orgId={}", orgId);

        ruleService.ensureDefaults(orgId);
        List<RiskClue> clues = clueRepo.findByOrgIdOrderByCreatedAtDesc(orgId);
        List<Partner> partners = partnerRepo.findByOrgIdOrderByUpdatedAtDesc(orgId);
        List<WhistleReport> reports = whistleRepo.findByOrgIdOrderByCreatedAtDesc(orgId);

        int score = 100;
        long l1 = clues.stream().filter(c -> c.getLevel() == 1).count();
        long l2 = clues.stream().filter(c -> c.getLevel() == 2).count();
        long l3 = clues.stream().filter(c -> c.getLevel() == 3).count();
        score -= (int) (l1 * 15 + l2 * 8 + l3 * 3);
        if (score < 0) score = 0;
        String level = score >= 80 ? "healthy" : score >= 60 ? "watch" : score >= 40 ? "risk" : "critical";

        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long cnt = clues.stream().filter(c -> c.getCreatedAt() != null && c.getCreatedAt().toLocalDate().equals(d)).count();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", d.toString());
            p.put("count", cnt);
            trend.add(p);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("healthScore", score);
        m.put("healthLevel", level);
        m.put("partnerCount", partners.size());
        m.put("clueStats", clueService.stats(orgId));
        m.put("recentClues", clues.stream().limit(8).collect(Collectors.toList()));
        m.put("recentReports", reports.stream().limit(5).collect(Collectors.toList()));
        m.put("trend", trend);
        m.put("typeCards", List.of(
                Map.of("type", "loan", "label", "助贷", "count", partners.stream().filter(p -> "loan".equals(p.getPartnerType())).count()),
                Map.of("type", "guarantee", "label", "融担", "count", partners.stream().filter(p -> "guarantee".equals(p.getPartnerType())).count()),
                Map.of("type", "ops", "label", "运营", "count", partners.stream().filter(p -> "ops".equals(p.getPartnerType())).count()),
                Map.of("type", "other", "label", "其他", "count", partners.stream().filter(p -> !List.of("loan","guarantee","ops").contains(p.getPartnerType())).count())
        ));
        return m;
    }
}
