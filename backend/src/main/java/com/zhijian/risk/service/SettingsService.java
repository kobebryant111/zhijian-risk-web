package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.OrgSetting;
import com.zhijian.risk.mapper.OrgSettingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 组织级配置：大模型、百度搜索、工商数据源。
 */
@Service
public class SettingsService {
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    public static final String LLM_BASE = "llm.baseUrl";
    public static final String LLM_KEY = "llm.apiKey";
    public static final String LLM_MODEL = "llm.model";
    public static final String LLM_ENABLED = "llm.enabled";
    public static final String BAIDU_KEY = "baidu.apiKey";
    public static final String BAIDU_ENABLED = "baidu.enabled";
    public static final String BAIDU_IN_BATCH = "baidu.useInBatch";
    public static final String QCC_KEY = "qcc.apiKey";
    public static final String QCC_ENABLED = "qcc.enabled";
    public static final String TYC_KEY = "tyc.apiKey";
    public static final String TYC_ENABLED = "tyc.enabled";

    private final OrgSettingMapper repo;

    public SettingsService(OrgSettingMapper repo) { this.repo = repo; }

    /**
     * 按 ID 查询详情。
     * @param orgId orgId
     * @param key key
     * @param def def
     */
    @Transactional(readOnly = true)
    public String get(Long orgId, String key, String def) {
        log.debug("get orgId={}, key={}, def={}", orgId, key, def);

        return repo.findByOrgIdAndSettingKey(orgId, key).map(OrgSetting::getSettingValue).filter(s -> !s.isBlank()).orElse(def);
    }

    /**
     * 执行 set。
     * @param orgId orgId
     * @param key key
     * @param value value
     */
    @Transactional
    public void set(Long orgId, String key, String value) {
        log.info("set orgId={}, key={}, value={}", orgId, key, value);

        OrgSetting s = repo.findByOrgIdAndSettingKey(orgId, key).orElseGet(OrgSetting::new);
        s.setOrgId(orgId);
        s.setSettingKey(key);
        s.setSettingValue(value == null ? "" : value);
        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
    }

    /**
     * 返回脱敏后的组织配置视图。
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public Map<String, Object> publicView(Long orgId) {
        log.debug("publicView orgId={}", orgId);

        String llmKey = get(orgId, LLM_KEY, "");
        String baiduKey = get(orgId, BAIDU_KEY, "");
        String qccKey = get(orgId, QCC_KEY, "");
        String tycKey = get(orgId, TYC_KEY, "");
        boolean llmEnabled = "true".equalsIgnoreCase(get(orgId, LLM_ENABLED, "false"));
        boolean baiduEnabled = "true".equalsIgnoreCase(get(orgId, BAIDU_ENABLED, "false"));
        boolean qccEnabled = "true".equalsIgnoreCase(get(orgId, QCC_ENABLED, "false"));
        boolean tycEnabled = "true".equalsIgnoreCase(get(orgId, TYC_ENABLED, "false"));
        Map<String, Object> m = new HashMap<>();
        m.put("llm", Map.of(
                "baseUrl", get(orgId, LLM_BASE, "https://api.openai.com/v1"),
                "model", get(orgId, LLM_MODEL, "gpt-4o-mini"),
                "enabled", llmEnabled,
                "hasApiKey", !llmKey.isBlank(),
                "ready", llmEnabled && !llmKey.isBlank()
        ));
        m.put("baidu", Map.of(
                "enabled", baiduEnabled,
                "hasApiKey", !baiduKey.isBlank(),
                "ready", baiduEnabled && !baiduKey.isBlank(),
                "useInBatch", "true".equalsIgnoreCase(get(orgId, BAIDU_IN_BATCH, "true"))
        ));
        m.put("mcp", Map.of(
                "qccEnabled", qccEnabled,
                "qccHasApiKey", !qccKey.isBlank(),
                "qccReady", qccEnabled && !qccKey.isBlank(),
                "tycEnabled", tycEnabled,
                "tycHasApiKey", !tycKey.isBlank(),
                "tycReady", tycEnabled && !tycKey.isBlank()
        ));
        return m;
    }

    /**
     * 批量保存组织配置（密钥留空则不覆盖）。
     * @param orgId orgId
     * @param body body
     */
    @Transactional
    public void saveAll(Long orgId, Map<String, Object> body) {
        log.info("save settings orgId={}", orgId);

        Map<?,?> llm = (Map<?,?>) body.getOrDefault("llm", Map.of());
        Map<?,?> baidu = (Map<?,?>) body.getOrDefault("baidu", Map.of());
        Map<?,?> mcp = (Map<?,?>) body.getOrDefault("mcp", Map.of());
        if (llm.get("baseUrl") != null) set(orgId, LLM_BASE, String.valueOf(llm.get("baseUrl")));
        if (llm.get("model") != null) set(orgId, LLM_MODEL, String.valueOf(llm.get("model")));
        if (llm.get("enabled") != null) set(orgId, LLM_ENABLED, String.valueOf(llm.get("enabled")));
        if (llm.get("apiKey") != null && !String.valueOf(llm.get("apiKey")).isBlank()) set(orgId, LLM_KEY, String.valueOf(llm.get("apiKey")));
        if (baidu.get("enabled") != null) set(orgId, BAIDU_ENABLED, String.valueOf(baidu.get("enabled")));
        if (baidu.get("useInBatch") != null) set(orgId, BAIDU_IN_BATCH, String.valueOf(baidu.get("useInBatch")));
        if (baidu.get("apiKey") != null && !String.valueOf(baidu.get("apiKey")).isBlank()) set(orgId, BAIDU_KEY, String.valueOf(baidu.get("apiKey")));
        if (mcp.get("qccEnabled") != null) set(orgId, QCC_ENABLED, String.valueOf(mcp.get("qccEnabled")));
        if (mcp.get("tycEnabled") != null) set(orgId, TYC_ENABLED, String.valueOf(mcp.get("tycEnabled")));
        if (mcp.get("qccApiKey") != null && !String.valueOf(mcp.get("qccApiKey")).isBlank()) set(orgId, QCC_KEY, String.valueOf(mcp.get("qccApiKey")));
        if (mcp.get("tycApiKey") != null && !String.valueOf(mcp.get("tycApiKey")).isBlank()) set(orgId, TYC_KEY, String.valueOf(mcp.get("tycApiKey")));
    }

    /**
     * 判断大模型是否已启用且配置了 Key。
     * @param orgId orgId
     */
    public boolean llmReady(Long orgId) {
        log.debug("llmReady orgId={}", orgId);

        return "true".equalsIgnoreCase(get(orgId, LLM_ENABLED, "false")) && !get(orgId, LLM_KEY, "").isBlank();
    }
}
