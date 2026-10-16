package com.zhijian.risk.web;

import com.zhijian.risk.domain.AgentConversation;
import com.zhijian.risk.security.UserPrincipal;
import com.zhijian.risk.service.AgentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 问鉴控：会话的查询、发送与删除。 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/conversations")
    public ApiResult<List<AgentConversation>> list() {
        UserPrincipal u = AuthSupport.requireUser();
        return ApiResult.ok(agentService.list(AuthSupport.requireOrgId(), u.getUserId()));
    }

    @GetMapping("/conversations/{id}")
    public ApiResult<AgentConversation> get(@PathVariable Long id) {
        UserPrincipal u = AuthSupport.requireUser();
        return ApiResult.ok(agentService.get(AuthSupport.requireOrgId(), u.getUserId(), id));
    }

    @PostMapping("/conversations")
    public ApiResult<AgentConversation> create(@RequestBody(required = false) Map<String, Object> body) {
        UserPrincipal u = AuthSupport.requireUser();
        String title = body == null || body.get("title") == null ? "新对话" : String.valueOf(body.get("title"));
        return ApiResult.ok(agentService.create(AuthSupport.requireOrgId(), u.getUserId(), title));
    }

    @PostMapping("/conversations/{id}/messages")
    public ApiResult<AgentConversation> send(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UserPrincipal u = AuthSupport.requireUser();
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
        return ApiResult.ok(agentService.send(AuthSupport.requireOrgId(), u.getUserId(), id, content));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        UserPrincipal u = AuthSupport.requireUser();
        agentService.delete(AuthSupport.requireOrgId(), u.getUserId(), id);
        return ApiResult.ok(null);
    }
}
