package com.zhijian.risk.client;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容客户端。先走 chat/completions；该地址对这个模型返回 404 时，再走 /responses。
 * 果冻这类网关把 gpt 模型只挂在 Responses 上。
 */
@Component
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    // 部分兼容网关会在 HTTP/2 握手时直接断开。桌面端用的是 HTTP/1.1。
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /**
     * 调用大模型 chat/completions。
     *
     * @param baseUrl  OpenAI 兼容接口根地址
     * @param apiKey   API Key（可空）
     * @param model    模型名
     * @param messages 对话消息
     * @return assistant 文本内容
     */
    public String chat(String baseUrl, String apiKey, String model, List<Map<String, String>> messages) {
        log.info("LLM chat start, model={}, msgs={}", model, messages == null ? 0 : messages.size());

        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String base = trim(baseUrl);
                HttpResponse<String> resp = post(base + "/chat/completions", chatBody(model, messages, null), apiKey, Duration.ofSeconds(120));
                if (resp.statusCode() == 404) {
                    log.info("LLM chat/completions 404, retry responses, model={}", model);
                    resp = post(base + "/responses", responsesBody(model, messages, null), apiKey, Duration.ofSeconds(120));
                }
                if (resp.statusCode() >= 400) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 调用失败: " + errorText(resp));
                }
                String answer = assistantText(mapper.readTree(resp.body()));
                if (answer.isBlank()) throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 未返回内容");
                log.info("LLM chat success, chars={}", answer.length());
                return answer;
            } catch (ApiException e) {
                log.warn("LLM chat business fail: {}", e.getMessage());
                throw e;
            } catch (Exception e) {
                last = e;
                if (attempt == 0 && retryable(e)) {
                    log.warn("LLM chat retry after {}", e.getMessage());
                    try { Thread.sleep(800); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                break;
            }
        }
        log.error("LLM chat exception", last);
        throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 调用异常: " + (last == null ? "" : last.getMessage()));
    }

    private static boolean retryable(Exception e) {
        String name = e.getClass().getName();
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return name.contains("SSL") || name.contains("Connect") || name.contains("IOException")
                || msg.contains("handshake") || msg.contains("reset") || msg.contains("terminated");
    }

    /**
     * 用一句极短对话确认地址、模型和密钥可用。超时 20 秒，不要求模型吐出长文。
     */
    public void ping(String baseUrl, String apiKey, String model) {
        try {
            List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", "ping"));
            String base = trim(baseUrl);
            HttpResponse<String> resp = post(base + "/chat/completions", chatBody(model, messages, 8), apiKey, Duration.ofSeconds(20));
            if (resp.statusCode() == 404) {
                resp = post(base + "/responses", responsesBody(model, messages, 16), apiKey, Duration.ofSeconds(20));
            }
            if (resp.statusCode() >= 400) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, errorText(resp));
            }
            JsonNode root = mapper.readTree(resp.body());
            if (root.has("error") && !root.path("error").isNull()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, errorText(resp));
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "网络错误: " + e.getMessage());
        }
    }

    private ObjectNode chatBody(String model, List<Map<String, String>> messages, Integer maxTokens) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        if (maxTokens != null) body.put("max_tokens", maxTokens);
        ArrayNode arr = body.putArray("messages");
        for (Map<String, String> message : messages) {
            ObjectNode node = arr.addObject();
            node.put("role", message.getOrDefault("role", "user"));
            node.put("content", message.getOrDefault("content", ""));
        }
        return body;
    }

    private ObjectNode responsesBody(String model, List<Map<String, String>> messages, Integer maxTokens) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        if (maxTokens != null) body.put("max_output_tokens", maxTokens);
        ArrayNode input = body.putArray("input");
        for (Map<String, String> message : messages) {
            ObjectNode node = input.addObject();
            node.put("role", message.getOrDefault("role", "user"));
            node.put("content", message.getOrDefault("content", ""));
        }
        return body;
    }

    private HttpResponse<String> post(String url, ObjectNode body, String apiKey, Duration timeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey.trim());
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String assistantText(JsonNode root) {
        String chat = root.path("choices").path(0).path("message").path("content").asText("");
        if (!chat.isBlank()) return chat;
        String outputText = root.path("output_text").asText("");
        if (!outputText.isBlank()) return outputText;
        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            for (JsonNode part : item.path("content")) {
                String piece = part.path("text").asText("");
                if (!piece.isBlank()) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(piece);
                }
            }
        }
        return text.toString();
    }

    private String errorText(HttpResponse<String> resp) {
        String detail = "";
        try {
            JsonNode root = mapper.readTree(resp.body());
            detail = root.path("error").path("message").asText("");
            if (detail.isBlank()) detail = root.path("message").asText("");
        } catch (Exception ignored) {
            detail = "";
        }
        detail = detail.replace('\n', ' ').trim();
        if (detail.length() > 180) detail = detail.substring(0, 180) + "…";
        return detail.isBlank() ? "HTTP " + resp.statusCode() : "HTTP " + resp.statusCode() + " " + detail;
    }

    private static String trim(String baseUrl) {
        return baseUrl.replaceAll("/$", "");
    }
}
