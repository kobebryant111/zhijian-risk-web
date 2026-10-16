package com.zhijian.risk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.engine.SearchCore;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 百度千帆 AI 搜索 HTTP 客户端。
 */
@Component
public class BaiduSearchClient {
    private static final Logger log = LoggerFactory.getLogger(BaiduSearchClient.class);

    /** 单条检索结果。 */
    public record Hit(String title, String url, String snippet) {}

    /** 千帆原始引用，含发布时间。 */
    public record RawRef(String title, String url, String content, String date) {}

    public record SearchResult(java.util.List<RawRef> hits, String error) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /**
     * 调用百度 AI 搜索。
     *
     * @param apiKey 千帆 API Key（为空则直接返回空列表）
     * @param query  检索词
     * @return 命中列表（最多 8 条）
     */
    public List<Hit> search(String apiKey, String query) {
        SearchResult r = searchDetailed(apiKey, query, new SearchCore.SearchOpts(8, null, null, null, null, null, false));
        List<Hit> out = new ArrayList<>();
        for (RawRef h : r.hits()) out.add(new Hit(h.title(), h.url(), h.content()));
        return out;
    }

    /**
     * 千帆检索：带 page_time / recency，并按本地日期门过滤。
     */
    public SearchResult searchDetailed(String apiKey, String query, SearchCore.SearchOpts opts) {
        String q = SearchCore.clipQuery(query == null ? "" : query.trim());
        log.info("Baidu search start, query={}", q);
        if (apiKey == null || apiKey.isBlank()) return new SearchResult(List.of(), "未配置百度千帆搜索 API Key");
        if (q.isEmpty()) return new SearchResult(List.of(), "搜索词不能为空");
        try {
            ObjectNode body = mapper.createObjectNode();
            ArrayNode messages = body.putArray("messages");
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", q);
            body.put("search_source", "baidu_search_v2");
            ArrayNode filter = body.putArray("resource_type_filter");
            ObjectNode web = filter.addObject();
            web.put("type", "web");
            web.put("top_k", Math.max(1, Math.min(20, opts.maxResults())));
            ObjectNode sort = body.putObject("sort");
            sort.put("priority", "auto");
            if (opts.pageTimeGte() != null && opts.pageTimeLte() != null) {
                ObjectNode range = body.putObject("search_filter").putObject("range").putObject("page_time");
                range.put("gte", opts.pageTimeGte());
                range.put("lte", opts.pageTimeLte());
            } else if (opts.recency() != null) {
                body.put("search_recency_filter", opts.recency());
            }
            String key = apiKey.trim();
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://qianfan.baidubce.com/v2/ai_search/web_search"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .header("X-Appbuilder-Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                String err = "百度搜索 HTTP " + resp.statusCode();
                log.warn(err);
                return new SearchResult(List.of(), err);
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode refs = root.path("references");
            if (!refs.isArray()) refs = root.path("data").path("references");
            List<RawRef> hits = new ArrayList<>();
            if (refs.isArray()) {
                for (JsonNode n : refs) {
                    String title = n.path("title").asText("");
                    String url = n.path("url").asText(n.path("link").asText(""));
                    String content = n.path("content").asText(n.path("snippet").asText(""));
                    String date = n.path("date").asText(n.path("published").asText(""));
                    java.time.LocalDate classified = SearchCore.classifyDate(date, url, title, content,
                            opts.dropBefore(), opts.dropAfter(), opts.strictUndated());
                    if (SearchCore.outOfRange(classified)) continue;
                    if (opts.strictUndated() && classified == null && !SearchCore.keepSearchHit(title)) continue;
                    hits.add(new RawRef(title, url, content, classified == null ? date : classified.toString()));
                }
            }
            log.info("Baidu search done, hits={}", hits.size());
            return new SearchResult(hits, null);
        } catch (Exception e) {
            log.warn("Baidu search failed: {}", e.getMessage());
            return new SearchResult(List.of(), "百度搜索网络错误: " + e.getMessage());
        }
    }
}
