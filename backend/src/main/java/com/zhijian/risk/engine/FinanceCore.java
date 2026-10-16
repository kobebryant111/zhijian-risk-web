package com.zhijian.risk.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营守护：财报指标抽取后的四维打分、综合评级、同业与时序章节。
 * 结论由指标规则给出，大模型只负责把附件文本抽成数字。
 */
public final class FinanceCore {
    public static final String EXTRACT_SYSTEM = """
            你是消费金融合作机构财务分析助手。从审计报告/年报/季报/附注文本中抽取机构信息与关键财务指标，只输出一个 JSON 对象（不要 markdown）。

            字段：
            {
              "partnerName":"企业全称",
              "period":"如 2025全年",
              "partnerType":"loan|guarantee|traffic|payment|data|collection|interbank|ops",
              "revenue":数字或null,
              "revenueYoy":同比%,
              "netProfit":,
              "netProfitYoy":,
              "netMargin":%,
              "grossMargin":,
              "opMargin":,
              "roa":,
              "roe":,
              "assetLiabilityRatio":%,
              "currentRatio":,
              "quickRatio":,
              "equityRatio":,
              "debtEbitda":,
              "assetTurnover":,
              "loanOriginated":,
              "loanBalance":,
              "npl90":,
              "ocfo":经营现金流净额,
              "fcf":,
              "capexRatio":%,
              "dividendPayout":%,
              "guarantee":{
                "leverage":担保放大倍数或null,
                "compensationRate":代偿率%或null,
                "reserveAdequate":准备金是否充足bool或null,
                "singleConcentration":%,
                "assetRatio":%,
                "fraudSignal":bool
              }|null,
              "currencyUnit":"亿元人民币",
              "notes":"审计意见/关键风险一句话"
            }

            规则：
            1. 金额统一换算为「亿元人民币」（原文为元则 ÷1e8；万元 ÷1e4）。无法换算则填 null 并在 notes 说明。
            2. 融资担保公司 partnerType 必须为 guarantee；担保费收入可写入 revenue。
            3. 优先取「本期/期末」相对「上期/期初」计算同比%；利润为负也要如实填写。
            4. 禁止编造原文没有的数字。""";

    private FinanceCore() {}

    public record Dimension(String dimension, String grade, int score, String comment) {}

    public record Scored(Map<String, Object> metrics, List<Dimension> dimensions, List<String> concerns,
                         String overall, int riskScore, String summary) {}

    public static Map<String, Object> parseExtract(String llmText) throws Exception {
        String json = extractObject(llmText);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        Map<String, Object> m = mapper.convertValue(node, Map.class);
        if (m == null) throw new IllegalStateException("财务指标 JSON 为空");
        return m;
    }

    public static Scored score(Map<String, Object> raw, String partnerType, String name, String period) {
        Map<String, Object> m = raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
        List<String> concerns = new ArrayList<>();
        int profit = 85;
        String profitComment = "盈利能力整体可接受";
        Double yoy = num(m, "revenueYoy");
        if (yoy != null && yoy < -20) {
            profit -= 20;
            concerns.add(String.format("营收同比下滑 %.1f%%（>20%% 关注信号）", yoy));
        }
        Double nm = num(m, "netMargin");
        if (nm != null) {
            if (nm >= 10) { profit += 5; profitComment = String.format("净利率 %.2f%% 表现优良", nm); }
            else if (nm < 3) { profit -= 15; concerns.add(String.format("净利率偏低（%.2f%%）", nm)); }
        }
        Double npy = num(m, "netProfitYoy");
        if (npy != null) {
            if (npy > 30) profit += 5;
            else if (npy < -30) { profit -= 10; concerns.add(String.format("净利润同比大幅下滑 %.1f%%", npy)); }
        }

        int solvency = 85;
        String solvencyComment = "偿债指标在可控区间";
        Double al = num(m, "assetLiabilityRatio");
        if (al != null) {
            if (al > 70) { solvency -= 25; concerns.add(String.format("资产负债率 %.1f%% 超过 70%% 警戒线", al)); solvencyComment = "负债率偏高，需关注流动性与融资结构"; }
            else if (al < 55) { solvency += 5; solvencyComment = String.format("资产负债率 %.1f%% 良性", al); }
        }
        Double cr = num(m, "currentRatio");
        if (cr != null && cr < 1) { solvency -= 20; concerns.add(String.format("流动比率 %.2f < 1.0 预警", cr)); }
        Double qr = num(m, "quickRatio");
        if (qr != null && qr < 0.8) { solvency -= 15; concerns.add(String.format("速动比率 %.2f < 0.8 预警", qr)); }
        Double er = num(m, "equityRatio");
        if (er != null && er > 2) { solvency -= 15; concerns.add(String.format("产权比率 %.2f > 2.0 预警", er)); }
        Double de = num(m, "debtEbitda");
        if (de != null && de > 5) { solvency -= 15; concerns.add(String.format("负债/EBITDA %.2f > 5 预警", de)); }

        int ops = 78;
        String opsComment = "运营效率稳定";
        Double at = num(m, "assetTurnover");
        if (at != null) {
            if (at >= 0.8) { ops += 6; opsComment = String.format("资产周转率 %.2f，周转效率较好", at); }
            else if (at < 0.3) { ops -= 8; concerns.add(String.format("资产周转率 %.2f 偏低，关注运营效率", at)); opsComment = String.format("资产周转率 %.2f 偏弱", at); }
            else opsComment = String.format("资产周转率 %.2f", at);
        }
        String unit = str(m, "currencyUnit", "亿元人民币");
        Double lb = num(m, "loanBalance");
        Double lo = num(m, "loanOriginated");
        if (lb != null && lo != null && lo > 0) opsComment = opsComment + String.format("；贷款发起 %.0f、在贷 %.0f（%s）", lo, lb, unit);
        Double npl = num(m, "npl90");
        boolean loan = "loan".equals(partnerType) || (partnerType != null && partnerType.contains("助贷"));
        if (npl == null && loan) { concerns.add("90天+不良率未披露（数据缺口）"); ops -= 5; }
        else if (npl != null && npl > 5) { ops -= 15; concerns.add(String.format("90天+不良率 %.2f%% 偏高", npl)); }

        int cash = 80;
        String cashComment = "现金流需结合明细核实";
        Double fcf = num(m, "fcf");
        if (fcf != null) {
            if (fcf > 0) {
                cash += 8;
                cashComment = String.format("自由现金流为正（约 %.2f）", fcf);
                Double np = num(m, "netProfit");
                if (np != null && np > 0) {
                    double cover = fcf / np;
                    if (cover >= 1.5) { cash += 5; cashComment = String.format("FCF/净利润覆盖约 %.1f 倍，现金生成能力强", cover); }
                }
            } else { cash -= 20; concerns.add("自由现金流为负或偏弱"); }
        }
        Double ocfo = num(m, "ocfo");
        if (ocfo != null && ocfo < 0) { cash -= 15; concerns.add("经营活动现金流为负"); }

        if ("guarantee".equals(partnerType) || (partnerType != null && partnerType.contains("担保"))) {
            Object g = m.get("guarantee");
            if (g instanceof Map<?, ?> gm) {
                Double lev = num(gm, "leverage");
                if (lev != null) {
                    if (lev > 10) concerns.add(String.format("融担放大倍数 %.1f 超红线（>10）→ 映射致命风险", lev));
                    else if (lev > 8) concerns.add(String.format("融担放大倍数 %.1f > 8，橙色预警", lev));
                }
                Double comp = num(gm, "compensationRate");
                if (comp != null) {
                    if (comp > 5) concerns.add(String.format("代偿率 %.1f%% > 5%% 预警", comp));
                    else if (comp > 3) concerns.add(String.format("代偿率 %.1f%% > 3%% 关注", comp));
                }
                Object reserve = gm.get("reserveAdequate");
                if (Boolean.FALSE.equals(reserve)) concerns.add("准备金计提不足");
                Double conc = num(gm, "singleConcentration");
                if (conc != null) {
                    if (conc > 10) concerns.add(String.format("单一客户集中度 %.1f%% 超标", conc));
                    else if (conc > 8) concerns.add(String.format("单一客户集中度 %.1f%% 接近红线", conc));
                }
                Double ar = num(gm, "assetRatio");
                if (ar != null) {
                    if (ar < 60) concerns.add(String.format("资产比例 %.1f%% < 60%% 不合规", ar));
                    else if (ar < 65) concerns.add(String.format("资产比例 %.1f%% < 65%% 关注", ar));
                }
                if (Boolean.TRUE.equals(gm.get("fraudSignal"))) concerns.add("存在财务造假/审计异常信号");
            } else {
                concerns.add("融担专项：缺独立审计/台账，专项评级「待核实」");
            }
        }

        List<Dimension> dims = List.of(
                new Dimension("盈利能力", letter(profit), clamp(profit), profitComment),
                new Dimension("偿债能力", letter(solvency), clamp(solvency), solvencyComment),
                new Dimension("运营能力", letter(ops), clamp(ops), opsComment),
                new Dimension("现金流能力", letter(cash), clamp(cash), cashComment)
        );
        boolean red = concerns.stream().anyMatch(c -> c.contains("超红线") || c.contains("不合规") || c.contains("造假") || c.contains("致命"));
        double avg = dims.stream().mapToInt(Dimension::score).average().orElse(70);
        int risk = (int) Math.round(avg);
        if (red) risk = Math.min(risk, 55);
        risk = Math.max(0, Math.min(100, risk));
        String overall = red ? "关注/预警" : overallOf(avg);
        String summary = concerns.isEmpty()
                ? String.format("%s（%s）综合评级 %s，量化经营分 %d/100，关键财务指标未见明显警戒突破。", name, period, overall, risk)
                : String.format("%s（%s）综合评级 %s，量化经营分 %d/100。关注点：%s", name, period, overall, risk,
                String.join("；", concerns.subList(0, Math.min(4, concerns.size()))));
        m.put("riskScore", risk);
        return new Scored(m, dims, concerns, overall, risk, summary);
    }

    public static String markdown(String name, String ptype, String period, Scored scored,
                                  List<String> related, List<String> peerLines, List<String> seriesLines, String sourceKind) {
        Map<String, Object> m = scored.metrics();
        String unit = str(m, "currencyUnit", "亿元人民币");
        StringBuilder md = new StringBuilder();
        md.append("# 季度经营评估报告 · ").append(name).append("\n\n");
        md.append("- 机构类型：").append(RuleCatalog.partnerTypeLabel(ptype)).append("\n");
        md.append("- 报告期间：").append(period).append("\n");
        md.append("- 综合评级：**").append(scored.overall()).append("**\n");
        md.append("- 量化经营分：**").append(scored.riskScore()).append("/100**\n");
        md.append("- 数据来源：").append(sourceKind).append("\n");
        md.append("- 币种单位：").append(unit).append("\n\n");
        md.append("## 1. 摘要\n\n").append(scored.summary()).append("\n\n");
        md.append("## 2. 总体财务快照表\n\n| 指标 | 数值 |\n|------|------|\n");
        row(md, "总营收", num(m, "revenue"), unit);
        rowPct(md, "营收同比", num(m, "revenueYoy"));
        row(md, "归母净利润", num(m, "netProfit"), unit);
        rowPct(md, "净利润同比", num(m, "netProfitYoy"));
        rowPct(md, "净利率", num(m, "netMargin"));
        rowPct(md, "毛利率", num(m, "grossMargin"));
        rowPct(md, "ROA", num(m, "roa"));
        rowPct(md, "ROE", num(m, "roe"));
        rowPct(md, "资产负债率", num(m, "assetLiabilityRatio"));
        rowNum(md, "流动比率", num(m, "currentRatio"));
        rowNum(md, "速动比率", num(m, "quickRatio"));
        rowNum(md, "资产周转率", num(m, "assetTurnover"));
        row(md, "经营现金流", num(m, "ocfo"), unit);
        row(md, "自由现金流", num(m, "fcf"), unit);
        md.append('\n');
        md.append("## 3. 四维分析\n\n| 维度 | 评级 | 得分 | 判断 |\n|------|------|------|------|\n");
        for (Dimension d : scored.dimensions()) {
            md.append("| ").append(d.dimension()).append(" | ").append(d.grade()).append(" | ")
                    .append(d.score()).append("/100 | ").append(d.comment().replace('|', '/')).append(" |\n");
        }
        md.append('\n');
        Object g = m.get("guarantee");
        if (g instanceof Map<?, ?> gm && ("guarantee".equals(ptype) || (ptype != null && ptype.contains("担保")))) {
            md.append("## 4. 融担专项\n\n| 指标 | 数值 | 警戒参考 |\n|------|------|----------|\n");
            md.append("| 放大倍数 | ").append(fmt(num(gm, "leverage"), "")).append(" | ≤10（普通） |\n");
            md.append("| 代偿率 | ").append(fmt(num(gm, "compensationRate"), "%")).append(" | <5% |\n");
            Object reserve = gm.get("reserveAdequate");
            md.append("| 准备金足额 | ").append(reserve == null ? "—" : (Boolean.TRUE.equals(reserve) ? "是" : "否")).append(" | 应足额 |\n");
            md.append("| 单一客户集中度 | ").append(fmt(num(gm, "singleConcentration"), "%")).append(" | ≤10% |\n");
            md.append("| 资产比例 | ").append(fmt(num(gm, "assetRatio"), "%")).append(" | ≥60% |\n\n");
        }
        md.append("## 5. 同业横向对比\n\n");
        if (peerLines == null || peerLines.isEmpty()) md.append("本期同类型机构暂无其他评估记录，入库更多机构后可自动排名。\n\n");
        else {
            md.append("| 排名 | 机构 | 评级 | 经营分 |\n|------|------|------|--------|\n");
            md.append(String.join("\n", peerLines)).append("\n\n");
        }
        md.append("## 6. 自身纵向时序\n\n");
        if (seriesLines == null || seriesLines.size() < 2) md.append("历史期次不足，继续按期间入库后可生成趋势对比。\n\n");
        else {
            md.append("| 期间 | 经营分 | 评级 | 资产负债率 | 净利率 | FCF |\n|------|--------|------|------------|--------|-----|\n");
            md.append(String.join("\n", seriesLines)).append("\n\n");
        }
        md.append("## 7. 股东/关联方穿透\n\n");
        if (related == null || related.isEmpty()) md.append("- 暂无穿透提示\n\n");
        else for (String f : related) md.append("- ").append(f).append('\n');
        md.append("\n## 8. 关注点清单\n\n");
        if (scored.concerns().isEmpty()) md.append("- 暂无警戒项\n");
        else for (String c : scored.concerns()) md.append("- ").append(c).append('\n');
        String notes = str(m, "notes", "");
        if (!notes.isBlank()) md.append("\n## 附录 · 备注\n\n").append(notes).append('\n');
        md.append("\n---\n*智鉴风控官 · 经营守护自动生成；红线项须人工复核。*\n");
        return md.toString();
    }

    public static String extractObject(String text) {
        if (text == null) throw new IllegalStateException("财务指标 JSON 解析失败");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalStateException("模型未返回财务指标 JSON");
        return text.substring(start, end + 1);
    }

    private static String overallOf(double avg) {
        if (avg >= 88) return "A";
        if (avg >= 82) return "A-";
        if (avg >= 75) return "B+";
        if (avg >= 68) return "B";
        return "C";
    }

    private static String letter(int score) {
        if (score >= 88) return "A";
        if (score >= 80) return "A-";
        if (score >= 72) return "B+";
        if (score >= 65) return "B";
        return "C";
    }

    private static int clamp(int n) { return Math.max(0, Math.min(100, n)); }

    public static Double num(Map<?, ?> m, String key) {
        if (m == null) return null;
        Object v = m.get(key);
        if (v == null || "null".equals(String.valueOf(v))) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v).replace("%", "").trim()); }
        catch (Exception e) { return null; }
    }

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? def : s;
    }

    private static void row(StringBuilder md, String k, Double v, String unit) {
        md.append("| ").append(k).append(" | ").append(v == null ? "—" : String.format("%.2f %s", v, unit)).append(" |\n");
    }
    private static void rowPct(StringBuilder md, String k, Double v) {
        md.append("| ").append(k).append(" | ").append(v == null ? "—" : String.format("%.2f%%", v)).append(" |\n");
    }
    private static void rowNum(StringBuilder md, String k, Double v) {
        md.append("| ").append(k).append(" | ").append(v == null ? "—" : String.format("%.2f", v)).append(" |\n");
    }
    private static String fmt(Double v, String suffix) { return v == null ? "—" : String.format("%.2f%s", v, suffix); }
}
