package com.zhijian.risk.web;

import com.zhijian.risk.domain.FinanceReport;
import com.zhijian.risk.service.FinanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 经营守护：财报研判的查询与发起。 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {
    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/reports")
    public ApiResult<List<FinanceReport>> list() {
        return ApiResult.ok(financeService.list(AuthSupport.requireOrgId()));
    }

    @GetMapping("/reports/{id}")
    public ApiResult<FinanceReport> get(@PathVariable Long id) {
        return ApiResult.ok(financeService.get(AuthSupport.requireOrgId(), id));
    }

    @PostMapping("/reports")
    public ApiResult<FinanceReport> start(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(financeService.start(AuthSupport.requireOrgId(), body));
    }
}
