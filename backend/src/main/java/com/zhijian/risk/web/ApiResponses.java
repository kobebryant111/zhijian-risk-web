package com.zhijian.risk.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 把统一信封写进尚未提交的 HTTP 响应。过滤器里的鉴权失败走这里。
 */
public final class ApiResponses {
    private ApiResponses() {}

    public static void write(ObjectMapper mapper, HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), ApiResult.fail(status, message));
    }
}
