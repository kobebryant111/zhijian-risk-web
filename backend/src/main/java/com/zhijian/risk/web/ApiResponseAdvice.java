package com.zhijian.risk.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 把 Controller 返回的业务对象包成统一信封。已经是信封的不再包一层。
 */
@RestControllerAdvice(basePackages = "com.zhijian.risk.web")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper mapper;

    public ApiResponseAdvice(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResult<?> || body instanceof byte[]) return body;
        String path = request.getURI().getPath();
        if (path != null && !path.startsWith("/api")) return body;
        if (body instanceof String text) {
            try {
                return mapper.writeValueAsString(ApiResult.ok(text));
            } catch (Exception e) {
                return text;
            }
        }
        return ApiResult.ok(body);
    }
}
