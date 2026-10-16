package com.zhijian.risk.web;

import com.zhijian.risk.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Controller 访问日志。异常栈交给全局异常处理器，这里不重复打。
 */
@Aspect
@Component
public class ControllerLogAspect {
    private static final Logger log = LoggerFactory.getLogger(ControllerLogAspect.class);
    private static final Set<String> KEEP = Set.of(
            "id", "orgid", "partnerid", "userid", "kind", "status", "level", "page", "size",
            "q", "scenario", "partnertype", "period", "name", "code");

    @Around("within(com.zhijian.risk.web..*) && @within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        HttpServletRequest request = currentRequest();
        String http = request == null ? "-" : request.getMethod() + " " + request.getRequestURI();
        UserPrincipal user = currentUser();
        log.info("api {} user={} org={} requestId={} args={}",
                http,
                user == null ? "-" : user.getUserId(),
                user == null || user.getOrgId() == null ? "-" : user.getOrgId(),
                MDC.get(RequestIdFilter.MDC_KEY),
                summarize(joinPoint.getArgs()));
        Object result = joinPoint.proceed();
        long ms = (System.nanoTime() - start) / 1_000_000;
        log.info("api done {} {}ms size={} requestId={}", http, ms, sizeOf(result), MDC.get(RequestIdFilter.MDC_KEY));
        return result;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) return principal;
        return null;
    }

    private String summarize(Object[] args) {
        if (args == null || args.length == 0) return "-";
        StringJoiner joiner = new StringJoiner(", ");
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof Number || arg instanceof Boolean) {
                joiner.add(String.valueOf(arg));
            } else if (arg instanceof String text) {
                joiner.add(clip(text));
            } else if (arg instanceof Map<?, ?> map) {
                joiner.add(mapKeys(map));
            } else {
                joiner.add(arg.getClass().getSimpleName());
            }
        }
        String text = joiner.toString();
        return text.isBlank() ? "-" : text;
    }

    private String mapKeys(Map<?, ?> map) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!KEEP.contains(key.toLowerCase())) continue;
            Object value = entry.getValue();
            if (value == null) continue;
            joiner.add(key + "=" + clip(String.valueOf(value)));
        }
        String text = joiner.toString();
        return text.isBlank() ? "body" : text;
    }

    private String sizeOf(Object result) {
        if (result instanceof Collection<?> collection) return String.valueOf(collection.size());
        if (result == null) return "0";
        return "1";
    }

    private String clip(String text) {
        String trimmed = text.replace('\n', ' ').trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "…";
    }
}
