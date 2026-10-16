package com.zhijian.risk.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 准入瞭望 30 项检查清单。结论由红/橙/黄规则给出，大模型只写说明。
 */
public final class AdmissionCore {
    public static final String NOTES_SYSTEM = """
            你是消费金融「合作机构准入」合规分析师。根据清单触发结果与证据，输出一份简明中文分析报告（可用小标题，不要用 markdown 代码块）。

            必须包含：
            1）主体速览（名称、类型、能从证据读到的工商/许可要点）
            2）财报要点（若有「财报附件」）：营收/利润/资产负债率/现金流等关键指标与异常；无附件则写「未上传财报」
            3）风险点统计：红/橙/黄各多少；并列出已触发项的具体含义
            4）待核验项：证据不足、无法确认的关键检查（尤其融担杠杆/代偿/准备金、完整司法处罚清单）
            5）结论建议：否决 / 有条件通过 / 待核验 / 通过 之一，并说明理由
            硬约束：
            - 只依据给定证据，禁止编造案号、处罚决定书、失信名单、财务数字；不确定就写「待核验」。
            - 「全网搜」只是舆情/开庭/投诉网页，不能代替工商登记。
            - 结论建议必须与清单统计一致，不得改写规则已经给出的结论。""";

    public record CheckDef(String id, String color, String title, String legalBasis, String[] types, String[] keywords) {}

    public record Evidence(String source, String text, String url, int level) {}

    public record Item(String id, String color, String title, String legalBasis, boolean triggered, String evidence, String sourceRef) {}

    public record Review(String conclusion, boolean evidenceIncomplete, List<Item> items, List<String> remediation,
                         String summary, String markdown, List<String> sources) {}

    public static final CheckDef[] CHECKS = {
            new CheckDef("R01", "red", "被列入严重违法失信名单", "《市场监督管理严重违法失信名单管理办法》", new String[]{}, new String[]{"严重违法失信", "违法失信名单"}),
            new CheckDef("R02", "red", "被列为失信被执行人", "最高法关于失信被执行人名单规定", new String[]{}, new String[]{"失信被执行人名单", "被列入失信被执行人", "被列为失信被执行人", "被限制高消费", "限制高消费令", "限制消费令"}),
            new CheckDef("R03", "red", "吊销许可证 / 责令停业整顿", "各行业监管法规", new String[]{}, new String[]{"吊销", "责令停业", "停业整顿", "撤销许可"}),
            new CheckDef("R04", "red", "融担放大倍数超红线（>10/15倍）", "融担监管条例第15条", new String[]{"guarantee"}, new String[]{"放大倍数", "超红线", "杠杆超限"}),
            new CheckDef("R05", "red", "为控股股东/实控人提供担保", "融担监管条例第17条", new String[]{"guarantee"}, new String[]{"为实控人担保", "控股股东担保", "关联担保违规"}),
            new CheckDef("R06", "red", "从事吸收存款/自营贷款等禁止业务", "融担监管条例第23条", new String[]{"guarantee"}, new String[]{"吸收存款", "自营贷款", "非法集资"}),
            new CheckDef("R07", "red", "实控人失联 / 主要股东股权冻结>30%", "通用风险点", new String[]{}, new String[]{"实控人失联", "股权冻结", "刑事拘留"}),
            new CheckDef("R08", "red", "暴力催收或侵犯公民个人信息刑事立案", "《个人信息保护法》、助贷新规第8条", new String[]{"collection", "loan"}, new String[]{"暴力催收", "侵犯公民个人信息", "爆通讯录", "刑事立案"}),
            new CheckDef("R09", "red", "综合融资成本超过24%", "金规〔2025〕9号第6条", new String[]{"loan"}, new String[]{"综合融资成本", "超过24%", "利率超24"}),
            new CheckDef("R10", "red", "未纳入银行名单制管理", "金规〔2025〕9号第4条", new String[]{"loan"}, new String[]{"名单制", "名单外", "未进入名单"}),
            new CheckDef("R11", "red", "支付牌照被注销/暂停", "央行支付业务许可相关规定", new String[]{"payment"}, new String[]{"支付牌照", "注销支付", "暂停支付业务"}),
            new CheckDef("R12", "red", "数据违规被刑事立案", "《个人信息保护法》等", new String[]{"data"}, new String[]{"数据违规", "侵犯公民个人信息", "非法提供数据"}),
            new CheckDef("O01", "orange", "未自主风控 / 核心风控外包", "助贷新规第5条", new String[]{"loan"}, new String[]{"风控外包", "未自主风控"}),
            new CheckDef("O02", "orange", "大额行政处罚（>100万）/ 监管公开通报", "负面惩罚清单", new String[]{}, new String[]{"行政处罚", "罚款", "公开通报", "处罚决定"}),
            new CheckDef("O03", "orange", "融担集中度超标 / 准备金不足 / 资产比例不合规 / 财务造假信号", "融担条例第16/18条、资产比例办法", new String[]{"guarantee"}, new String[]{"集中度", "准备金", "资产比例", "财务造假"}),
            new CheckDef("O04", "orange", "出现违规催收行为", "助贷新规第8条", new String[]{"loan", "collection"}, new String[]{"违规催收", "骚扰催收", "暴力催收投诉"}),
            new CheckDef("O05", "orange", "同业多家解约", "行业惯例", new String[]{}, new String[]{"取消合作", "终止合作", "解约"}),
            new CheckDef("O06", "orange", "数据来源合规性质疑 / 数据安全事件", "网信办相关规定", new String[]{"data", "traffic"}, new String[]{"数据泄露", "数据安全", "约谈", "下架"}),
            new CheckDef("O07", "orange", "经营资质存续异常", "各监管官网", new String[]{}, new String[]{"续期受阻", "资质异常", "许可证", "备案撤销"}),
            new CheckDef("O08", "orange", "外部信用评级下调", "评级机构公告", new String[]{}, new String[]{"评级下调", "信用评级"}),
            new CheckDef("O09", "orange", "违规催收举报集中爆发（>50件/月）", "消保法、助贷新规第8条", new String[]{"collection"}, new String[]{"批量举报", "投诉爆发", "催收举报"}),
            new CheckDef("O10", "orange", "备付金违规 / 反洗钱处罚", "央行相关规定", new String[]{"payment"}, new String[]{"备付金", "反洗钱", "涉赌涉诈"}),
            new CheckDef("Y01", "yellow", "不良率恶化 / 代偿率上升", "助贷/融担经营信号", new String[]{"loan", "guarantee"}, new String[]{"不良率", "代偿率", "资产质量"}),
            new CheckDef("Y02", "yellow", "营收连续下滑", "财务异常信号", new String[]{}, new String[]{"营收下滑", "同比下降", "亏损"}),
            new CheckDef("Y03", "yellow", "非标审计意见 / 管理层变更", "融担监管条例等", new String[]{}, new String[]{"非标意见", "保留意见", "管理层变更", "实控人变更"}),
            new CheckDef("Y04", "yellow", "大额诉讼（>净资产5%）", "通用风险点", new String[]{}, new String[]{"重大诉讼", "被起诉", "作为被告", "涉案金额"}),
            new CheckDef("Y05", "yellow", "监管评级 C/D 级", "分类监管办法", new String[]{"guarantee"}, new String[]{"监管评级", "C级", "D级"}),
            new CheckDef("Y06", "yellow", "催收投诉异常增长 / 消保约谈", "消保法", new String[]{"collection", "loan"}, new String[]{"黑猫投诉", "投诉量", "约谈"}),
            new CheckDef("Y07", "yellow", "经营异常名录", "《企业经营异常名录管理暂行办法》", new String[]{}, new String[]{"经营异常", "异常名录"}),
            new CheckDef("Y08", "yellow", "监管行政处罚（未达大额线）", "监管处罚公开信息；大额（>100万）或公开通报走 O02", new String[]{}, new String[]{"行政处罚", "罚款", "处罚决定", "罚单", "被处罚"})
    };

    private AdmissionCore() {}

    public static List<String> aliases(String name) {
        List<String> out = new ArrayList<>();
        String n = name == null ? "" : name.trim();
        String brand = SearchCore.searchBrandFromName(n);
        for (String s : new String[]{n, brand}) {
            if (s != null && s.codePointCount(0, s.length()) >= 4 && !out.contains(s)) out.add(s);
        }
        return out;
    }

    public static boolean webHitUsable(String title, String url, String text, List<String> aliases) {
        if (!mentions(text, aliases)) return false;
        if ((title != null && title.contains("开庭公告")) || (url != null && url.contains("openNoticeDetail"))) return true;
        return SearchCore.keepSearchHit(title);
    }

    public static Review review(String name, String partnerType, String scenario, List<Evidence> docs, List<String> sourceLabels) {
        List<String> aliases = aliases(name);
        List<Item> items = new ArrayList<>();
        for (CheckDef def : CHECKS) {
            if (!typeMatch(def.types(), partnerType)) continue;
            Hit hit = match(def, docs, aliases);
            items.add(new Item(def.id(), def.color(), def.title(), def.legalBasis(), hit.triggered, hit.evidence, hit.url));
        }
        boolean redHit = items.stream().anyMatch(i -> "red".equals(i.color()) && i.triggered());
        List<Item> orange = items.stream().filter(i -> "orange".equals(i.color()) && i.triggered()).toList();
        List<Item> yellow = items.stream().filter(i -> "yellow".equals(i.color()) && i.triggered()).toList();
        boolean incomplete = docs.isEmpty();
        boolean hasExternal = sourceLabels.stream().anyMatch(s ->
                "企查查".equals(s) || "天眼查".equals(s) || "全网搜".equals(s) || "财报附件".equals(s) || "上传材料".equals(s) || "本地线索".equals(s));
        boolean guaranteeThin = "guarantee".equals(partnerType) && docs.stream().noneMatch(d ->
                d.text().contains("放大倍数") || d.text().contains("代偿率") || d.text().contains("担保赔偿准备金") || d.text().contains("在保余额"));
        String conclusion;
        if (redHit) conclusion = "否决";
        else if (!orange.isEmpty()) conclusion = "有条件通过";
        else if (incomplete || !hasExternal || guaranteeThin) conclusion = "待核验";
        else conclusion = "通过";

        List<String> remediation = new ArrayList<>();
        for (Item i : orange) remediation.add("限期整改：" + i.title() + "（依据：" + i.legalBasis() + "）");
        for (Item i : yellow) remediation.add("持续监控：" + i.title());
        if (incomplete || !hasExternal) remediation.add("证据不足：请配置百度千帆搜索，或粘贴尽调材料后再签批");
        if (guaranteeThin) remediation.add("融担核心指标待核验：请补齐放大倍数、代偿率、准备金/在保余额等经营数据后再研判");
        if ("待核验".equals(conclusion)) remediation.add("结论为「待核验」：禁止按「通过」推进业务合作，须补齐证据后重跑");

        long redN = items.stream().filter(i -> "red".equals(i.color()) && i.triggered()).count();
        String summary = String.format("%s（%s）场景「%s」：一票否决 %d 项触发，重大违规 %d 项，关注 %d 项 → 结论【%s】%s",
                name, RuleCatalog.partnerTypeLabel(partnerType), scenario, redN, orange.size(), yellow.size(), conclusion,
                incomplete ? "（证据不完整）" : "");
        String markdown = render(name, partnerType, scenario, conclusion, incomplete, items, remediation, summary, sourceLabels);
        return new Review(conclusion, incomplete, items, remediation, summary, markdown, sourceLabels);
    }

    public static String notesUser(String name, String ptype, String scenario, Review review, List<Evidence> docs) {
        long red = review.items().stream().filter(i -> "red".equals(i.color()) && i.triggered()).count();
        long orange = review.items().stream().filter(i -> "orange".equals(i.color()) && i.triggered()).count();
        long yellow = review.items().stream().filter(i -> "yellow".equals(i.color()) && i.triggered()).count();
        StringBuilder triggered = new StringBuilder();
        for (Item i : review.items()) if (i.triggered()) triggered.append(i.id()).append('[').append(i.color()).append("] ")
                .append(i.title()).append(" | ").append(clip(i.evidence(), 100)).append('\n');
        StringBuilder pending = new StringBuilder();
        int n = 0;
        for (Item i : review.items()) {
            if (i.triggered()) continue;
            if ("red".equals(i.color()) || List.of("O02", "O03", "O07", "Y01", "Y03", "Y04", "Y05", "Y08").contains(i.id())) {
                pending.append(i.id()).append(' ').append(i.title()).append('\n');
                if (++n >= 8) break;
            }
        }
        StringBuilder evidence = new StringBuilder();
        int shown = 0;
        for (Evidence d : docs) {
            if (shown++ >= 14) break;
            int limit = d.source().contains("财报") || d.source().contains("上传") ? 3200 : 280;
            evidence.append("- [").append(d.source()).append("] ").append(clip(d.text(), limit)).append('\n');
        }
        return "机构：" + name + "\n类型：" + RuleCatalog.partnerTypeLabel(ptype) + "\n场景：" + scenario
                + "\n规则结论：" + review.conclusion()
                + "\n统计：红" + red + "/橙" + orange + "/黄" + yellow
                + "\n已触发：\n" + (triggered.isEmpty() ? "（无）\n" : triggered)
                + "关键未触发（待核实）：\n" + (pending.isEmpty() ? "（无）\n" : pending)
                + "证据摘要：\n" + evidence;
    }

    private static String render(String name, String ptype, String scenario, String conclusion, boolean incomplete,
                                 List<Item> items, List<String> remediation, String summary, List<String> sources) {
        StringBuilder md = new StringBuilder();
        md.append("# 准入评审 — ").append(name).append("\n\n");
        md.append("- 机构类型：").append(RuleCatalog.partnerTypeLabel(ptype)).append('\n');
        md.append("- 触发场景：").append(scenario).append('\n');
        md.append("- 综合结论：**").append(conclusion).append("**").append(incomplete ? "（证据不完整）" : "").append('\n');
        md.append("- 证据来源：").append(String.join("、", sources)).append("\n\n");
        md.append("## 摘要\n\n").append(summary).append("\n\n");
        md.append("## 检查清单\n\n| 编号 | 颜色 | 检查项 | 触发 | 依据 | 证据 |\n|---|---|---|---|---|---|\n");
        for (Item i : items) {
            md.append("| ").append(i.id()).append(" | ").append(colorLabel(i.color())).append(" | ")
                    .append(i.title().replace('|', '/')).append(" | ").append(i.triggered() ? "是" : "否")
                    .append(" | ").append(i.legalBasis().replace('|', '/')).append(" | ")
                    .append(clip(i.evidence(), 80).replace('|', '/').replace('\n', ' ')).append(" |\n");
        }
        md.append("\n## 整改与监控\n\n");
        if (remediation.isEmpty()) md.append("- 无\n");
        else for (String r : remediation) md.append("- ").append(r).append('\n');
        if (incomplete || "待核验".equals(conclusion)) {
            md.append("\n> **警示**：证据不完整或结论为待核验时，不得按「通过」推进业务合作。\n");
        }
        md.append("\n---\n*结论由规则清单给出，AI 说明不得改写结论。*\n");
        return md.toString();
    }

    private record Hit(boolean triggered, String evidence, String url) {}

    private static Hit match(CheckDef def, List<Evidence> docs, List<String> aliases) {
        for (Evidence d : docs) {
            if ("Y04".equals(def.id())) {
                if (isDefendant(d.text(), aliases)) {
                    return new Hit(true, "[" + d.source() + "] 本公司作为被告：" + clip(d.text(), 80), d.url());
                }
                if (isPlaintiff(d.text(), aliases) && !isDefendant(d.text(), aliases)) continue;
            }
            for (String kw : def.keywords()) {
                if (kw == null || kw.isEmpty()) continue;
                if (chrome(def, d)) continue;
                if ("Y08".equals(def.id()) && (penaltyGe100(d.text()) || d.text().contains("公开通报") || d.text().contains("监管通报"))) continue;
                boolean near = "red".equals(def.color()) || "O02".equals(def.id()) || "Y08".equals(def.id());
                if (near) {
                    if (!keywordNear(d.text(), kw, aliases, 120)) continue;
                } else if (!d.text().contains(kw)) continue;
                if ("O02".equals(def.id()) && !o02Counts(d.text(), kw)) continue;
                if (d.text().contains(kw)) {
                    if ("red".equals(def.color()) && d.level() > 2 && "本地线索".equals(d.source())) continue;
                    return new Hit(true, "[" + d.source() + "] 命中「" + kw + "」：" + clip(d.text(), 80), d.url());
                }
            }
        }
        return new Hit(false, "", "");
    }

    private static boolean typeMatch(String[] allowed, String partnerType) {
        if (allowed == null || allowed.length == 0) return true;
        for (String t : allowed) if (t.equals(partnerType)) return true;
        return false;
    }

    private static boolean mentions(String text, List<String> aliases) {
        if (text == null) return false;
        for (String a : aliases) if (a != null && text.contains(a)) return true;
        return false;
    }

    private static boolean chrome(CheckDef def, Evidence d) {
        boolean docket = d.text().contains("开庭公告") || (d.url() != null && d.url().contains("openNoticeDetail"));
        return docket && ("R02".equals(def.id()) || "O02".equals(def.id()) || "Y08".equals(def.id()));
    }

    private static boolean isDefendant(String text, List<String> aliases) {
        for (String a : aliases) {
            if (text.contains("被告:" + a) || text.contains("被告：" + a) || text.contains("被告 " + a)
                    || text.contains("被告" + a) || text.contains("与被告" + a) || text.contains(a + "作为被告")
                    || text.contains(a + "（被告）") || text.contains(a + "(被告)")) return true;
        }
        return false;
    }

    private static boolean isPlaintiff(String text, List<String> aliases) {
        for (String a : aliases) {
            if (text.contains("原告:" + a) || text.contains("原告：" + a) || text.contains("原告 " + a)
                    || text.contains("原告" + a) || text.contains(a + "作为原告")) return true;
        }
        return false;
    }

    private static boolean keywordNear(String text, String kw, List<String> aliases, int window) {
        int from = 0;
        while (from < text.length()) {
            int kp = text.indexOf(kw, from);
            if (kp < 0) return false;
            if (!negated(text, kp)) {
                for (String a : aliases) {
                    int ap = 0;
                    while (ap < text.length()) {
                        int hit = text.indexOf(a, ap);
                        if (hit < 0) break;
                        if (Math.abs(kp - hit) <= window) return true;
                        ap = hit + Math.max(1, a.length());
                    }
                }
            }
            from = kp + Math.max(1, kw.length());
        }
        return false;
    }

    private static boolean negated(String text, int idx) {
        int start = Math.max(0, idx - 12);
        String pre = text.substring(start, idx);
        for (String x : new String[]{"未", "没有", "并非", "未见", "不存在", "不是", "非"}) if (pre.contains(x)) return true;
        return false;
    }

    private static boolean penaltyGe100(String text) {
        char[] c = text.toCharArray();
        int i = 0;
        while (i < c.length) {
            int j = i;
            while (j < c.length && Character.isDigit(c[j])) j++;
            if (j > i) {
                int n;
                try { n = Integer.parseInt(new String(c, i, j - i)); } catch (Exception e) { n = 0; }
                if (n >= 100 && j < c.length && c[j] == '万') {
                    int start = Math.max(0, i - 16);
                    String ctx = new String(c, start, j - start);
                    if (ctx.indexOf('罚') >= 0) return true;
                }
                i = Math.max(j, i + 1);
                continue;
            }
            i++;
        }
        return false;
    }

    private static boolean o02Counts(String text, String kw) {
        if ("公开通报".equals(kw) || "监管通报".equals(kw)) return true;
        if ("行政处罚".equals(kw) || "罚款".equals(kw) || "处罚决定".equals(kw)) {
            return penaltyGe100(text) || text.contains("公开通报") || text.contains("监管通报");
        }
        return true;
    }

    private static String colorLabel(String color) {
        return switch (color) {
            case "red" -> "红";
            case "orange" -> "橙";
            default -> "黄";
        };
    }

    private static String clip(String s, int n) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').trim();
        return t.codePointCount(0, t.length()) <= n ? t : t.substring(0, t.offsetByCodePoints(0, n)) + "…";
    }
}
