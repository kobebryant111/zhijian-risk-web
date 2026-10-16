package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.WhistleReport;
import com.zhijian.risk.service.WhistleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 吹哨人 HTTP 接口。
 */
@RestController
@RequestMapping("/api/whistle")
public class WhistleController {
    private static final Logger log = LoggerFactory.getLogger(WhistleController.class);

    private final WhistleService whistleService;
    public WhistleController(WhistleService whistleService) { this.whistleService = whistleService; }

    /**
     * 列表查询。
     */
    @GetMapping("/reports")
    public List<WhistleReport> list() {
        log.debug("list start");

        return whistleService.list(AuthSupport.requireOrgId());
    }

    /**
     * 按 ID 查询详情。
     * @param id id
     */
    @GetMapping("/reports/{id}")
    public WhistleReport get(@PathVariable Long id) {
        log.debug("get id={}", id);

        return whistleService.get(AuthSupport.requireOrgId(), id);
    }

    @PostMapping("/jobs")
    public WhistleReport start(@RequestBody(required = false) Map<String, Object> body) {
        return whistleService.startJob(AuthSupport.requireOrgId(), body == null ? Map.of() : body);
    }
}
