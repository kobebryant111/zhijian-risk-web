package com.zhijian.risk.web;

import com.zhijian.risk.domain.RiskRule;
import com.zhijian.risk.service.RuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 规则库。组织尚无规则时，列表接口会补一套默认规则。 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public ApiResult<List<RiskRule>> list() {
        Long orgId = AuthSupport.requireOrgId();
        ruleService.ensureDefaults(orgId);
        return ApiResult.ok(ruleService.list(orgId));
    }

    @PostMapping
    public ApiResult<RiskRule> create(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(ruleService.create(AuthSupport.requireOrgId(), body));
    }

    @PutMapping("/{id}")
    public ApiResult<RiskRule> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResult.ok(ruleService.update(AuthSupport.requireOrgId(), id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        ruleService.delete(AuthSupport.requireOrgId(), id);
        return ApiResult.ok(null);
    }
}
