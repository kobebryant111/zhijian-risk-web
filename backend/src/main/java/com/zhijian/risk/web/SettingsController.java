package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 组织设置 HTTP 接口。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final SettingsService settingsService;
    public SettingsController(SettingsService settingsService) { this.settingsService = settingsService; }

    /**
     * 按 ID 查询详情。
     */
    @GetMapping
    public Map<String, Object> get() {
        log.debug("get start");

        return settingsService.publicView(AuthSupport.requireOrgId());
    }

    /**
     * 执行 save。
     * @param body body
     */
    @PutMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        log.info("save bodyPresent={}", body != null);

        Long orgId = AuthSupport.requireOrgId();
        settingsService.saveAll(orgId, body);
        return settingsService.publicView(orgId);
    }
}
