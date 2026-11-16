package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.RiskRule;
import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.excel.ExcelFiles;
import com.zhijian.risk.mapper.RiskRuleMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 风险规则库：默认规则种子与组织内规则维护。
 */
@Service
public class RuleService {
    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    private final RiskRuleMapper repo;

    public RuleService(RiskRuleMapper repo) { this.repo = repo; }

    /**
     * 列表查询。
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public List<RiskRule> list(Long orgId) {
        log.debug("list orgId={}", orgId);

        return repo.findByOrgIdOrderByUpdatedAtDesc(orgId);
    }

    /**
     * 创建资源。
     * @param orgId orgId
     * @param body body
     */
    @Transactional
    public RiskRule create(Long orgId, Map<String, Object> body) {
        log.info("create orgId={}", orgId);

        RiskRule r = new RiskRule();
        r.setOrgId(orgId);
        apply(r, body, true);
        r.setUpdatedAt(LocalDateTime.now());
        return repo.save(r);
    }

    /**
     * 更新资源。
     * @param orgId orgId
     * @param id id
     * @param body body
     */
    @Transactional
    public RiskRule update(Long orgId, Long id, Map<String, Object> body) {
        log.info("update orgId={}, id={}", orgId, id);

        RiskRule r = repo.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "规则不存在"));
        apply(r, body, false);
        r.setUpdatedAt(LocalDateTime.now());
        return repo.save(r);
    }

    /**
     * 删除资源。
     * @param orgId orgId
     * @param id id
     */
    @Transactional
    public void delete(Long orgId, Long id) {
        log.info("delete orgId={}, id={}", orgId, id);

        RiskRule r = repo.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "规则不存在"));
        repo.deleteById(r.getId());
    }

    /**
     * 若组织尚无规则则写入默认规则集。
     * @param orgId orgId
     */
    @Transactional
    public void ensureDefaults(Long orgId) {
        log.info("ensureDefaults orgId={}", orgId);

        if (repo.countByOrgId(orgId) > 0) return;
        seed(orgId, "R-ABNORMAL", "common", "经营异常名录命中", 2, "命中经营异常名录", "工商", "企业信息公示");
        seed(orgId, "R-ILLEGAL", "common", "严重违法失信", 1, "列入严重违法失信名单", "工商", "企业信息公示");
        seed(orgId, "R-ZHIXING", "loan", "被执行人风险", 2, "存在被执行人记录", "司法", "执行信息公开");
        seed(orgId, "R-SHIXIN", "loan", "失信被执行人", 1, "存在失信被执行人记录", "司法", "失信被执行人名单");
        seed(orgId, "R-PENALTY", "common", "行政处罚", 3, "近一年存在行政处罚", "监管", "行政处罚决定");
        seed(orgId, "R-SENTIMENT", "common", "舆情负面关键词", 3, "新闻/舆情出现负面关键词", "舆情", "公开检索");
        seed(orgId, "R-RELATED", "guarantee", "关联企业风险传导", 3, "关联主体存在高风险线索", "关联", "图谱传导");
        seed(orgId, "R-COURT", "ops", "开庭公告诉讼", 4, "存在开庭公告或诉讼信息", "司法", "开庭公告");
    }

    public byte[] template() {
        return ExcelFiles.workbook("规则库",
                List.of("编码", "风险点", "适用类型", "等级", "触发说明", "数据来源", "法规依据", "关键词", "启用"),
                List.of(
                        "从第 2 行开始填写，不要修改表头。",
                        "风险点必填。编码留空则新增；同一组织内编码相同的规则会更新。",
                        "适用类型可填：通用、助贷、融担、运营。留空视为通用。",
                        "等级填 1 到 4。关键词用顿号或逗号分隔。启用填：是 或 否，留空视为是。"));
    }

    /**
     * 编码已存在则更新，编码留空则新增。单行校验失败不影响其他行。
     */
    @Transactional
    public Dtos.ImportOutcome importExcel(Long orgId, byte[] bytes) {
        List<ExcelFiles.SheetRow> rows;
        try {
            rows = ExcelFiles.read(bytes);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "表格没有数据行");
        if (!ExcelFiles.hasHeader(rows, "风险点")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "表头需要包含「风险点」");
        }
        Map<String, RiskRule> byCode = new LinkedHashMap<>();
        for (RiskRule existing : repo.findByOrgIdOrderByUpdatedAtDesc(orgId)) {
            if (existing.getRuleCode() != null && !existing.getRuleCode().isBlank()) {
                byCode.putIfAbsent(existing.getRuleCode().trim(), existing);
            }
        }
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();
        for (ExcelFiles.SheetRow row : rows) {
            String point = ExcelFiles.cell(row.cells(), "风险点", "riskPoint");
            if (point.isBlank()) {
                errors.add("第" + row.rowNumber() + "行：风险点不能为空");
                continue;
            }
            if (point.length() > 200) {
                errors.add("第" + row.rowNumber() + "行：风险点不能超过 200 字");
                continue;
            }
            String typeRaw = ExcelFiles.cell(row.cells(), "适用类型", "partnerType");
            String type = typeRaw.isBlank() ? "common" : TYPE_LABELS.get(typeRaw);
            if (type == null) {
                errors.add("第" + row.rowNumber() + "行：适用类型无法识别");
                continue;
            }
            int level;
            try {
                level = parseLevel(ExcelFiles.cell(row.cells(), "等级", "level"));
            } catch (IllegalArgumentException e) {
                errors.add("第" + row.rowNumber() + "行：" + e.getMessage());
                continue;
            }
            String code = ExcelFiles.cell(row.cells(), "编码", "ruleCode");
            if (code.length() > 64) {
                errors.add("第" + row.rowNumber() + "行：编码不能超过 64 字");
                continue;
            }
            String trigger = clip(ExcelFiles.cell(row.cells(), "触发说明", "triggerText"), 500);
            String source = clip(ExcelFiles.cell(row.cells(), "数据来源", "dataSource"), 200);
            String legal = clip(ExcelFiles.cell(row.cells(), "法规依据", "legalBasis"), 500);
            String keywords;
            try {
                keywords = keywordsJson(ExcelFiles.cell(row.cells(), "关键词", "keywords"));
            } catch (IllegalArgumentException e) {
                errors.add("第" + row.rowNumber() + "行：" + e.getMessage());
                continue;
            }
            boolean enabled = parseEnabled(ExcelFiles.cell(row.cells(), "启用", "enabled"));
            RiskRule rule = code.isBlank() ? null : byCode.get(code);
            boolean fresh = rule == null;
            if (fresh) {
                rule = new RiskRule();
                rule.setOrgId(orgId);
                rule.setRuleCode(code.isBlank() ? "R-" + row.rowNumber() + "-" + System.currentTimeMillis() : code);
            }
            rule.setPartnerType(type);
            rule.setRiskPoint(point);
            rule.setLevel(level);
            rule.setTriggerText(trigger);
            rule.setDataSource(source);
            rule.setLegalBasis(legal);
            rule.setKeywordsJson(keywords);
            rule.setEnabled(enabled);
            rule.setUpdatedAt(LocalDateTime.now());
            repo.save(rule);
            byCode.put(rule.getRuleCode(), rule);
            if (fresh) created++;
            else updated++;
        }
        log.info("import rules orgId={} created={} updated={} failed={}", orgId, created, updated, errors.size());
        if (errors.size() <= 20) return new Dtos.ImportOutcome(created, updated, errors);
        List<String> shown = new ArrayList<>(errors.subList(0, 20));
        shown.add("其余 " + (errors.size() - 20) + " 行省略");
        return new Dtos.ImportOutcome(created, updated, shown);
    }

    private static int parseLevel(String raw) {
        if (raw == null || raw.isBlank()) return 3;
        String text = raw.trim().toUpperCase();
        if (text.startsWith("L")) text = text.substring(1);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        try {
            int level = Integer.parseInt(text);
            if (level >= 1 && level <= 4) return level;
        } catch (NumberFormatException ignored) {
            // 下面统一报错
        }
        throw new IllegalArgumentException("等级需要是 1 到 4");
    }

    private static boolean parseEnabled(String raw) {
        if (raw == null || raw.isBlank()) return true;
        return switch (raw.trim().toLowerCase()) {
            case "否", "停用", "0", "false", "n", "no" -> false;
            default -> true;
        };
    }

    private static String keywordsJson(String raw) {
        if (raw == null || raw.isBlank()) return "[]";
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (String part : raw.split("[,，;；、]")) {
            String text = part.trim();
            if (text.isEmpty()) continue;
            joiner.add("\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        String json = joiner.toString();
        if (json.length() > 1000) throw new IllegalArgumentException("关键词过长");
        return json;
    }

    private static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("通用", "common"), Map.entry("common", "common"),
            Map.entry("助贷", "loan"), Map.entry("loan", "loan"),
            Map.entry("融担", "guarantee"), Map.entry("融资担保", "guarantee"), Map.entry("guarantee", "guarantee"),
            Map.entry("运营", "ops"), Map.entry("运营辅助", "ops"), Map.entry("ops", "ops"));

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void seed(Long orgId, String code, String type, String point, int level, String trigger, String source, String legal) {
        RiskRule r = new RiskRule();
        r.setOrgId(orgId);
        r.setRuleCode(code);
        r.setPartnerType(type);
        r.setRiskPoint(point);
        r.setLevel(level);
        r.setTriggerText(trigger);
        r.setDataSource(source);
        r.setLegalBasis(legal);
        r.setKeywordsJson("[]");
        r.setEnabled(true);
        r.setUpdatedAt(LocalDateTime.now());
        repo.save(r);
    }

    private void apply(RiskRule r, Map<String, Object> body, boolean creating) {
        if (body.get("ruleCode") != null) r.setRuleCode(String.valueOf(body.get("ruleCode")).trim());
        if (body.get("partnerType") != null) r.setPartnerType(String.valueOf(body.get("partnerType")).trim());
        if (body.get("riskPoint") != null) r.setRiskPoint(String.valueOf(body.get("riskPoint")).trim());
        if (body.get("level") != null) r.setLevel(Integer.parseInt(String.valueOf(body.get("level"))));
        if (body.get("triggerText") != null) r.setTriggerText(String.valueOf(body.get("triggerText")));
        if (body.get("dataSource") != null) r.setDataSource(String.valueOf(body.get("dataSource")));
        if (body.get("legalBasis") != null) r.setLegalBasis(String.valueOf(body.get("legalBasis")));
        if (body.get("keywordsJson") != null) r.setKeywordsJson(String.valueOf(body.get("keywordsJson")));
        if (body.get("enabled") != null) r.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        if (creating) {
            if (r.getRuleCode() == null || r.getRuleCode().isBlank()) r.setRuleCode("R-" + System.currentTimeMillis());
            if (r.getPartnerType() == null || r.getPartnerType().isBlank()) r.setPartnerType("common");
            if (r.getRiskPoint() == null || r.getRiskPoint().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "风险点不能为空");
            }
            if (r.getLevel() < 1 || r.getLevel() > 4) r.setLevel(3);
        }
    }
}
