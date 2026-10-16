package com.zhijian.risk.web;

import com.zhijian.risk.domain.WhistleReport;
import com.zhijian.risk.service.WhistleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 风险吹哨：本批报告的查询与发起。 */
@RestController
@RequestMapping("/api/whistle")
public class WhistleController {
    private final WhistleService whistleService;

    public WhistleController(WhistleService whistleService) {
        this.whistleService = whistleService;
    }

    @GetMapping("/reports")
    public ApiResult<List<WhistleReport>> list() {
        return ApiResult.ok(whistleService.list(AuthSupport.requireOrgId()));
    }

    @GetMapping("/reports/{id}")
    public ApiResult<WhistleReport> get(@PathVariable Long id) {
        return ApiResult.ok(whistleService.get(AuthSupport.requireOrgId(), id));
    }

    @PostMapping("/jobs")
    public ApiResult<WhistleReport> start(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResult.ok(whistleService.startJob(AuthSupport.requireOrgId(), body == null ? Map.of() : body));
    }
}
