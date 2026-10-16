package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.RiskRule;
import com.zhijian.risk.mapper.RiskRuleMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 风险规则库：默认规则种子与组织内规则维护。
 */
@Service
public class RuleService {
    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private final RiskRuleMapper repo;

    public RuleService(RiskRuleMapper repo) { this.repo = repo; }

    /**
     * 列表查询。
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public List<RiskRule> list(Long orgId) {
        log.debug("list orgId={}", orgId);

        return repo.findByOrgIdOrderByUpdatedAtDesc(orgId);
    }

    /**
     * 创建资源。
     * @param orgId orgId
     * @param body body
     */
    @Transactional
    public RiskRule create(Long orgId, Map<String, Object> body) {
        log.info("create orgId={}", orgId);

        RiskRule r = new RiskRule();
        r.setOrgId(orgId);
        apply(r, body, true);
        r.setUpdatedAt(LocalDateTime.now());
        return repo.save(r);
    }

    /**
     * 更新资源。
     * @param orgId orgId
     * @param id id
     * @param body body
     */
    @Transactional
    public RiskRule update(Long orgId, Long id, Map<String, Object> body) {
        log.info("update orgId={}, id={}", orgId, id);

        RiskRule r = repo.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "规则不存在"));
        apply(r, body, false);
        r.setUpdatedAt(LocalDateTime.now());
        return repo.save(r);
    }

    /**
     * 删除资源。
     * @param orgId orgId
     * @param id id
     */
    @Transactional
    public void delete(Long orgId, Long id) {
        log.info("delete orgId={}, id={}", orgId, id);

        RiskRule r = repo.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "规则不存在"));
        repo.deleteById(r.getId());
    }

    /**
     * 若组织尚无规则则写入默认规则集。
     * @param orgId orgId
     */
    @Transactional
    public void ensureDefaults(Long orgId) {
        log.info("ensureDefaults orgId={}", orgId);

        if (repo.countByOrgId(orgId) > 0) return;
        seed(orgId, "R-ABNORMAL", "common", "经营异常名录命中", 2, "命中经营异常名录", "工商", "企业信息公示");
        seed(orgId, "R-ILLEGAL", "common", "严重违法失信", 1, "列入严重违法失信名单", "工商", "企业信息公示");
        seed(orgId, "R-ZHIXING", "loan", "被执行人风险", 2, "存在被执行人记录", "司法", "执行信息公开");
        seed(orgId, "R-SHIXIN", "loan", "失信被执行人", 1, "存在失信被执行人记录", "司法", "失信被执行人名单");
        seed(orgId, "R-PENALTY", "common", "行政处罚", 3, "近一年存在行政处罚", "监管", "行政处罚决定");
        seed(orgId, "R-SENTIMENT", "common", "舆情负面关键词", 3, "新闻/舆情出现负面关键词", "舆情", "公开检索");
        seed(orgId, "R-RELATED", "guarantee", "关联企业风险传导", 3, "关联主体存在高风险线索", "关联", "图谱传导");
        seed(orgId, "R-COURT", "ops", "开庭公告诉讼", 4, "存在开庭公告或诉讼信息", "司法", "开庭公告");
    }

    private void seed(Long orgId, String code, String type, String point, int level, String trigger, String source, String legal) {
        RiskRule r = new RiskRule();
        r.setOrgId(orgId);
        r.setRuleCode(code);
        r.setPartnerType(type);
        r.setRiskPoint(point);
        r.setLevel(level);
        r.setTriggerText(trigger);
        r.setDataSource(source);
        r.setLegalBasis(legal);
        r.setKeywordsJson("[]");
        r.setEnabled(true);
        r.setUpdatedAt(LocalDateTime.now());
        repo.save(r);
    }

    private void apply(RiskRule r, Map<String, Object> body, boolean creating) {
        if (body.get("ruleCode") != null) r.setRuleCode(String.valueOf(body.get("ruleCode")).trim());
        if (body.get("partnerType") != null) r.setPartnerType(String.valueOf(body.get("partnerType")).trim());
        if (body.get("riskPoint") != null) r.setRiskPoint(String.valueOf(body.get("riskPoint")).trim());
        if (body.get("level") != null) r.setLevel(Integer.parseInt(String.valueOf(body.get("level"))));
        if (body.get("triggerText") != null) r.setTriggerText(String.valueOf(body.get("triggerText")));
        if (body.get("dataSource") != null) r.setDataSource(String.valueOf(body.get("dataSource")));
        if (body.get("legalBasis") != null) r.setLegalBasis(String.valueOf(body.get("legalBasis")));
        if (body.get("keywordsJson") != null) r.setKeywordsJson(String.valueOf(body.get("keywordsJson")));
        if (body.get("enabled") != null) r.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        if (creating) {
            if (r.getRuleCode() == null || r.getRuleCode().isBlank()) r.setRuleCode("R-" + System.currentTimeMillis());
            if (r.getPartnerType() == null || r.getPartnerType().isBlank()) r.setPartnerType("common");
            if (r.getRiskPoint() == null || r.getRiskPoint().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "风险点不能为空");
            }
            if (r.getLevel() < 1 || r.getLevel() > 4) r.setLevel(3);
        }
    }
}
