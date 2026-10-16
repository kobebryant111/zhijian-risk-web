package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.RiskRule;
import com.zhijian.risk.service.RuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 规则库 HTTP 接口。
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private static final Logger log = LoggerFactory.getLogger(RuleController.class);

    private final RuleService ruleService;
    public RuleController(RuleService ruleService) { this.ruleService = ruleService; }

    /**
     * 列表查询。
     */
    @GetMapping
    public List<RiskRule> list() {
        log.debug("list start");

        Long orgId = AuthSupport.requireOrgId();
        ruleService.ensureDefaults(orgId);
        return ruleService.list(orgId);
    }

    /**
     * 创建资源。
     * @param body body
     */
    @PostMapping
    public RiskRule create(@RequestBody Map<String, Object> body) {
        log.info("create bodyPresent={}", body != null);

        return ruleService.create(AuthSupport.requireOrgId(), body);
    }

    /**
     * 更新资源。
     * @param id id
     * @param body body
     */
    @PutMapping("/{id}")
    public RiskRule update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.info("update id={}, bodyPresent={}", body != null);

        return ruleService.update(AuthSupport.requireOrgId(), id, body);
    }

    /**
     * 删除资源。
     * @param id id
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        log.info("delete id={}", id);

        ruleService.delete(AuthSupport.requireOrgId(), id);
    }
}
