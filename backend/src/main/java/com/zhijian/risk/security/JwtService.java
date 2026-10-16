package com.zhijian.risk.security;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * JWT 签发与解析。
 */
@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long expireHours;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expire-hours:72}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    /**
     * 签发 JWT。
     * @param userId userId
     * @param username username
     * @param platformAdmin platformAdmin
     * @param orgId orgId
     * @param orgRole orgRole
     */
    public String issue(Long userId, String username, boolean platformAdmin, Long orgId, String orgRole) {
        log.info("issue userId={}, username={}, platformAdmin={}, orgId={}, orgRole={}", userId, username, platformAdmin, orgId, orgRole);

        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "username", username,
                        "platformAdmin", platformAdmin,
                        "orgId", orgId == null ? -1L : orgId,
                        "orgRole", orgRole == null ? "" : orgRole
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 JWT。
     * @param token token
     */
    public Claims parse(String token) {
        log.debug("parse start");

        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
