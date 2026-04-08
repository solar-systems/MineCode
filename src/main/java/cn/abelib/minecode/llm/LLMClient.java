package cn.abelib.minecode.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * LLM 客户端 - OpenAI 兼容 API 的最简实现
 *
 * <p>支持所有 OpenAI 兼容的 API：
 * <ul>
 *   <li>OpenAI (GPT-4, GPT-4o, etc.)</li>
 *   <li>DeepSeek</li>
 *   <li>阿里云 Qwen</li>
 *   <li>Moonshot Kimi</li>
 *   <li>Ollama（OpenAI 兼容模式）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * LLMClient client = LLMClient.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .model("gpt-4o")
 *     .build();
 *
 * // 或使用 DeepSeek
 * LLMClient client = LLMClient.builder()
 *     .apiKey(System.getenv("DEEPSEEK_API_KEY"))
 *     .baseUrl("https://api.deepseek.com/v1")
 *     .model("deepseek-chat")
 *     .build();
 * }</pre>
 *
 * @author Abel
 */
public class LLMClient {
    private static final Logger log = LoggerFactory.getLogger(LLMClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    // Token 统计
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);

    private LLMClient(Builder builder) {
        this.model = builder.model;
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        this.apiKey = builder.apiKey;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        log.info("LLMClient initialized: model={}, baseUrl={}", model, baseUrl);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 调用 LLM API
     *
     * @param messages 消息列表
     * @param tools    工具 Schema 列表
     * @param onToken  流式输出回调
     * @return LLM 响应
     */
    public LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools,
                            Consumer<String> onToken) throws IOException {
        ObjectNode requestBody = buildRequestBody(messages, tools);
        String url = baseUrl + "/chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofMinutes(5))
                .build();

        return executeStreaming(request, onToken);
    }

    /**
     * 构建请求体
     */
    private ObjectNode buildRequestBody(List<ObjectNode> messages, List<ObjectNode> tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);

        ArrayNode messagesArray = body.putArray("messages");
        messages.forEach(messagesArray::add);

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            tools.forEach(toolsArray::add);
        }

        // 流式选项 - 包含 token 使用统计
        ObjectNode streamOptions = body.putObject("stream_options");
        streamOptions.put("include_usage", true);

        return body;
    }

    /**
     * 执行流式请求
     */
    private LLMResponse executeStreaming(HttpRequest request, Consumer<String> onToken) throws IOException {
        CompletableFuture<LLMResponse> future = new CompletableFuture<>();
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        Map<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();

        long[] promptTokens = {0};
        long[] completionTokens = {0};

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        future.completeExceptionally(new IOException("HTTP " + response.statusCode()));
                        return;
                    }

                    response.body().forEach(line -> {
                        if (line.isEmpty() || line.startsWith(":")) {
                            return;
                        }

                        String data = line.startsWith("data: ") ? line.substring(6) : line;

                        if ("[DONE]".equals(data)) {
                            for (ToolCallBuilder builder : toolCallBuilders.values()) {
                                toolCalls.add(builder.build());
                            }
                            future.complete(new LLMResponse(
                                    content.toString(),
                                    toolCalls,
                                    promptTokens[0],
                                    completionTokens[0]
                            ));
                            return;
                        }

                        try {
                            JsonNode chunk = mapper.readTree(data);

                            // 解析 usage
                            JsonNode usage = chunk.path("usage");
                            if (!usage.isMissingNode()) {
                                promptTokens[0] = usage.path("prompt_tokens").asLong(0);
                                completionTokens[0] = usage.path("completion_tokens").asLong(0);
                            }

                            JsonNode choices = chunk.path("choices");
                            if (choices.isArray() && choices.size() > 0) {
                                JsonNode delta = choices.get(0).path("delta");

                                // 处理文本内容
                                JsonNode contentNode = delta.path("content");
                                if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                                    String text = contentNode.asText();
                                    content.append(text);
                                    if (onToken != null) {
                                        onToken.accept(text);
                                    }
                                }

                                // 处理工具调用
                                JsonNode toolCallsNode = delta.path("tool_calls");
                                if (toolCallsNode.isArray()) {
                                    for (JsonNode tc : toolCallsNode) {
                                        int index = tc.path("index").asInt();
                                        toolCallBuilders.computeIfAbsent(index, k -> new ToolCallBuilder());

                                        ToolCallBuilder builder = toolCallBuilders.get(index);

                                        String tcId = tc.path("id").asText(null);
                                        if (tcId != null) {
                                            builder.id = tcId;
                                        }

                                        JsonNode function = tc.path("function");
                                        if (!function.isMissingNode()) {
                                            String name = function.path("name").asText(null);
                                            if (name != null) {
                                                builder.name = name;
                                            }
                                            String args = function.path("arguments").asText(null);
                                            if (args != null) {
                                                builder.arguments.append(args);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error parsing SSE data: {}", data, e);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    future.completeExceptionally(new IOException("Failed to get LLM response", throwable));
                    return null;
                });

        try {
            LLMResponse response = future.get(5, TimeUnit.MINUTES);
            totalPromptTokens.addAndGet(response.promptTokens());
            totalCompletionTokens.addAndGet(response.completionTokens());
            return response;
        } catch (Exception e) {
            throw new IOException("Failed to get LLM response", e);
        }
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "https://api.openai.com/v1";
        }
        return url.replaceAll("/+$", "");
    }

    public String getModel() {
        return model;
    }

    public long getTotalPromptTokens() {
        return totalPromptTokens.get();
    }

    public long getTotalCompletionTokens() {
        return totalCompletionTokens.get();
    }

    /**
     * ToolCall 构建器（用于流式解析）
     */
    private static class ToolCallBuilder {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();

        ToolCall build() {
            JsonNode argsNode;
            try {
                argsNode = mapper.readTree(arguments.toString());
            } catch (Exception e) {
                argsNode = mapper.createObjectNode();
            }
            return new ToolCall(id, name, argsNode);
        }
    }

    // ==================== Builder ====================

    public static class Builder {
        private String apiKey;
        private String model = "gpt-4o";
        private String baseUrl = "https://api.openai.com/v1";

        /**
         * 设置 API Key（必需）
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * 设置模型名称
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * 设置 API 基础 URL
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * 从环境变量读取配置
         */
        public Builder fromEnv() {
            this.apiKey = getEnv("OPENAI_API_KEY", this.apiKey);
            this.model = getEnv("MINECODE_MODEL", this.model);
            this.baseUrl = getEnv("OPENAI_BASE_URL", this.baseUrl);
            return this;
        }

        private String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            return value != null ? value : defaultValue;
        }

        /**
         * 构建 LLMClient 实例
         */
        public LLMClient build() {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("API Key is required");
            }
            return new LLMClient(this);
        }
    }
}
