package com.zhijian.risk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 已有库可能按早期默认长度建表，schema.sql 又不能重复执行（会删表）。
 * 启动时把装正文和 JSON 的列对齐成脚本里的长度，避免准入检查项写入被截断。
 */
@Component
public class ColumnWidthPatch implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(ColumnWidthPatch.class);

    private final JdbcTemplate jdbc;

    public ColumnWidthPatch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // 早期建库把正文建成了 TINYTEXT（约 255 字节）。只改本应用的表。
        List<Map<String, Object>> tiny = jdbc.queryForList("""
                SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'tinytext'
                  AND TABLE_NAME IN (
                    'users','organizations','org_members','partners','org_settings','risk_rules',
                    'risk_clues','whistle_reports','admission_reviews','finance_reports','agent_conversations'
                  )
                """);
        for (Map<String, Object> row : tiny) {
            String table = String.valueOf(row.get("TABLE_NAME"));
            String column = String.valueOf(row.get("COLUMN_NAME"));
            String nullable = "YES".equals(String.valueOf(row.get("IS_NULLABLE"))) ? "NULL" : "NOT NULL";
            String comment = row.get("COLUMN_COMMENT") == null ? "" : String.valueOf(row.get("COLUMN_COMMENT")).replace("'", "''");
            ensure(table, column, "longtext", null,
                    "LONGTEXT " + nullable + " COMMENT '" + comment + "'");
        }

        varchar("admission_reviews", "summary", 2000, "评审摘要");
        varchar("whistle_reports", "summary", 2000, "报告摘要");
        varchar("finance_reports", "summary", 2000, "报告摘要");

        jdbc.update("""
                UPDATE admission_reviews
                SET summary = '评审失败：内容超过字段长度，请重新评审'
                WHERE status = 'failed' AND summary LIKE '%Data too long for column%'
                """);
    }

    private void varchar(String table, String column, int length, String comment) {
        ensure(table, column, "varchar", length,
                "VARCHAR(" + length + ") NULL COMMENT '" + comment + "'");
    }

    private void ensure(String table, String column, String wantType, Integer minChars, String ddl) {
        if (!table.matches("[a-z_]+") || !column.matches("[a-z_]+")) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                table, column);
        if (rows.isEmpty()) return;
        String type = String.valueOf(rows.get(0).get("DATA_TYPE"));
        Object max = rows.get(0).get("CHARACTER_MAXIMUM_LENGTH");
        long maxLen = max instanceof Number n ? n.longValue() : Long.MAX_VALUE;
        if ("longtext".equalsIgnoreCase(type)) return;
        if ("mediumtext".equalsIgnoreCase(type) && !"longtext".equalsIgnoreCase(wantType)) return;
        if ("text".equalsIgnoreCase(type) && "varchar".equalsIgnoreCase(wantType)) return;
        if (wantType.equalsIgnoreCase(type) && (minChars == null || maxLen >= minChars)) return;
        jdbc.execute("ALTER TABLE `" + table + "` MODIFY `" + column + "` " + ddl);
        log.info("widened {}.{} from {} to {}", table, column, type, wantType);
    }
}
