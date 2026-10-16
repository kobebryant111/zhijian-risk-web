package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.security.UserPrincipal;
import com.zhijian.risk.service.OrgService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织与成员 HTTP 接口。
 */
@RestController
@RequestMapping("/api/orgs")
public class OrgController {
    private static final Logger log = LoggerFactory.getLogger(OrgController.class);

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    /**
     * 列表查询。
     */
    @GetMapping
    public List<Dtos.OrgDetail> list() {
        log.debug("list start");

        return orgService.listVisible(AuthSupport.requireUser());
    }

    /**
     * 创建资源。
     * @param req req
     */
    @PostMapping
    public Dtos.OrgDetail create(@Valid @RequestBody Dtos.CreateOrgReq req) {
        log.info("create start");

        return orgService.create(AuthSupport.requireUser(), req);
    }

    /**
     * 成员列表接口。
     * @param orgId orgId
     */
    @GetMapping("/{orgId}/members")
    public List<Dtos.MemberView> members(@PathVariable Long orgId) {
        log.info("members orgId={}", orgId);

        return orgService.listMembers(AuthSupport.requireUser(), orgId);
    }

    /**
     * 添加组织成员。
     * @param orgId orgId
     * @param req req
     */
    @PostMapping("/{orgId}/members")
    public void addMember(@PathVariable Long orgId, @Valid @RequestBody Dtos.AddMemberReq req) {
        log.info("addMember orgId={}", orgId);

        orgService.addMember(AuthSupport.requireUser(), orgId, req);
    }

    /**
     * 更新成员角色。
     * @param orgId orgId
     * @param userId userId
     * @param req req
     */
    @PatchMapping("/{orgId}/members/{userId}")
    public void updateRole(@PathVariable Long orgId, @PathVariable Long userId,
                           @Valid @RequestBody Dtos.UpdateMemberRoleReq req) {
        log.info("updateRole orgId={}, userId={}", orgId, userId);

        orgService.updateRole(AuthSupport.requireUser(), orgId, userId, req);
    }

    /**
     * 移除资源。
     * @param orgId orgId
     * @param userId userId
     */
    @DeleteMapping("/{orgId}/members/{userId}")
    public void remove(@PathVariable Long orgId, @PathVariable Long userId) {
        log.info("remove orgId={}, userId={}", orgId, userId);

        orgService.removeMember(AuthSupport.requireUser(), orgId, userId);
    }

    @GetMapping("/users/search")
    public List<Dtos.UserBrief> searchUsers(@RequestParam(required = false) String q) {
        AuthSupport.requireUser();
        return orgService.searchUsers(q);
    }
}
