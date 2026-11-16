package com.zhijian.risk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 企查查、天眼查使用的 Streamable HTTP MCP。测试连接只做 initialize 和 tools/list。
 */
public final class McpProbe {
    public static final String QCC_COMPANY = "https://agent.qcc.com/mcp/company/stream";
    public static final String TYC = "https://mcp.tianyancha.com/mcp";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private McpProbe() {}

    /** 返回 tools/list 的工具数量。失败时抛出带原因的 IllegalArgumentException。 */
    public static int connect(String url, String authorization) {
        HttpResult init = send(url, authorization, null, rpcBody(1, "initialize", initParams()));
        ensureOk(init, "初始化失败");
        send(url, authorization, init.session, notifyBody());
        HttpResult listed = send(url, authorization, init.session, rpcBody(2, "tools/list", MAPPER.createObjectNode()));
        JsonNode listedMsg = ensureOk(listed, "读取工具列表失败");
        JsonNode tools = listedMsg.path("result").path("tools");
        return tools.isArray() ? tools.size() : 0;
    }

    private static ObjectNode initParams() {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "zhijian-risk-web").put("version", "0.1.0");
        return params;
    }

    private static ObjectNode rpcBody(int id, String method, ObjectNode params) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.set("params", params);
        return body;
    }

    private static ObjectNode notifyBody() {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", "notifications/initialized");
        body.putObject("params");
        return body;
    }

    private record HttpResult(int status, String contentType, String body, String session) {}

    private static HttpResult send(String url, String authorization, String session, ObjectNode body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (authorization != null && !authorization.isBlank()) {
                builder.header("Authorization", authorization);
            }
            if (session != null && !session.isBlank()) {
                builder.header("mcp-session-id", session);
            }
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(),
                    response.headers().firstValue("content-type").orElse(""),
                    response.body(),
                    response.headers().firstValue("mcp-session-id").orElse(session));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("网络错误: " + e.getMessage());
        }
    }

    private static JsonNode ensureOk(HttpResult result, String fallback) {
        if (result.status >= 400) {
            throw new IllegalArgumentException("HTTP " + result.status + " " + clip(result.body));
        }
        JsonNode message = parse(result.body, result.contentType);
        if (message.has("error")) {
            String text = message.path("error").path("message").asText("");
            throw new IllegalArgumentException(text.isBlank() ? fallback : text);
        }
        return message;
    }

    private static JsonNode parse(String text, String contentType) {
        try {
            if (contentType.contains("text/event-stream")) return parseSse(text);
            return pick(MAPPER.readTree(text));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("响应无法解析");
        }
    }

    private static JsonNode parseSse(String text) throws Exception {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.startsWith("data:")) {
                if (!current.isEmpty()) current.append('\n');
                current.append(line.substring(5).trim());
            } else if (line.isBlank() && !current.isEmpty()) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) chunks.add(current.toString());
        IllegalArgumentException last = null;
        for (String chunk : chunks) {
            try {
                return pick(MAPPER.readTree(chunk));
            } catch (IllegalArgumentException e) {
                last = e;
            }
        }
        if (last != null) throw last;
        throw new IllegalArgumentException("响应里没有结果");
    }

    private static JsonNode pick(JsonNode node) {
        if (node.has("result") || node.has("error")) return node;
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.has("result") || item.has("error")) return item;
            }
        }
        throw new IllegalArgumentException("响应里没有结果");
    }

    private static String clip(String text) {
        if (text == null) return "";
        String trimmed = text.replace('\n', ' ').trim();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160) + "…";
    }
}
