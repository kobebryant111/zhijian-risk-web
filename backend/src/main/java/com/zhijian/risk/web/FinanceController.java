package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.FinanceReport;
import com.zhijian.risk.service.FinanceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 经营哨 HTTP 接口。
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {
    private static final Logger log = LoggerFactory.getLogger(FinanceController.class);

    private final FinanceService financeService;
    public FinanceController(FinanceService financeService) { this.financeService = financeService; }

    /**
     * 列表查询。
     */
    @GetMapping("/reports")
    public List<FinanceReport> list() {
        log.debug("list start");

        return financeService.list(AuthSupport.requireOrgId());
    }

    /**
     * 按 ID 查询详情。
     * @param id id
     */
    @GetMapping("/reports/{id}")
    public FinanceReport get(@PathVariable Long id) {
        log.debug("get id={}", id);

        return financeService.get(AuthSupport.requireOrgId(), id);
    }

    /**
     * 启动异步任务。
     * @param body body
     */
    @PostMapping("/reports")
    public FinanceReport start(@RequestBody Map<String, Object> body) {
        log.info("start bodyPresent={}", body != null);

        return financeService.start(AuthSupport.requireOrgId(), body);
    }
}
