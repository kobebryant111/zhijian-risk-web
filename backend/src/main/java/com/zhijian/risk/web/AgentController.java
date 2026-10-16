package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.AgentConversation;
import com.zhijian.risk.security.UserPrincipal;
import com.zhijian.risk.service.AgentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 问鉴控 HTTP 接口。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    public AgentController(AgentService agentService) { this.agentService = agentService; }

    /**
     * 列表查询。
     */
    @GetMapping("/conversations")
    public List<AgentConversation> list() {
        log.debug("list start");

        UserPrincipal u = AuthSupport.requireUser();
        return agentService.list(AuthSupport.requireOrgId(), u.getUserId());
    }

    /**
     * 按 ID 查询详情。
     * @param id id
     */
    @GetMapping("/conversations/{id}")
    public AgentConversation get(@PathVariable Long id) {
        log.debug("get id={}", id);

        UserPrincipal u = AuthSupport.requireUser();
        return agentService.get(AuthSupport.requireOrgId(), u.getUserId(), id);
    }

    @PostMapping("/conversations")
    public AgentConversation create(@RequestBody(required = false) Map<String, Object> body) {
        UserPrincipal u = AuthSupport.requireUser();
        String title = body == null || body.get("title") == null ? "新对话" : String.valueOf(body.get("title"));
        return agentService.create(AuthSupport.requireOrgId(), u.getUserId(), title);
    }

    /**
     * 发送问鉴控消息并异步回复。
     * @param id id
     * @param body body
     */
    @PostMapping("/conversations/{id}/messages")
    public AgentConversation send(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.info("send conversation/user action");

        UserPrincipal u = AuthSupport.requireUser();
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
        return agentService.send(AuthSupport.requireOrgId(), u.getUserId(), id, content);
    }

    /**
     * 删除资源。
     * @param id id
     */
    @DeleteMapping("/conversations/{id}")
    public void delete(@PathVariable Long id) {
        log.info("delete id={}", id);

        UserPrincipal u = AuthSupport.requireUser();
        agentService.delete(AuthSupport.requireOrgId(), u.getUserId(), id);
    }
}
