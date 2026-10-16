package com.zhijian.risk.security;

import com.zhijian.risk.domain.OrgRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {
    private final Long userId;
    private final String username;
    private final String passwordHash;
    private final boolean platformAdmin;
    private final Long orgId;
    private final OrgRole orgRole;
    private final boolean enabled;

    public UserPrincipal(Long userId, String username, String passwordHash, boolean platformAdmin,
                         Long orgId, OrgRole orgRole, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.platformAdmin = platformAdmin;
        this.orgId = orgId;
        this.orgRole = orgRole;
        this.enabled = enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (platformAdmin) {
            return List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        }
        if (orgRole != null) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + orgRole.name()));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public Long getUserId() { return userId; }
    public boolean isPlatformAdmin() { return platformAdmin; }
    public Long getOrgId() { return orgId; }
    public OrgRole getOrgRole() { return orgRole; }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
