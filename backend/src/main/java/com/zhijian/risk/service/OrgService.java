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
import com.zhijian.risk.security.UserPrincipal;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织与成员管理：创建组织、成员增删改、可见组织列表。
 */
@Service
public class OrgService {
    private static final Logger log = LoggerFactory.getLogger(OrgService.class);

    private final OrganizationMapper orgRepo;
    private final OrgMemberMapper memberRepo;
    private final UserAccountMapper userRepo;
    private final RuleService ruleService;

    public OrgService(OrganizationMapper orgRepo, OrgMemberMapper memberRepo, UserAccountMapper userRepo, RuleService ruleService) {
        this.orgRepo = orgRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.ruleService = ruleService;
    }

    /**
     * 创建资源。
     * @param actor actor
     * @param req req
     */
    @Transactional
    public Dtos.OrgDetail create(UserPrincipal actor, Dtos.CreateOrgReq req) {
        log.info("create actor={}", actor);

        if (!actor.isPlatformAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "仅系统管理员可创建组织");
        }
        String code = req.code().trim();
        if (orgRepo.existsByCode(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "组织编码已存在");
        }
        Organization o = new Organization();
        o.setCode(code);
        o.setName(req.name().trim());
        o.setDescription(req.description() == null ? "" : req.description().trim());
        orgRepo.save(o);

        // creator joins as ORG_ADMIN
        OrgMember m = new OrgMember();
        m.setOrgId(o.getId());
        m.setUserId(actor.getUserId());
        m.setRole(OrgRole.ORG_ADMIN);
        memberRepo.save(m);
        ruleService.ensureDefaults(o.getId());
        return toDetail(o);
    }

    /**
     * 列出当前用户可见组织。
     * @param actor actor
     */
    @Transactional(readOnly = true)
    public List<Dtos.OrgDetail> listVisible(UserPrincipal actor) {
        log.info("listVisible actor={}", actor);

        List<Organization> all;
        if (actor.isPlatformAdmin()) {
            all = orgRepo.findAll();
        } else {
            all = new ArrayList<>();
            for (OrgMember m : memberRepo.findByUserId(actor.getUserId())) {
                orgRepo.findById(m.getOrgId()).ifPresent(all::add);
            }
        }
        return all.stream().map(this::toDetail).toList();
    }

    /**
     * 列出组织成员。
     * @param actor actor
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public List<Dtos.MemberView> listMembers(UserPrincipal actor, Long orgId) {
        log.info("listMembers actor={}, orgId={}", actor, orgId);

        assertCanManageOrView(actor, orgId);
        List<Dtos.MemberView> out = new ArrayList<>();
        for (OrgMember m : memberRepo.findByOrgId(orgId)) {
            UserAccount u = userRepo.findById(m.getUserId()).orElse(null);
            if (u == null) continue;
            out.add(new Dtos.MemberView(u.getId(), u.getUsername(), u.getDisplayName(),
                    m.getRole().name(), m.getJoinedAt().toString()));
        }
        return out;
    }

    /**
     * 添加组织成员。
     * @param actor actor
     * @param orgId orgId
     * @param req req
     */
    @Transactional
    public void addMember(UserPrincipal actor, Long orgId, Dtos.AddMemberReq req) {
        log.info("addMember actor={}, orgId={}", actor, orgId);

        assertCanManage(actor, orgId);
        if (!orgRepo.existsById(orgId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "组织不存在");
        }
        UserAccount target = userRepo.findById(req.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (memberRepo.existsByOrgIdAndUserId(orgId, target.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户已在组织中");
        }
        OrgMember m = new OrgMember();
        m.setOrgId(orgId);
        m.setUserId(target.getId());
        m.setRole(req.role());
        memberRepo.save(m);
    }

    /**
     * 更新成员角色。
     * @param actor actor
     * @param orgId orgId
     * @param userId userId
     * @param req req
     */
    @Transactional
    public void updateRole(UserPrincipal actor, Long orgId, Long userId, Dtos.UpdateMemberRoleReq req) {
        log.info("updateRole actor={}, orgId={}, userId={}", actor, orgId, userId);

        assertCanManage(actor, orgId);
        OrgMember m = memberRepo.findByOrgIdAndUserId(orgId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "成员不存在"));
        m.setRole(req.role());
        memberRepo.save(m);
    }

    /**
     * 移除组织成员。
     * @param actor actor
     * @param orgId orgId
     * @param userId userId
     */
    @Transactional
    public void removeMember(UserPrincipal actor, Long orgId, Long userId) {
        log.info("removeMember actor={}, orgId={}, userId={}", actor, orgId, userId);

        assertCanManage(actor, orgId);
        if (!memberRepo.existsByOrgIdAndUserId(orgId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "成员不存在");
        }
        memberRepo.deleteByOrgIdAndUserId(orgId, userId);
    }

    /**
     * 按关键字搜索用户。
     * @param q q
     */
    @Transactional(readOnly = true)
    public List<Dtos.UserBrief> searchUsers(String q) {
        log.info("searchUsers q={}", q);

        if (!com.zhijian.risk.web.AuthSupport.requireUser().isPlatformAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "仅系统管理员可分配成员");
        }
        String key = q == null ? "" : q.trim();
        List<UserAccount> list = key.isEmpty()
                ? userRepo.findAll()
                : userRepo.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(key, key);
        return list.stream()
                .filter(UserAccount::isEnabled)
                .limit(30)
                .map(u -> new Dtos.UserBrief(u.getId(), u.getUsername(), u.getDisplayName()))
                .toList();
    }

    private void assertCanManageOrView(UserPrincipal actor, Long orgId) {
        if (actor.isPlatformAdmin()) return;
        if (memberRepo.existsByOrgIdAndUserId(orgId, actor.getUserId())) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "无权查看该组织");
    }

    private void assertCanManage(UserPrincipal actor, Long orgId) {
        if (actor.isPlatformAdmin()) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "仅系统管理员可分配组织成员");
    }

    private Dtos.OrgDetail toDetail(Organization o) {
        int count = memberRepo.findByOrgId(o.getId()).size();
        return new Dtos.OrgDetail(o.getId(), o.getCode(), o.getName(), o.getDescription(), o.isEnabled(), count);
    }
}
