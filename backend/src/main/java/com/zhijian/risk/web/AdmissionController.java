package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.AdmissionReview;
import com.zhijian.risk.service.AdmissionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 准入官 HTTP 接口。
 */
@RestController
@RequestMapping("/api/admission")
public class AdmissionController {
    private static final Logger log = LoggerFactory.getLogger(AdmissionController.class);

    private final AdmissionService admissionService;
    public AdmissionController(AdmissionService admissionService) { this.admissionService = admissionService; }

    /**
     * 列表查询。
     */
    @GetMapping("/reviews")
    public List<AdmissionReview> list() {
        log.debug("list start");

        return admissionService.list(AuthSupport.requireOrgId());
    }

    /**
     * 按 ID 查询详情。
     * @param id id
     */
    @GetMapping("/reviews/{id}")
    public AdmissionReview get(@PathVariable Long id) {
        log.debug("get id={}", id);

        return admissionService.get(AuthSupport.requireOrgId(), id);
    }

    /**
     * 启动异步任务。
     * @param body body
     */
    @PostMapping("/reviews")
    public AdmissionReview start(@RequestBody Map<String, Object> body) {
        log.info("start bodyPresent={}", body != null);

        return admissionService.start(AuthSupport.requireOrgId(), body);
    }
}
