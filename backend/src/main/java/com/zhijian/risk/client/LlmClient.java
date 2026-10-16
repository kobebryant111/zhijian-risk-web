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
 * OpenAI 兼容 Chat Completions HTTP 客户端。
 */
@Component
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

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

        try {
            String url = baseUrl.replaceAll("/$", "") + "/chat/completions";
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            ArrayNode arr = body.putArray("messages");
            for (Map<String, String> m : messages) {
                ObjectNode n = arr.addObject();
                n.put("role", m.getOrDefault("role", "user"));
                n.put("content", m.getOrDefault("content", ""));
            }
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (apiKey != null && !apiKey.isBlank()) b.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 调用失败: HTTP " + resp.statusCode() + " " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 未返回内容");
            }
            String answer = content.asText();
            log.info("LLM chat success, chars={}", answer.length());
            return answer;
        } catch (ApiException e) {
            log.warn("LLM chat business fail: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("LLM chat exception", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "LLM 调用异常: " + e.getMessage());
        }
    }
}
