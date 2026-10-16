package com.zhijian.risk.dto;

import com.zhijian.risk.domain.OrgRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class Dtos {
    public record RegisterReq(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            @NotBlank @Size(max = 64) String displayName) {}

    public record LoginReq(
            @NotBlank String username,
            @NotBlank String password,
            Long orgId) {}

    public record ChangePasswordReq(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 6, max = 64) String newPassword) {}

    public record AuthResp(
            String token,
            Long userId,
            String username,
            String displayName,
            boolean platformAdmin,
            Long currentOrgId,
            String currentOrgName,
            String currentOrgRole,
            List<OrgBrief> orgs) {}

    public record OrgBrief(Long id, String code, String name, String role) {}

    public record CreateOrgReq(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            String description) {}

    public record OrgDetail(
            Long id, String code, String name, String description, boolean enabled, int memberCount) {}

    public record AddMemberReq(
            @NotNull Long userId,
            @NotNull OrgRole role) {}

    public record UpdateMemberRoleReq(@NotNull OrgRole role) {}

    public record MemberView(
            Long userId, String username, String displayName, String role, String joinedAt) {}

    public record SwitchOrgReq(@NotNull Long orgId) {}

    public record UserBrief(Long id, String username, String displayName) {}

    public record PartnerReq(
            @NotBlank String name,
            String alias,
            String partnerType,
            String status,
            String uscc) {}

    public record PartnerView(
            Long id, String name, String alias, String partnerType, String status, String uscc, String updatedAt) {}
}
