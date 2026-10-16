package com.zhijian.risk.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.OrgMember;
import com.zhijian.risk.domain.OrgRole;
import com.zhijian.risk.domain.Organization;
import com.zhijian.risk.domain.Partner;
import com.zhijian.risk.domain.UserAccount;
import com.zhijian.risk.mapper.OrgMemberMapper;
import com.zhijian.risk.mapper.OrganizationMapper;
import com.zhijian.risk.mapper.PartnerMapper;
import com.zhijian.risk.mapper.UserAccountMapper;
import com.zhijian.risk.service.RuleService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 演示账号、组织、机构与默认规则种子数据。
 */
@Configuration
public class DataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seed(UserAccountMapper users, OrganizationMapper orgs,
                           OrgMemberMapper members, PartnerMapper partners,
                           PasswordEncoder encoder, RuleService ruleService) {
        return args -> {
            if (users.count() > 0) {
                orgs.findAll().forEach(o -> ruleService.ensureDefaults(o.getId()));
                return;
            }

            UserAccount admin = user(users, encoder, "admin", "平台管理员", "Admin@123", true);
            UserAccount alice = user(users, encoder, "alice", "张敏", "User@123", false);
            UserAccount bob = user(users, encoder, "bob", "李强", "User@123", false);

            Organization o1 = org(orgs, "risk-coop", "风险管理部-合作机构组", "合作机构监测与准入");
            Organization o2 = org(orgs, "risk-ops", "风险管理部-运营辅助组", "运营类合作方管理");

            member(members, o1.getId(), admin.getId(), OrgRole.ORG_ADMIN);
            member(members, o1.getId(), alice.getId(), OrgRole.ORG_OPERATOR);
            member(members, o2.getId(), admin.getId(), OrgRole.ORG_ADMIN);
            member(members, o2.getId(), bob.getId(), OrgRole.ORG_REVIEWER);

            partner(partners, o1.getId(), "某某助贷科技有限公司", "某某助贷", "loan");
            partner(partners, o1.getId(), "航卓融资担保有限公司", "航卓融担", "guarantee");
            partner(partners, o2.getId(), "某某运营服务有限公司", "某某运营", "ops");

            ruleService.ensureDefaults(o1.getId());
            ruleService.ensureDefaults(o2.getId());
        };
    }

    private static UserAccount user(UserAccountMapper repo, PasswordEncoder encoder,
                                    String username, String name, String pwd, boolean admin) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setDisplayName(name);
        u.setPasswordHash(encoder.encode(pwd));
        u.setPlatformAdmin(admin);
        return repo.save(u);
    }

    private static Organization org(OrganizationMapper repo, String code, String name, String desc) {
        Organization o = new Organization();
        o.setCode(code);
        o.setName(name);
        o.setDescription(desc);
        return repo.save(o);
    }

    private static void member(OrgMemberMapper repo, Long orgId, Long userId, OrgRole role) {
        OrgMember m = new OrgMember();
        m.setOrgId(orgId);
        m.setUserId(userId);
        m.setRole(role);
        repo.save(m);
    }

    private static void partner(PartnerMapper repo, Long orgId, String name, String alias, String type) {
        Partner p = new Partner();
        p.setOrgId(orgId);
        p.setName(name);
        p.setAlias(alias);
        p.setPartnerType(type);
        p.setStatus("active");
        repo.save(p);
    }
}
