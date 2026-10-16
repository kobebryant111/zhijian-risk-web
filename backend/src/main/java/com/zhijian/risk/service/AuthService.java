package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.OrgMember;
import com.zhijian.risk.domain.OrgRole;
import com.zhijian.risk.domain.Organization;
import com.zhijian.risk.domain.UserAccount;
import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.mapper.OrgMemberMapper;
import com.zhijian.risk.mapper.OrganizationMapper;
import com.zhijian.risk.mapper.UserAccountMapper;
import com.zhijian.risk.security.JwtService;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 认证与登录态：注册、登录、切换组织、签发 JWT。
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountMapper userRepo;
    private final OrgMemberMapper memberRepo;
    private final OrganizationMapper orgRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserAccountMapper userRepo, OrgMemberMapper memberRepo,
                       OrganizationMapper orgRepo, PasswordEncoder encoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.orgRepo = orgRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    /**
     * 用户注册并返回登录态。
     * @param req req
     */
    @Transactional
    public Dtos.AuthResp register(Dtos.RegisterReq req) {
        log.info("register start");

        if (userRepo.existsByUsername(req.username())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }
        UserAccount u = new UserAccount();
        u.setUsername(req.username().trim());
        u.setDisplayName(req.displayName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setPlatformAdmin(false);
        userRepo.save(u);
        return buildAuth(u, null, null);
    }

    /**
     * 用户登录，可选绑定组织。
     * @param req req
     */
    @Transactional(readOnly = true)
    public Dtos.AuthResp login(Dtos.LoginReq req) {
        log.info("login start");

        UserAccount u = userRepo.findByUsername(req.username().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!u.isEnabled() || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        Long orgId = req.orgId();
        OrgRole role = null;
        if (orgId != null) {
            OrgMember m = memberRepo.findByOrgIdAndUserId(orgId, u.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "你不属于该组织"));
            role = m.getRole();
        } else {
            List<OrgMember> memberships = memberRepo.findByUserId(u.getId());
            if (memberships.size() == 1) {
                orgId = memberships.get(0).getOrgId();
                role = memberships.get(0).getRole();
            }
        }
        return buildAuth(u, orgId, role);
    }

    /**
     * 校验原密码后更新当前用户密码。
     */
    @Transactional
    public void changePassword(Long userId, Dtos.ChangePasswordReq req) {
        log.info("changePassword userId={}", userId);
        UserAccount u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        if (!encoder.matches(req.oldPassword(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        if (req.oldPassword().equals(req.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        userRepo.save(u);
    }

    /**
     * 返回当前登录用户信息。
     * @param userId userId
     * @param orgId orgId
     * @param orgRole orgRole
     */
    @Transactional(readOnly = true)
    public Dtos.AuthResp me(Long userId, Long orgId, OrgRole orgRole) {
        log.debug("me userId={}, orgId={}, orgRole={}", userId, orgId, orgRole);

        UserAccount u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return buildAuth(u, orgId, orgRole);
    }

    /**
     * 切换当前组织并重签 JWT。
     * @param userId userId
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public Dtos.AuthResp switchOrg(Long userId, Long orgId) {
        log.info("switchOrg userId={}, orgId={}", userId, orgId);

        UserAccount u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        OrgMember m = memberRepo.findByOrgIdAndUserId(orgId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "你不属于该组织"));
        return buildAuth(u, orgId, m.getRole());
    }

    private Dtos.AuthResp buildAuth(UserAccount u, Long orgId, OrgRole role) {
        List<Dtos.OrgBrief> orgs = new ArrayList<>();
        for (OrgMember m : memberRepo.findByUserId(u.getId())) {
            Organization o = orgRepo.findById(m.getOrgId()).orElse(null);
            if (o != null && o.isEnabled()) {
                orgs.add(new Dtos.OrgBrief(o.getId(), o.getCode(), o.getName(), m.getRole().name()));
            }
        }
        orgs.sort(Comparator.comparing(Dtos.OrgBrief::name));
        String orgName = null;
        if (orgId != null) {
            orgName = orgRepo.findById(orgId).map(Organization::getName).orElse(null);
        }
        String token = jwtService.issue(u.getId(), u.getUsername(), u.isPlatformAdmin(), orgId,
                role == null ? null : role.name());
        return new Dtos.AuthResp(token, u.getId(), u.getUsername(), u.getDisplayName(),
                u.isPlatformAdmin(), orgId, orgName, role == null ? null : role.name(), orgs);
    }
}
