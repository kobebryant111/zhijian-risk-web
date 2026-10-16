package com.zhijian.risk.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常收口，失败响应与成功响应使用同一信封。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handle(ApiException e, HttpServletRequest request) {
        int status = e.getStatus().value();
        log.warn("api fail status={} path={} requestId={} message={}", status, request.getRequestURI(), requestId(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(ApiResult.fail(status, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("api fail status=400 path={} requestId={} message={}", request.getRequestURI(), requestId(), message);
        return ResponseEntity.badRequest().body(ApiResult.fail(400, message));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResult<Void>> handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("api fail status=400 path={} requestId={} message={}", request.getRequestURI(), requestId(), e.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.fail(400, "请求参数不正确"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("api fail status=403 path={} requestId={}", request.getRequestURI(), requestId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResult.fail(403, "无权限"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception e, HttpServletRequest request) {
        log.error("api fail status=500 path={} requestId={}", request.getRequestURI(), requestId(), e);
        return ResponseEntity.internalServerError().body(ApiResult.fail(500, "服务器错误"));
    }

    private String requestId() {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        return id == null ? "-" : id;
    }
}
