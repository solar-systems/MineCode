package cn.abelib.minecode.llm.provider;

import cn.abelib.minecode.llm.LLMConfig;
import cn.abelib.minecode.llm.LLMProvider;
import cn.abelib.minecode.llm.LLMResponse;
import cn.abelib.minecode.llm.ToolCall;
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
 * OpenAI 兼容 API 提供商
 *
 * <p>支持所有 OpenAI 兼容的 API：
 * <ul>
 *   <li>OpenAI (GPT-4, GPT-4o, etc.)</li>
 *   <li>DeepSeek</li>
 *   <li>阿里云 Qwen</li>
 *   <li>Moonshot Kimi</li>
 *   <li>智谱 GLM</li>
 *   <li>Ollama（OpenAI 兼容模式）</li>
 *   <li>vLLM</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * LLMProvider provider = new OpenAIProvider(
 *     LLMConfig.builder()
 *         .apiKey(System.getenv("OPENAI_API_KEY"))
 *         .model("gpt-4o")
 *         .baseUrl("https://api.openai.com/v1")
 *         .build()
 * );
 *
 * // 或使用 DeepSeek
 * LLMProvider deepseek = new OpenAIProvider(
 *     LLMConfig.builder()
 *         .apiKey(System.getenv("DEEPSEEK_API_KEY"))
 *         .model("deepseek-chat")
 *         .baseUrl("https://api.deepseek.com/v1")
 *         .build()
 * );
 * }</pre>
 *
 * @author Abel
 */
public class OpenAIProvider implements LLMProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final LLMConfig config;

    // Token 统计（线程安全）
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);

    /**
     * 从配置创建提供商
     */
    public OpenAIProvider(LLMConfig config) {
        this.config = config;
        this.model = config.getModel();
        this.baseUrl = normalizeBaseUrl(config.getBaseUrl());
        this.apiKey = config.getApiKey();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools,
                            Consumer<String> onToken) throws IOException {
        ObjectNode requestBody = buildRequestBody(messages, tools);
        String url = baseUrl + "/chat/completions";
        log.debug("Requesting URL: {}", url);
        log.debug("Request body: {}", mapper.writeValueAsString(requestBody));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofMinutes(5))
                .build();

        return executeWithRetry(request, onToken, 3);
    }

    /**
     * 构建请求体
     */
    private ObjectNode buildRequestBody(List<ObjectNode> messages, List<ObjectNode> tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);

        if (config.getTemperature() != null) {
            body.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            body.put("max_tokens", config.getMaxTokens());
        }

        ArrayNode messagesArray = body.putArray("messages");
        messages.forEach(messagesArray::add);

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            tools.forEach(toolsArray::add);
        }

        // 流式选项
        ObjectNode streamOptions = body.putObject("stream_options");
        streamOptions.put("include_usage", true);

        return body;
    }

    /**
     * 执行请求（带重试）
     */
    private LLMResponse executeWithRetry(HttpRequest request, Consumer<String> onToken, int maxRetries)
            throws IOException {
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return executeStreaming(request, onToken);
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries - 1) {
                    long waitMs = (long) Math.pow(2, attempt) * 1000;
                    log.warn("Request failed, retrying in {}ms: {}", waitMs, e.getMessage());
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry", ie);
                    }
                }
            }
        }

        throw new IOException("Request failed after " + maxRetries + " retries", lastException);
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
        // 只移除末尾的斜杠，不自动添加 /v1
        // 让用户自己控制完整的 baseUrl
        return url.replaceAll("/+$", "");
    }

    @Override
    public long getTotalPromptTokens() {
        return totalPromptTokens.get();
    }

    @Override
    public long getTotalCompletionTokens() {
        return totalCompletionTokens.get();
    }

    @Override
    public void resetTokenStats() {
        totalPromptTokens.set(0);
        totalCompletionTokens.set(0);
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
}
