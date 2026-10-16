package com.zhijian.risk.engine;

import com.zhijian.risk.domain.Partner;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 风险吹哨 / 准入瞭望的检索词、时间窗和命中过滤，对齐桌面端 baidu_search + partners。
 */
public final class SearchCore {
    public static final int MAX_CALLS_PER_OPERATION = 2;
    public static final int MAX_CALLS_PER_BATCH = 250;
    public static final int QUERY_UNIT_LIMIT = 72;

    public static final String RISK_ACUTE = "最近有没有爆雷停摆跑路资金池资金链失联挤兑非法集资";
    public static final String RISK_REG = "有没有被处罚罚款约谈立案吊销停业投诉维权暴力催收";
    public static final String RISK_COMBINED = "最近有没有爆雷停摆跑路资金池失联处罚投诉立案暴力催收";

    private static final String[] SEARCH_RISK_TERMS = {
            "爆雷", "暴雷", "资金池", "停摆", "跑路", "挤兑", "瘫痪", "失联", "资金链", "非法集资",
            "处罚", "罚款", "约谈", "立案", "吊销", "停业", "投诉", "维权", "暴力催收", "违规催收",
            "失信", "解约", "泄露", "下架", "涉诈"
    };
    private static final String[] TITLE_RISK_EXTRA = {"催收", "征信", "逾期", "截留", "停服", "误伤"};
    private static final String[] PR_TITLE_MARKS = {
            "成长之路", "向阳而生", "新征程", "政企同频", "紧跟监管", "合规新征程", "扎根营口", "发展密码", "探寻其"
    };
    private static final String[] LEGAL_SUFFIXES = {
            "股份有限公司", "有限责任公司", "集团有限公司", "有限公司", "集团公司", "集团"
    };
    private static final String[][] TRADE_ABBREVS = {
            {"数字科技", "数科"}, {"融资担保", "融担"}, {"商业保理", "保理"},
            {"小额贷款", "小贷"}, {"消费金融", "消金"}, {"资产管理", "资管"}, {"信息技术", "信息"}
    };

    private SearchCore() {}

    public record SearchOpts(int maxResults, String recency, String pageTimeGte, String pageTimeLte,
                             String dropBefore, String dropAfter, boolean strictUndated) {}

    public record SearchJob(String query, SearchOpts opts, List<Partner> partners) {}

    public record WebHit(String title, String url, String summary, String body, String eventTime, String credibility) {}

    public static SearchOpts windowFor(String kind, LocalDate asOf, boolean dated) {
        if (dated) {
            if ("weekly".equalsIgnoreCase(kind)) {
                LocalDate start = asOf.minusDays(asOf.getDayOfWeek().getValue() - 1L);
                LocalDate end = start.plusDays(6);
                return calendar(start.toString(), end.toString(), 8);
            }
            String d = asOf.toString();
            return calendar(d, d, 10);
        }
        if ("weekly".equalsIgnoreCase(kind)) {
            return new SearchOpts(8, "week", null, null, null, null, false);
        }
        LocalDate today = LocalDate.now();
        return new SearchOpts(10, null, today.minusDays(1).toString(), today.toString(), null, null, false);
    }

    public static SearchOpts lastSemiyear(int max) {
        LocalDate today = LocalDate.now();
        String start = today.minusDays(183).toString();
        String end = today.toString();
        return new SearchOpts(max, null, start, end, start, end, false);
    }

    public static String windowHint(String kind, LocalDate asOf, boolean dated) {
        if (!dated) return "weekly".equalsIgnoreCase(kind) ? "最近7天" : "最近24小时";
        if ("weekly".equalsIgnoreCase(kind)) {
            LocalDate start = asOf.minusDays(asOf.getDayOfWeek().getValue() - 1L);
            return start + " ~ " + start.plusDays(6);
        }
        return asOf.toString();
    }

    public static List<SearchJob> plan(List<Partner> partners, int cursor, SearchOpts opts) {
        List<SearchJob> jobs = new ArrayList<>();
        if (partners == null || partners.isEmpty()) return jobs;
        if (partners.size() == 1) {
            Partner p = partners.get(0);
            String brand = bestBrand(p);
            jobs.add(new SearchJob(focused(brand, RISK_ACUTE), opts, List.of(p)));
            jobs.add(new SearchJob(focused(brand, RISK_REG), opts, List.of(p)));
            return jobs;
        }
        int n = partners.size();
        int start = Math.floorMod(cursor, n);
        int cap = Math.min(MAX_CALLS_PER_BATCH, n);
        SearchOpts capped = new SearchOpts(Math.min(opts.maxResults(), 8), opts.recency(),
                opts.pageTimeGte(), opts.pageTimeLte(), opts.dropBefore(), opts.dropAfter(), opts.strictUndated());
        for (int i = 0; i < cap; i++) {
            Partner p = partners.get((start + i) % n);
            jobs.add(new SearchJob(focused(bestBrand(p), RISK_COMBINED), capped, List.of(p)));
        }
        return jobs;
    }

    public static List<String> admissionQueries(String name) {
        String brand = searchBrandFromName(name);
        return List.of(
                focused(brand, RISK_COMBINED),
                focused(brand, "最近有没有工商变更诉讼被执行")
        );
    }

    public static String searchBrandFromName(String name) {
        Partner p = new Partner();
        p.setName(name == null ? "" : name.trim());
        p.setAlias("");
        return bestBrand(p);
    }

    public static boolean keepSearchHit(String title) {
        if (title == null) return false;
        for (String k : PR_TITLE_MARKS) if (title.contains(k)) return false;
        for (String k : SEARCH_RISK_TERMS) if (title.contains(k)) return true;
        for (String k : TITLE_RISK_EXTRA) if (title.contains(k)) return true;
        return false;
    }

    public static boolean textMentions(String text, List<String> terms) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String t : terms) {
            if (t == null) continue;
            String s = t.trim();
            if (s.length() >= 2 && lower.contains(s.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static List<String> watchTerms(Partner p) {
        return expand(p.getName(), p.getAlias(), List.of());
    }

    public static List<String> expand(String name, String alias, List<String> lexicon) {
        List<String> words = new ArrayList<>();
        if (lexicon != null) for (String w : lexicon) push(words, w);
        push(words, name);
        push(words, alias);
        String compact = stripLegal(stripBrackets(name == null ? "" : name));
        push(words, compact);
        addTradeAbbrevs(words);
        addMarkerBrands(words, compact);
        return words;
    }

    public static String clipQuery(String s) {
        if (s == null) return "";
        int units = 0;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int w = cp < 128 ? 1 : 2;
            if (units + w > QUERY_UNIT_LIMIT) break;
            units += w;
            out.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    public static String credibility(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.contains(".gov.cn") || u.contains("court.gov") || u.contains("pbc.gov")
                || u.contains("cbirc") || u.contains("nfra.gov") || u.contains("csrc.gov")) return "A";
        if (u.contains("reuters") || u.contains("bloomberg") || u.contains("caixin") || u.contains("财新")
                || u.contains("xinhua") || u.contains("people.com.cn") || u.contains("thepaper")
                || u.contains("36kr") || u.contains("cls.cn") || u.contains("yicai") || u.contains("stcn.com")) return "B";
        return "C";
    }

    /** 本地日期门：百度 date 优先，其次 URL 稿号，再看正文。 */
    public static LocalDate classifyDate(String published, String url, String title, String body,
                                         String dropBefore, String dropAfter, boolean strictUndated) {
        LocalDate start = parseDay(dropBefore);
        LocalDate end = parseDay(dropAfter);
        LocalDate pub = parseDay(published);
        List<LocalDate> urlDates = extractUrlDates(url);
        List<LocalDate> bodyDates = extractFullDates((title == null ? "" : title) + " " + (body == null ? "" : body));
        if (start == null) {
            if (pub != null) return pub;
            if (!urlDates.isEmpty()) return urlDates.get(0);
            if (!bodyDates.isEmpty()) return bodyDates.get(0);
            return null;
        }
        if (end == null) end = start;
        if (pub != null) return inWindow(pub, start, end) ? pub : LocalDate.MIN;
        if (!urlDates.isEmpty()) {
            for (LocalDate d : urlDates) if (inWindow(d, start, end)) return d;
            return LocalDate.MIN;
        }
        if (!bodyDates.isEmpty()) {
            for (LocalDate d : bodyDates) if (inWindow(d, start, end)) return d;
            return LocalDate.MIN;
        }
        if (strictUndated && !hasAcute(title, body)) return LocalDate.MIN;
        return null;
    }

    public static boolean outOfRange(LocalDate classified) {
        return classified != null && classified.equals(LocalDate.MIN);
    }

    private static SearchOpts calendar(String start, String end, int max) {
        return new SearchOpts(max, null, start, end, start, end, true);
    }

    private static boolean inWindow(LocalDate d, LocalDate start, LocalDate end) {
        return !d.isBefore(start) && !d.isAfter(end);
    }

    private static boolean hasAcute(String title, String body) {
        String blob = (title == null ? "" : title) + (body == null ? "" : body);
        for (String k : new String[]{"爆雷", "暴雷", "资金池", "停摆", "跑路", "挤兑"}) {
            if (blob.contains(k)) return true;
        }
        return false;
    }

    private static String focused(String brand, String risk) {
        return clipQuery("\"" + brand + "\" " + risk);
    }

    private static String bestBrand(Partner p) {
        List<String> shorts = new ArrayList<>();
        for (String t : searchTerms(p, true)) if (isShort(t)) shorts.add(t);
        shorts.sort(Comparator.comparingInt(String::length));
        for (String s : shorts) if (s.contains("数科") && s.length() >= 4) return s;
        for (String s : shorts) if (s.length() >= 4) return s;
        String alias = p.getAlias() == null ? "" : p.getAlias().trim();
        if (!alias.isEmpty()) return alias;
        String name = p.getName() == null ? "" : p.getName().trim();
        return name.isEmpty() ? alias : name;
    }

    private static List<String> searchTerms(Partner p, boolean single) {
        List<String> expanded = expand(p.getName(), p.getAlias(), List.of());
        String full = p.getName() == null ? "" : p.getName().trim();
        String alias = p.getAlias() == null ? "" : p.getAlias().trim();
        if (single) {
            List<String> terms = new ArrayList<>();
            if (!full.isEmpty()) terms.add(full);
            if (!alias.isEmpty() && !alias.equals(full)) terms.add(alias);
            List<String> shorts = new ArrayList<>();
            for (String t : expanded) if (isShort(t) && !t.equals(full) && !t.equals(alias)) shorts.add(t);
            shorts.sort(Comparator.comparingInt(String::length));
            for (String s : shorts) {
                if (terms.size() >= 5) break;
                if (!terms.contains(s)) terms.add(s);
            }
            return terms;
        }
        if (isShort(alias)) return List.of(alias);
        String best = null;
        for (String t : expanded) {
            if (!isShort(t)) continue;
            if (best == null || t.length() < best.length()) best = t;
        }
        if (best != null) return List.of(best);
        return full.isEmpty() ? List.of() : List.of(full);
    }

    private static boolean isShort(String s) {
        if (s == null) return false;
        int n = s.codePointCount(0, s.length());
        return n >= 3 && n <= 16 && !looksLikeUscc(s);
    }

    private static boolean looksLikeUscc(String s) {
        int n = s.codePointCount(0, s.length());
        if (n < 16) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
        }
        return true;
    }

    private static void addTradeAbbrevs(List<String> words) {
        List<String> snapshot = new ArrayList<>(words);
        boolean hasDigital = snapshot.stream().anyMatch(x -> x.contains("数字科技"));
        for (String w : snapshot) {
            for (String[] pair : TRADE_ABBREVS) {
                if (w.contains(pair[0])) push(words, w.replace(pair[0], pair[1]));
            }
            if (hasDigital && w.endsWith("科技") && !w.contains("数科") && w.codePointCount(0, w.length()) <= 8) {
                push(words, w.replaceFirst("科技", "数科"));
            }
        }
    }

    private static void addMarkerBrands(List<String> words, String compact) {
        for (String[] pair : TRADE_ABBREVS) {
            int idx = compact.indexOf(pair[0]);
            if (idx < 0) continue;
            String prefix = compact.substring(0, idx).replaceAll("[区市省县州旗域园]", "");
            int[] cps = prefix.codePoints().toArray();
            for (int n : new int[]{2, 3}) {
                if (cps.length < n) continue;
                String brand = new String(cps, cps.length - n, n);
                int first = brand.codePointAt(0);
                if ("片口验贸的和与自".indexOf(first) >= 0) continue;
                push(words, brand + pair[0]);
                push(words, brand + pair[1]);
            }
        }
    }

    private static String stripBrackets(String s) {
        StringBuilder out = new StringBuilder();
        int cn = 0, en = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp == '（') cn++;
            else if (cp == '）') cn = Math.max(0, cn - 1);
            else if (cp == '(') en++;
            else if (cp == ')') en = Math.max(0, en - 1);
            else if (cn == 0 && en == 0) out.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private static String stripLegal(String s) {
        String t = s.trim();
        for (String suf : LEGAL_SUFFIXES) {
            if (t.endsWith(suf)) {
                t = t.substring(0, t.length() - suf.length()).trim();
                break;
            }
        }
        return t;
    }

    private static void push(List<String> words, String t) {
        if (t == null) return;
        t = t.trim();
        if (t.isEmpty() || words.contains(t)) return;
        words.add(t);
    }

    private static LocalDate parseDay(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String day = raw.trim().length() >= 10 ? raw.trim().substring(0, 10) : raw.trim();
        try { return LocalDate.parse(day); } catch (DateTimeParseException e) { return null; }
    }

    private static List<LocalDate> extractUrlDates(String url) {
        List<LocalDate> out = new ArrayList<>();
        if (url == null) return out;
        out.addAll(extractFullDates(url));
        char[] chars = url.toCharArray();
        for (int i = 0; i + 8 <= chars.length; i++) {
            if (i > 0 && Character.isDigit(chars[i - 1])) continue;
            boolean digits = true;
            for (int k = 0; k < 8; k++) if (!Character.isDigit(chars[i + k])) { digits = false; break; }
            if (!digits) continue;
            int y = Integer.parseInt(url.substring(i, i + 4));
            int m = Integer.parseInt(url.substring(i + 4, i + 6));
            int d = Integer.parseInt(url.substring(i + 6, i + 8));
            if (y >= 2020 && y <= 2035) {
                try { out.add(LocalDate.of(y, m, d)); i += 7; } catch (Exception ignored) {}
            }
        }
        return out;
    }

    private static List<LocalDate> extractFullDates(String text) {
        List<LocalDate> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        char[] c = text.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (i + 10 <= c.length) {
                String s = text.substring(i, i + 10);
                LocalDate d = null;
                try { d = LocalDate.parse(s); } catch (Exception ignored) {}
                if (d == null && s.charAt(4) == '/' && s.charAt(7) == '/') {
                    try { d = LocalDate.parse(s.replace('/', '-')); } catch (Exception ignored) {}
                }
                if (d != null) { out.add(d); i += 9; continue; }
            }
            LocalDate cn = tryCn(c, i);
            if (cn != null) out.add(cn);
        }
        return out;
    }

    private static LocalDate tryCn(char[] c, int i) {
        if (i + 8 > c.length) return null;
        if (!(Character.isDigit(c[i]) && Character.isDigit(c[i + 1]) && Character.isDigit(c[i + 2]) && Character.isDigit(c[i + 3]) && c[i + 4] == '年'))
            return null;
        int y = Integer.parseInt(new String(c, i, 4));
        int j = i + 5;
        int[] m = readUint(c, j);
        if (m == null || m[1] >= c.length || c[m[1]] != '月') return null;
        int[] d = readUint(c, m[1] + 1);
        if (d == null || d[1] >= c.length || c[d[1]] != '日') return null;
        try { return LocalDate.of(y, m[0], d[0]); } catch (Exception e) { return null; }
    }

    private static int[] readUint(char[] c, int i) {
        int j = i;
        while (j < c.length && Character.isDigit(c[j])) j++;
        if (j == i) return null;
        return new int[]{Integer.parseInt(new String(c, i, j - i)), j};
    }
}
