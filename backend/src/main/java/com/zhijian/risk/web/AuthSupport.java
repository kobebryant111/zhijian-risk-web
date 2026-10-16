package com.zhijian.risk.web;

import com.zhijian.risk.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Controller 侧认证辅助：从 SecurityContext 取当前用户/组织。
 */
public final class AuthSupport {
    private static final Logger log = LoggerFactory.getLogger(AuthSupport.class);

    private AuthSupport() {}

    /**
     * 获取当前登录用户，未登录则抛出 401。
     */
    public static UserPrincipal requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            log.debug("requireUser failed: no principal");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return p;
    }

    /**
     * 获取当前组织 ID，未选择组织则抛出 400。
     */
    public static Long requireOrgId() {
        UserPrincipal p = requireUser();
        if (p.getOrgId() == null) {
            log.debug("requireOrgId failed: userId={}", p.getUserId());
            throw new ApiException(HttpStatus.BAD_REQUEST, "请先选择或加入组织");
        }
        return p.getOrgId();
    }
}
