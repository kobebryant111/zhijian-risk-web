package com.zhijian.risk.engine;

import com.zhijian.risk.domain.RiskRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌面端 v1.1.0 基线规则 + 组织自定义规则。定级取命中项里等级数字最小的一条。
 */
@Component
public class RuleCatalog {
    private static final Logger log = LoggerFactory.getLogger(RuleCatalog.class);
    public static final String RULE_SET_VERSION = "v1.1.0";

    public record EffectiveRule(String id, String partnerType, int level, String riskPoint,
                                String legalBasis, List<String> keywords, boolean enabled) {}

    public record Grade(int level, String ruleId, String riskPoint, String legalBasis, String version) {}

    private final List<EffectiveRule> baseline;
    private final List<String> riskKeywords;

    public RuleCatalog() {
        this.baseline = loadBaseline();
        this.riskKeywords = loadKeywords();
        log.info("baseline rules={}, riskKeywords={}", baseline.size(), riskKeywords.size());
    }

    public List<String> riskKeywords() { return riskKeywords; }

    public List<EffectiveRule> effective(List<RiskRule> orgRules) {
        Map<String, EffectiveRule> byId = new LinkedHashMap<>();
        for (EffectiveRule r : baseline) byId.put(r.id(), r);
        if (orgRules != null) {
            for (RiskRule r : orgRules) {
                if (r.getRuleCode() == null || r.getRuleCode().isBlank()) continue;
                List<String> kws = parseKeywords(r.getKeywordsJson());
                byId.put(r.getRuleCode(), new EffectiveRule(
                        r.getRuleCode(),
                        r.getPartnerType() == null ? "common" : r.getPartnerType(),
                        r.getLevel(),
                        r.getRiskPoint() == null ? "" : r.getRiskPoint(),
                        r.getLegalBasis() == null ? "" : r.getLegalBasis(),
                        kws,
                        r.isEnabled()
                ));
            }
        }
        List<EffectiveRule> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(EffectiveRule::partnerType).thenComparingInt(EffectiveRule::level).thenComparing(EffectiveRule::id));
        return out;
    }

    public Grade grade(String partnerType, String text, List<EffectiveRule> rules) {
        if (text == null) return null;
        Grade best = null;
        String type = partnerType == null ? "" : partnerType;
        for (EffectiveRule r : rules) {
            if (!r.enabled()) continue;
            if (!"common".equals(r.partnerType()) && !r.partnerType().equals(type)) continue;
            boolean hit = false;
            for (String k : r.keywords()) {
                if (k != null && !k.isBlank() && text.contains(k)) { hit = true; break; }
            }
            if (!hit) continue;
            Grade cand = new Grade(r.level(), r.id(), r.riskPoint(), r.legalBasis(), RULE_SET_VERSION);
            if (best == null || cand.level() < best.level()) best = cand;
        }
        return best;
    }

    public boolean dualMatch(String text, List<String> subjectTerms) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        boolean subject = false;
        for (String t : subjectTerms) {
            if (t != null && !t.isBlank() && lower.contains(t.trim().toLowerCase())) { subject = true; break; }
        }
        if (!subject) return false;
        for (String k : riskKeywords) {
            if (k != null && !k.isBlank() && lower.contains(k.toLowerCase())) return true;
        }
        return false;
    }

    public static String partnerTypeLabel(String raw) {
        if (raw == null) return "";
        return switch (raw) {
            case "common" -> "通用";
            case "loan" -> "助贷";
            case "guarantee" -> "融资担保";
            case "traffic" -> "引流";
            case "payment" -> "支付";
            case "data" -> "数据";
            case "collection" -> "催收";
            case "interbank" -> "金市同业";
            case "ops" -> "运营辅助";
            default -> raw;
        };
    }

    @SuppressWarnings("unchecked")
    private List<EffectiveRule> loadBaseline() {
        List<EffectiveRule> out = new ArrayList<>();
        Yaml yaml = new Yaml();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:rules/baseline/*.yaml");
            for (Resource file : files) {
                String name = file.getFilename() == null ? "" : file.getFilename();
                if (name.startsWith("keywords")) continue;
                try (InputStream in = file.getInputStream()) {
                    Map<String, Object> root = yaml.load(in);
                    if (root == null) continue;
                    String ptype = String.valueOf(root.getOrDefault("partner_type", "common"));
                    Object rules = root.get("rules");
                    if (!(rules instanceof List<?> list)) continue;
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> m)) continue;
                        Map<String, Object> row = (Map<String, Object>) m;
                        List<String> kws = new ArrayList<>();
                        Object rawKws = row.get("match_any_keywords");
                        if (rawKws instanceof List<?> ks) for (Object k : ks) if (k != null) kws.add(String.valueOf(k));
                        boolean enabled = !Boolean.FALSE.equals(row.get("enabled"));
                        int level = row.get("level") instanceof Number n ? n.intValue() : 3;
                        out.add(new EffectiveRule(
                                String.valueOf(row.getOrDefault("id", "")),
                                ptype, level,
                                String.valueOf(row.getOrDefault("risk_point", "")),
                                String.valueOf(row.getOrDefault("legal_basis", "")),
                                kws, enabled
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("load baseline rules failed", e);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<String> loadKeywords() {
        List<String> words = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/rules/baseline/keywords.yaml")) {
            if (in == null) return words;
            Map<String, Object> root = new Yaml().load(in);
            Object cats = root.get("categories");
            if (!(cats instanceof Map<?, ?> map)) return words;
            for (Object cat : map.values()) {
                if (!(cat instanceof Map<?, ?> cm)) continue;
                Object ws = cm.get("words");
                if (ws instanceof List<?> list) for (Object w : list) if (w != null) words.add(String.valueOf(w));
            }
        } catch (Exception e) {
            log.error("load keywords failed", e);
        }
        return words;
    }

    private List<String> parseKeywords(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        String s = json.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        for (String part : s.split(",")) {
            String t = part.trim();
            if (t.startsWith("\"")) t = t.substring(1);
            if (t.endsWith("\"")) t = t.substring(0, t.length() - 1);
            if (!t.isBlank()) out.add(t);
        }
        return out;
    }
}
