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
 * Ollama 本地模型提供商
 *
 * <p>支持 Ollama 运行的本地模型：
 * <ul>
 *   <li>Llama 3.x</li>
 *   <li>Mistral</li>
 *   <li>Qwen</li>
 *   <li>DeepSeek Coder</li>
 *   <li>Codellama</li>
 *   <li>其他 Ollama 支持的模型</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * LLMProvider ollama = new OllamaProvider(
 *     LLMConfig.builder()
 *         .baseUrl("http://localhost:11434")
 *         .model("llama3.2")
 *         .build()
 * );
 *
 * LLMResponse response = ollama.chat(messages, tools, onToken);
 * }</pre>
 *
 * @author Abel
 */
public class OllamaProvider implements LLMProvider {
    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final LLMConfig config;

    // Token 统计
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);

    public OllamaProvider(LLMConfig config) {
        this.config = config;
        this.model = config.getModel();
        this.baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String getName() {
        return "ollama";
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools,
                            Consumer<String> onToken) throws IOException {
        ObjectNode requestBody = buildRequestBody(messages, tools);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofMinutes(10))  // 本地模型可能较慢
                .build();

        return executeStreaming(request, onToken);
    }

    /**
     * 构建 Ollama API 请求体
     */
    private ObjectNode buildRequestBody(List<ObjectNode> messages, List<ObjectNode> tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);

        // 转换消息格式
        ArrayNode messagesArray = body.putArray("messages");
        for (ObjectNode msg : messages) {
            ObjectNode ollamaMsg = mapper.createObjectNode();
            String role = msg.path("role").asText("");
            JsonNode contentNode = msg.path("content");

            ollamaMsg.put("role", role);

            if (contentNode.isTextual()) {
                ollamaMsg.put("content", contentNode.asText());
            } else if (contentNode.isArray()) {
                // 提取文本内容
                StringBuilder textContent = new StringBuilder();
                for (JsonNode block : contentNode) {
                    if (block.path("type").asText("").equals("text")) {
                        textContent.append(block.path("text").asText());
                    }
                }
                ollamaMsg.put("content", textContent.toString());
            }

            messagesArray.add(ollamaMsg);
        }

        // 转换工具定义（Ollama 格式）
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (ObjectNode tool : tools) {
                ObjectNode ollamaTool = mapper.createObjectNode();
                ollamaTool.put("type", "function");

                ObjectNode function = mapper.createObjectNode();
                function.put("name", tool.path("function").path("name").asText());
                function.put("description", tool.path("function").path("description").asText());
                function.set("parameters", tool.path("function").path("parameters"));

                ollamaTool.set("function", function);
                toolsArray.add(ollamaTool);
            }
        }

        // Ollama 特定选项
        ObjectNode options = body.putObject("options");
        if (config.getTemperature() != null) {
            options.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            options.put("num_predict", config.getMaxTokens());
        }

        return body;
    }

    /**
     * 执行流式请求
     */
    private LLMResponse executeStreaming(HttpRequest request, Consumer<String> onToken) throws IOException {
        CompletableFuture<LLMResponse> future = new CompletableFuture<>();
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        long[] promptTokens = {0};
        long[] completionTokens = {0};

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        future.completeExceptionally(new IOException("HTTP " + response.statusCode()));
                        return;
                    }

                    response.body().forEach(line -> {
                        if (line.isEmpty()) {
                            return;
                        }

                        try {
                            JsonNode chunk = mapper.readTree(line);

                            // 处理消息内容
                            JsonNode message = chunk.path("message");
                            if (!message.isMissingNode()) {
                                String role = message.path("role").asText("");
                                String text = message.path("content").asText("");

                                if (!text.isEmpty()) {
                                    content.append(text);
                                    if (onToken != null) {
                                        onToken.accept(text);
                                    }
                                }

                                // 处理工具调用
                                JsonNode toolCallsNode = message.path("tool_calls");
                                if (toolCallsNode.isArray()) {
                                    for (JsonNode tc : toolCallsNode) {
                                        String id = tc.path("id").asText("call_" + System.currentTimeMillis());
                                        JsonNode func = tc.path("function");
                                        String name = func.path("name").asText();
                                        JsonNode args = func.path("arguments");

                                        if (args.isTextual()) {
                                            args = mapper.readTree(args.asText());
                                        }

                                        toolCalls.add(new ToolCall(id, name, args));
                                    }
                                }
                            }

                            // 处理 Token 统计
                            JsonNode promptEvalCount = chunk.path("prompt_eval_count");
                            JsonNode evalCount = chunk.path("eval_count");

                            if (!promptEvalCount.isMissingNode()) {
                                promptTokens[0] = promptEvalCount.asLong();
                            }
                            if (!evalCount.isMissingNode()) {
                                completionTokens[0] += evalCount.asLong();
                            }

                            // 检查是否完成
                            if (chunk.path("done").asBoolean(false)) {
                                future.complete(new LLMResponse(
                                        content.toString(),
                                        toolCalls,
                                        promptTokens[0],
                                        completionTokens[0]
                                ));
                            }

                        } catch (Exception e) {
                            log.error("Error parsing Ollama response: {}", line, e);
                        }
                    });

                    // 如果没有 done 标记，也完成
                    if (!future.isDone()) {
                        future.complete(new LLMResponse(
                                content.toString(),
                                toolCalls,
                                promptTokens[0],
                                completionTokens[0]
                        ));
                    }
                })
                .exceptionally(throwable -> {
                    future.completeExceptionally(new IOException("Failed to get Ollama response", throwable));
                    return null;
                });

        try {
            LLMResponse response = future.get(10, TimeUnit.MINUTES);  // 本地模型可能较慢
            totalPromptTokens.addAndGet(response.promptTokens());
            totalCompletionTokens.addAndGet(response.completionTokens());
            return response;
        } catch (Exception e) {
            throw new IOException("Failed to get Ollama response", e);
        }
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
     * 检查 Ollama 服务是否可用
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取可用模型列表
     */
    public List<String> listModels() throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tags"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());

            List<String> models = new ArrayList<>();
            JsonNode modelsNode = root.path("models");
            if (modelsNode.isArray()) {
                for (JsonNode modelNode : modelsNode) {
                    models.add(modelNode.path("name").asText());
                }
            }
            return models;
        } catch (Exception e) {
            throw new IOException("Failed to list Ollama models", e);
        }
    }
}
