-- 智鉴风控官 Web · MySQL 8.x 建表脚本
-- 用法：先创建库，再执行本脚本
--   CREATE DATABASE zhijian_risk DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   USE zhijian_risk;
--   SOURCE schema.sql;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 用户
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`        VARCHAR(64)  NOT NULL COMMENT '登录用户名',
  `display_name`    VARCHAR(64)  NOT NULL COMMENT '显示名称',
  `password_hash`   VARCHAR(200) NOT NULL COMMENT '密码哈希（BCrypt）',
  `platform_admin`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否平台管理员：1是 0否',
  `enabled`         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0停用',
  `created_at`      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号';

-- ----------------------------
-- 组织
-- ----------------------------
DROP TABLE IF EXISTS `organizations`;
CREATE TABLE `organizations` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code`        VARCHAR(64)  NOT NULL COMMENT '组织编码（唯一）',
  `name`        VARCHAR(128) NOT NULL COMMENT '组织名称',
  `description` VARCHAR(500) NULL COMMENT '组织描述',
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0停用',
  `created_at`  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_organizations_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织';

-- ----------------------------
-- 组织成员
-- ----------------------------
DROP TABLE IF EXISTS `org_members`;
CREATE TABLE `org_members` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`     BIGINT      NOT NULL COMMENT '组织ID',
  `user_id`    BIGINT      NOT NULL COMMENT '用户ID',
  `role`       VARCHAR(32) NOT NULL COMMENT '组织角色：ORG_ADMIN/ORG_OPERATOR/ORG_REVIEWER',
  `joined_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_members_org_user` (`org_id`, `user_id`),
  KEY `idx_org_members_user` (`user_id`),
  KEY `idx_org_members_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织成员';

-- ----------------------------
-- 合作机构名单
-- ----------------------------
DROP TABLE IF EXISTS `partners`;
CREATE TABLE `partners` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`       BIGINT       NOT NULL COMMENT '所属组织ID',
  `name`         VARCHAR(200) NOT NULL COMMENT '机构全称',
  `alias`        VARCHAR(64)  NULL COMMENT '机构简称/别名',
  `partner_type` VARCHAR(32)  NULL DEFAULT 'loan' COMMENT '机构类型：loan助贷/guarantee融担/ops运营等',
  `status`       VARCHAR(32)  NULL DEFAULT 'active' COMMENT '状态：active/inactive 等',
  `uscc`         VARCHAR(64)  NULL COMMENT '统一社会信用代码',
  `updated_at`   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_partners_org` (`org_id`),
  KEY `idx_partners_org_type` (`org_id`, `partner_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合作机构';

-- ----------------------------
-- 组织配置（LLM/搜索等）
-- ----------------------------
DROP TABLE IF EXISTS `org_settings`;
CREATE TABLE `org_settings` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`         BIGINT        NOT NULL COMMENT '所属组织ID',
  `setting_key`    VARCHAR(64)   NOT NULL COMMENT '配置键，如 llm.apiKey / baidu.enabled',
  `setting_value`  VARCHAR(4000) NOT NULL DEFAULT '' COMMENT '配置值（密钥等敏感信息存此）',
  `updated_at`     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_org_settings_org_key` (`org_id`, `setting_key`),
  KEY `idx_org_settings_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织级配置';

-- ----------------------------
-- 风险规则
-- ----------------------------
DROP TABLE IF EXISTS `risk_rules`;
CREATE TABLE `risk_rules` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`         BIGINT        NOT NULL COMMENT '所属组织ID',
  `rule_code`      VARCHAR(64)   NOT NULL COMMENT '规则编码',
  `partner_type`   VARCHAR(32)   NOT NULL DEFAULT 'common' COMMENT '适用机构类型：common/loan/guarantee/ops',
  `risk_point`     VARCHAR(200)  NOT NULL COMMENT '风险点名称',
  `level`          INT           NOT NULL DEFAULT 3 COMMENT '风险等级：1最高～4最低',
  `trigger_text`   VARCHAR(500)  NULL DEFAULT '' COMMENT '触发条件说明',
  `data_source`    VARCHAR(200)  NULL DEFAULT '' COMMENT '数据来源',
  `legal_basis`    VARCHAR(500)  NULL DEFAULT '' COMMENT '法规/制度依据',
  `keywords_json`  VARCHAR(1000) NULL DEFAULT '[]' COMMENT '关键词 JSON 数组',
  `enabled`        TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0停用',
  `updated_at`     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_risk_rules_org` (`org_id`),
  KEY `idx_risk_rules_org_code` (`org_id`, `rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险规则';

-- ----------------------------
-- 风险线索
-- ----------------------------
DROP TABLE IF EXISTS `risk_clues`;
CREATE TABLE `risk_clues` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`         BIGINT        NOT NULL COMMENT '所属组织ID',
  `public_id`      VARCHAR(64)   NULL COMMENT '对外线索编号',
  `partner_id`     BIGINT        NULL COMMENT '关联机构ID（可空）',
  `partner_name`   VARCHAR(200)  NOT NULL COMMENT '主体/机构名称',
  `partner_type`   VARCHAR(64)   NULL DEFAULT '' COMMENT '机构类型',
  `level`          INT           NOT NULL DEFAULT 3 COMMENT '风险等级：1最高～4最低',
  `title`          VARCHAR(500)  NOT NULL COMMENT '线索标题',
  `summary`        VARCHAR(4000) NULL DEFAULT '' COMMENT '线索摘要',
  `event_date`     VARCHAR(32)   NULL DEFAULT '' COMMENT '事件日期（YYYY-MM-DD）',
  `owner`          VARCHAR(64)   NULL DEFAULT '合作风控' COMMENT '责任人/跟进人',
  `progress`       INT           NOT NULL DEFAULT 0 COMMENT '处置进度 0～100',
  `status`         VARCHAR(32)   NULL DEFAULT '待复核' COMMENT '状态：待复核/跟进中/已闭环等',
  `source_system`  VARCHAR(64)   NULL DEFAULT '' COMMENT '来源系统：manual/whistle 等',
  `source_url`     VARCHAR(1000) NULL DEFAULT '' COMMENT '来源链接',
  `credibility`    VARCHAR(8)    NULL DEFAULT 'C' COMMENT '可信度：A/B/C',
  `rule_id`        VARCHAR(64)   NULL DEFAULT '' COMMENT '命中规则编码',
  `legal_basis`    VARCHAR(500)  NULL DEFAULT '' COMMENT '法规/依据说明',
  `created_at`     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_risk_clues_org` (`org_id`),
  KEY `idx_risk_clues_org_status` (`org_id`, `status`),
  KEY `idx_risk_clues_org_level` (`org_id`, `level`),
  KEY `idx_risk_clues_partner` (`partner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险线索';

-- ----------------------------
-- 吹哨报告
-- ----------------------------
DROP TABLE IF EXISTS `whistle_reports`;
CREATE TABLE `whistle_reports` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`            BIGINT        NOT NULL COMMENT '所属组织ID',
  `kind`              VARCHAR(32)   NULL DEFAULT 'daily' COMMENT '报告类型：daily日度/monthly月度',
  `period`            VARCHAR(64)   NULL DEFAULT '' COMMENT '报告周期，如日期或月份',
  `title`             VARCHAR(200)  NOT NULL COMMENT '报告标题',
  `summary`           VARCHAR(2000) NULL DEFAULT '' COMMENT '报告摘要',
  `markdown`          LONGTEXT      NULL COMMENT '报告正文（Markdown）',
  `batch_stats_json`  LONGTEXT      NULL COMMENT '跑批统计 JSON',
  `clue_count`        INT           NOT NULL DEFAULT 0 COMMENT '线索/覆盖机构数',
  `level1`            INT           NOT NULL DEFAULT 0 COMMENT 'L1 数量',
  `level2`            INT           NOT NULL DEFAULT 0 COMMENT 'L2 数量',
  `level3`            INT           NOT NULL DEFAULT 0 COMMENT 'L3 数量',
  `level4`            INT           NOT NULL DEFAULT 0 COMMENT 'L4 数量',
  `status`            VARCHAR(32)   NULL DEFAULT 'done' COMMENT '任务状态：running/done/failed',
  `progress`          INT           NOT NULL DEFAULT 100 COMMENT '任务进度 0～100',
  `partner_name`      VARCHAR(200)  NULL DEFAULT '' COMMENT '监测主体（单户或全量描述）',
  `created_at`        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updated_at`        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_whistle_reports_org` (`org_id`),
  KEY `idx_whistle_reports_org_created` (`org_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='吹哨报告';

-- ----------------------------
-- 准入评审
-- ----------------------------
DROP TABLE IF EXISTS `admission_reviews`;
CREATE TABLE `admission_reviews` (
  `id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`               BIGINT        NOT NULL COMMENT '所属组织ID',
  `public_id`            VARCHAR(64)   NULL COMMENT '对外评审编号',
  `partner_name`         VARCHAR(200)  NOT NULL COMMENT '拟准入主体名称',
  `partner_type`         VARCHAR(32)   NULL DEFAULT 'loan' COMMENT '机构类型：loan/guarantee/ops',
  `scenario`             VARCHAR(32)   NULL DEFAULT '准入' COMMENT '场景：准入/续签/扩围',
  `conclusion`           VARCHAR(32)   NULL DEFAULT '待核验' COMMENT '结论：建议准入/有条件准入/不建议准入等',
  `items_json`           LONGTEXT      NULL COMMENT '检查项 JSON',
  `remediation_json`     LONGTEXT      NULL COMMENT '补件/整改项 JSON',
  `summary`              VARCHAR(2000) NULL DEFAULT '' COMMENT '评审摘要',
  `markdown`             LONGTEXT      NULL COMMENT '评审报告（Markdown）',
  `evidence_incomplete`  TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '材料是否不完整：1是 0否',
  `evidence_sources`     VARCHAR(500)  NULL DEFAULT '' COMMENT '证据来源链接（逗号分隔）',
  `ai_notes`             LONGTEXT      NULL COMMENT '大模型原始研判备注',
  `status`               VARCHAR(32)   NULL DEFAULT 'done' COMMENT '任务状态：running/done/failed',
  `progress`             INT           NOT NULL DEFAULT 100 COMMENT '任务进度 0～100',
  `created_at`           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updated_at`           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_admission_reviews_org` (`org_id`),
  KEY `idx_admission_reviews_org_created` (`org_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='准入评审';

-- ----------------------------
-- 经营报告
-- ----------------------------
DROP TABLE IF EXISTS `finance_reports`;
CREATE TABLE `finance_reports` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`           BIGINT        NOT NULL COMMENT '所属组织ID',
  `partner_name`     VARCHAR(200)  NOT NULL COMMENT '分析主体名称',
  `partner_type`     VARCHAR(32)   NULL DEFAULT 'loan' COMMENT '机构类型',
  `period`           VARCHAR(64)   NULL DEFAULT '' COMMENT '分析周期',
  `grade`            VARCHAR(16)   NULL DEFAULT 'B' COMMENT '经营评级：A/B/C/D/F',
  `metrics_json`     LONGTEXT      NULL COMMENT '经营指标 JSON',
  `dimensions_json`  LONGTEXT      NULL COMMENT '维度评分 JSON',
  `markdown`         LONGTEXT      NULL COMMENT '经营报告正文（Markdown）',
  `summary`          VARCHAR(2000) NULL DEFAULT '' COMMENT '报告摘要',
  `status`           VARCHAR(32)   NULL DEFAULT 'done' COMMENT '任务状态：running/done/failed',
  `progress`         INT           NOT NULL DEFAULT 100 COMMENT '任务进度 0～100',
  `created_at`       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updated_at`       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_finance_reports_org` (`org_id`),
  KEY `idx_finance_reports_org_created` (`org_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='经营报告';

-- ----------------------------
-- 问鉴控会话
-- ----------------------------
DROP TABLE IF EXISTS `agent_conversations`;
CREATE TABLE `agent_conversations` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_id`         BIGINT       NOT NULL COMMENT '所属组织ID',
  `user_id`        BIGINT       NOT NULL COMMENT '所属用户ID',
  `title`          VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  `messages_json`  LONGTEXT     NULL COMMENT '消息列表 JSON（role/content/ts/references）',
  `status`         VARCHAR(32)  NULL DEFAULT 'idle' COMMENT '会话状态：idle/running',
  `created_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `updated_at`     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_conv_org_user` (`org_id`, `user_id`),
  KEY `idx_agent_conv_updated` (`org_id`, `user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问鉴控会话';

SET FOREIGN_KEY_CHECKS = 1;
