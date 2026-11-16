package com.zhijian.risk.web;

import com.zhijian.risk.domain.RiskRule;
import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.service.RuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        AuthSupport.requireOrgId();
        return ExcelDownload.attachment(ruleService.template(), "规则库导入模板.xlsx");
    }

    @PostMapping("/import")
    public ApiResult<Dtos.ImportOutcome> importExcel(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(ruleService.importExcel(AuthSupport.requireOrgId(), ExcelDownload.bytes(file)));
    }
}
