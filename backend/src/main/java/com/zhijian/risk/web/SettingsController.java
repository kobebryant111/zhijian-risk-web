package com.zhijian.risk.web;

import com.zhijian.risk.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 组织设置。返回体不含密钥原文。 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResult<Map<String, Object>> get() {
        return ApiResult.ok(settingsService.publicView(AuthSupport.requireOrgId()));
    }

    @PutMapping
    public ApiResult<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        Long orgId = AuthSupport.requireOrgId();
        settingsService.saveAll(orgId, body);
        return ApiResult.ok(settingsService.publicView(orgId));
    }
}
