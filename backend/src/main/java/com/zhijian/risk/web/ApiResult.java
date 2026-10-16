package com.zhijian.risk.web;

/**
 * 全站接口信封。成功时 code 为 0，失败时 code 与 HTTP 状态一致。
 */
public record ApiResult<T>(int code, String message, T data) {
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message == null || message.isBlank() ? "请求失败" : message, null);
    }
}
