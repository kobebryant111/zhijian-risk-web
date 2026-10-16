package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 风险总览 HTTP 接口。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;
    public DashboardController(DashboardService dashboardService) { this.dashboardService = dashboardService; }

    /**
     * 聚合风险总览指标。
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        log.debug("overview start");

        return dashboardService.overview(AuthSupport.requireOrgId());
    }
}
