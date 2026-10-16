package com.zhijian.risk.web;

import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.service.OrgService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 组织与成员。创建组织和分配成员仅系统管理员可调用。 */
@RestController
@RequestMapping("/api/orgs")
public class OrgController {
    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping
    public ApiResult<List<Dtos.OrgDetail>> list() {
        return ApiResult.ok(orgService.listVisible(AuthSupport.requireUser()));
    }

    @PostMapping
    public ApiResult<Dtos.OrgDetail> create(@Valid @RequestBody Dtos.CreateOrgReq req) {
        return ApiResult.ok(orgService.create(AuthSupport.requireUser(), req));
    }

    @GetMapping("/{orgId}/members")
    public ApiResult<List<Dtos.MemberView>> members(@PathVariable Long orgId) {
        return ApiResult.ok(orgService.listMembers(AuthSupport.requireUser(), orgId));
    }

    @PostMapping("/{orgId}/members")
    public ApiResult<Void> addMember(@PathVariable Long orgId, @Valid @RequestBody Dtos.AddMemberReq req) {
        orgService.addMember(AuthSupport.requireUser(), orgId, req);
        return ApiResult.ok(null);
    }

    @PatchMapping("/{orgId}/members/{userId}")
    public ApiResult<Void> updateRole(@PathVariable Long orgId, @PathVariable Long userId,
                                      @Valid @RequestBody Dtos.UpdateMemberRoleReq req) {
        orgService.updateRole(AuthSupport.requireUser(), orgId, userId, req);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    public ApiResult<Void> remove(@PathVariable Long orgId, @PathVariable Long userId) {
        orgService.removeMember(AuthSupport.requireUser(), orgId, userId);
        return ApiResult.ok(null);
    }

    @GetMapping("/users/search")
    public ApiResult<List<Dtos.UserBrief>> searchUsers(@RequestParam(required = false) String q) {
        return ApiResult.ok(orgService.searchUsers(q));
    }
}
