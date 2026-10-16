package com.zhijian.risk.security;

import com.zhijian.risk.domain.OrgRole;
import com.zhijian.risk.domain.UserAccount;
import com.zhijian.risk.mapper.UserAccountMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserAccountMapper userRepo;

    public JwtAuthFilter(JwtService jwtService, UserAccountMapper userRepo) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                UserAccount user = userRepo.findById(userId).orElse(null);
                if (user != null && user.isEnabled()) {
                    Object orgRaw = claims.get("orgId");
                    Long orgId = null;
                    if (orgRaw instanceof Number n) {
                        long v = n.longValue();
                        orgId = v < 0 ? null : v;
                    }
                    String roleStr = claims.get("orgRole", String.class);
                    OrgRole role = (roleStr == null || roleStr.isBlank()) ? null : OrgRole.valueOf(roleStr);
                    UserPrincipal principal = new UserPrincipal(
                            user.getId(), user.getUsername(), user.getPasswordHash(),
                            user.isPlatformAdmin(), orgId, role, user.isEnabled());
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
