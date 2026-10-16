package com.zhijian.risk.web;

import com.zhijian.risk.domain.AdmissionReview;
import com.zhijian.risk.service.AdmissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 准入瞭望：研判记录查询与发起。 */
@RestController
@RequestMapping("/api/admission")
public class AdmissionController {
    private final AdmissionService admissionService;

    public AdmissionController(AdmissionService admissionService) {
        this.admissionService = admissionService;
    }

    @GetMapping("/reviews")
    public ApiResult<List<AdmissionReview>> list() {
        return ApiResult.ok(admissionService.list(AuthSupport.requireOrgId()));
    }

    @GetMapping("/reviews/{id}")
    public ApiResult<AdmissionReview> get(@PathVariable Long id) {
        return ApiResult.ok(admissionService.get(AuthSupport.requireOrgId(), id));
    }

    @PostMapping("/reviews")
    public ApiResult<AdmissionReview> start(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(admissionService.start(AuthSupport.requireOrgId(), body));
    }
}
