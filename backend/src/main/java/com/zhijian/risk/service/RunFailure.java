package com.zhijian.risk.service;

/**
 * 任务失败写回页面的短原因。完整异常只进日志，不把 SQL 贴到卡片上。
 */
public final class RunFailure {
    private RunFailure() {}

    public static String summary(String action, Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("Data too long") || raw.contains("Data truncation")) {
            return action + "失败：内容超过字段长度";
        }
        if (raw.contains("###") || raw.contains("com.mysql")) {
            return action + "失败";
        }
        if (raw.length() > 120) raw = raw.substring(0, 120);
        return raw.isBlank() ? action + "失败" : action + "失败: " + raw;
    }
}
