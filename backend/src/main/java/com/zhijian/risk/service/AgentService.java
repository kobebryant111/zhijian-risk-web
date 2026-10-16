package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhijian.risk.client.BaiduSearchClient;
import com.zhijian.risk.client.LlmClient;
import com.zhijian.risk.domain.AgentConversation;
import com.zhijian.risk.mapper.AgentConversationMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 问鉴控对话：会话管理、检索增强与大模型回复。
 */
@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentConversationMapper repo;
    private final SettingsService settings;
    private final LlmClient llm;
    private final BaiduSearchClient baidu;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public AgentService(AgentConversationMapper repo, SettingsService settings, LlmClient llm, BaiduSearchClient baidu) {
        this.repo = repo; this.settings = settings; this.llm = llm; this.baidu = baidu;
    }

    /**
     * 列表查询。
     * @param orgId orgId
     * @param userId userId
     */
    @Transactional(readOnly = true)
    public List<AgentConversation> list(Long orgId, Long userId) {
        log.debug("list orgId={}, userId={}", orgId, userId);

        return repo.findByOrgIdAndUserIdOrderByUpdatedAtDesc(orgId, userId);
    }

    /**
     * 按 ID 查询详情。
     * @param orgId orgId
     * @param userId userId
     * @param id id
     */
    @Transactional(readOnly = true)
    public AgentConversation get(Long orgId, Long userId, Long id) {
        log.debug("get orgId={}, userId={}, id={}", orgId, userId, id);

        return repo.findByIdAndOrgIdAndUserId(id, orgId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "会话不存在"));
    }

    /**
     * 创建资源。
     * @param orgId orgId
     * @param userId userId
     * @param title title
     */
    @Transactional
    public AgentConversation create(Long orgId, Long userId, String title) {
        log.info("create orgId={}, userId={}, title={}", orgId, userId, title);

        AgentConversation c = new AgentConversation();
        c.setOrgId(orgId);
        c.setUserId(userId);
        c.setTitle(title == null || title.isBlank() ? "新对话" : title.trim());
        c.setStatus("idle");
        c.setMessagesJson("[]");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return repo.save(c);
    }

    /**
     * 发送问鉴控消息并异步回复。
     * @param orgId orgId
     * @param userId userId
     * @param id id
     * @param content content
     */
    @Transactional
    public AgentConversation send(Long orgId, Long userId, Long id, String content) {
        log.info("send conversation/user action");

        if (content == null || content.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "消息不能为空");
        AgentConversation c = get(orgId, userId, id);
        if ("running".equals(c.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "上一条消息仍在处理中");
        List<Map<String, Object>> msgs = readMsgs(c);
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", content);
        userMsg.put("ts", LocalDateTime.now().toString());
        msgs.add(userMsg);
        if ("新对话".equals(c.getTitle())) {
            c.setTitle(content.length() > 24 ? content.substring(0, 24) + "…" : content);
        }
        c.setMessagesJson(writeMsgs(msgs));
        c.setStatus("running");
        c.setUpdatedAt(LocalDateTime.now());
        c = repo.save(c);
        Long cid = c.getId();
        pool.submit(() -> reply(orgId, cid, content));
        return c;
    }

        /** 异步执行：reply。 */
private void reply(Long orgId, Long id, String userContent) {
        log.info("agent reply start orgId={}, conversationId={}", orgId, id);

        try {
            List<BaiduSearchClient.Hit> hits = new ArrayList<>();
            String baiduKey = settings.get(orgId, SettingsService.BAIDU_KEY, "");
            if ("true".equalsIgnoreCase(settings.get(orgId, SettingsService.BAIDU_ENABLED, "false")) && !baiduKey.isBlank()) {
                hits = baidu.search(baiduKey, userContent);
            }
            String answer;
            if (settings.llmReady(orgId)) {
                List<Map<String, String>> chat = new ArrayList<>();
                chat.add(Map.of("role", "system", "content",
                        "你是「问鉴控」企业风控助手。结合组织上下文回答风控问题，必要时引用检索摘要。回答使用中文，结构清晰。"));
                if (!hits.isEmpty()) {
                    StringBuilder sb = new StringBuilder("以下是相关公开检索摘要：\n");
                    for (BaiduSearchClient.Hit h : hits) {
                        sb.append("- ").append(h.title()).append(": ").append(h.snippet()).append("\n");
                    }
                    chat.add(Map.of("role", "system", "content", sb.toString()));
                }
                AgentConversation cur = repo.findById(id).orElse(null);
                if (cur != null) {
                    for (Map<String, Object> m : readMsgs(cur)) {
                        String role = String.valueOf(m.getOrDefault("role", "user"));
                        if (!"user".equals(role) && !"assistant".equals(role)) continue;
                        chat.add(Map.of("role", role, "content", String.valueOf(m.getOrDefault("content", ""))));
                    }
                } else {
                    chat.add(Map.of("role", "user", "content", userContent));
                }
                answer = llm.chat(
                        settings.get(orgId, SettingsService.LLM_BASE, ""),
                        settings.get(orgId, SettingsService.LLM_KEY, ""),
                        settings.get(orgId, SettingsService.LLM_MODEL, "gpt-4o-mini"),
                        chat
                );
            } else {
                answer = "（演示回复）已收到：" + userContent
                        + "\n\n请在「设置」中配置大模型 API Key 后，问鉴控即可给出完整研判。"
                        + (hits.isEmpty() ? "" : "\n\n已检索到 " + hits.size() + " 条公开信息可供参考。");
            }
            final String ans = answer;
            final List<BaiduSearchClient.Hit> finalHits = hits;
            repo.findById(id).ifPresent(c -> {
                List<Map<String, Object>> msgs = readMsgs(c);
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("role", "assistant");
                a.put("content", ans);
                a.put("ts", LocalDateTime.now().toString());
                if (!finalHits.isEmpty()) {
                    List<Map<String, String>> refs = new ArrayList<>();
                    for (BaiduSearchClient.Hit h : finalHits) {
                        refs.add(Map.of("title", h.title(), "url", h.url()));
                    }
                    a.put("references", refs);
                }
                msgs.add(a);
                c.setMessagesJson(writeMsgs(msgs));
                c.setStatus("idle");
                c.setUpdatedAt(LocalDateTime.now());
                repo.save(c);
            });
        } catch (Exception e) {
            log.error("agent reply failed id={}", id, e);
            repo.findById(id).ifPresent(c -> {
                List<Map<String, Object>> msgs = readMsgs(c);
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("role", "assistant");
                a.put("content", "处理失败：" + e.getMessage());
                a.put("ts", LocalDateTime.now().toString());
                msgs.add(a);
                c.setMessagesJson(writeMsgs(msgs));
                c.setStatus("idle");
                c.setUpdatedAt(LocalDateTime.now());
                repo.save(c);
            });
        }
    }

    /**
     * 删除资源。
     * @param orgId orgId
     * @param userId userId
     * @param id id
     */
    @Transactional
    public void delete(Long orgId, Long userId, Long id) {
        log.info("delete orgId={}, userId={}, id={}", orgId, userId, id);

        AgentConversation c = get(orgId, userId, id);
        repo.deleteById(c.getId());
    }

    private List<Map<String, Object>> readMsgs(AgentConversation c) {
        try {
            if (c.getMessagesJson() == null || c.getMessagesJson().isBlank()) return new ArrayList<>();
            return mapper.readValue(c.getMessagesJson(), new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeMsgs(List<Map<String, Object>> msgs) {
        try { return mapper.writeValueAsString(msgs); }
        catch (Exception e) { return "[]"; }
    }
}
