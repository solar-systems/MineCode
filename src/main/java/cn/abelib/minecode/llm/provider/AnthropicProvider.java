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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Anthropic Claude API 提供商
 *
 * <p>支持 Anthropic Claude 系列模型：
 * <ul>
 *   <li>claude-3-opus</li>
 *   <li>claude-3-sonnet</li>
 *   <li>claude-3-haiku</li>
 *   <li>claude-3-5-sonnet</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * LLMProvider claude = new AnthropicProvider(
 *     LLMConfig.builder()
 *         .apiKey(System.getenv("ANTHROPIC_API_KEY"))
 *         .model("claude-3-5-sonnet-20241022")
 *         .build()
 * );
 *
 * LLMResponse response = claude.chat(messages, tools, onToken);
 * }</pre>
 *
 * @author Abel
 */
public class AnthropicProvider implements LLMProvider {
    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";
    private static final String API_VERSION = "2023-06-01";

    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final LLMConfig config;

    // Token 统计
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);

    public AnthropicProvider(LLMConfig config) {
        this.config = config;
        this.model = config.getModel();
        this.baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL;
        this.apiKey = config.getApiKey();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String getName() {
        return "anthropic";
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools,
                            Consumer<String> onToken) throws IOException {
        // 转换消息格式
        ObjectNode requestBody = buildRequestBody(messages, tools);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .timeout(Duration.ofMinutes(5))
                .build();

        return executeStreaming(request, onToken);
    }

    /**
     * 构建 Anthropic API 请求体
     *
     * <p>Anthropic 消息格式与 OpenAI 不同：
     * <ul>
     *   <li>系统消息单独放在 system 字段</li>
     *   <li>消息内容可以是字符串或内容块数组</li>
     *   <li>工具定义格式不同</li>
     * </ul>
     */
    private ObjectNode buildRequestBody(List<ObjectNode> messages, List<ObjectNode> tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);

        if (config.getMaxTokens() != null) {
            body.put("max_tokens", config.getMaxTokens());
        } else {
            body.put("max_tokens", 4096); // Anthropic 必需
        }

        // 提取系统消息
        StringBuilder systemPrompt = new StringBuilder();
        List<ObjectNode> convertedMessages = new ArrayList<>();

        for (ObjectNode msg : messages) {
            String role = msg.path("role").asText("");
            JsonNode contentNode = msg.path("content");

            if ("system".equals(role)) {
                // 系统消息单独处理
                if (contentNode.isTextual()) {
                    systemPrompt.append(contentNode.asText()).append("\n");
                } else if (contentNode.isArray()) {
                    for (JsonNode block : contentNode) {
                        if (block.path("type").asText("").equals("text")) {
                            systemPrompt.append(block.path("text").asText()).append("\n");
                        }
                    }
                }
            } else if ("user".equals(role) || "assistant".equals(role)) {
                // 转换消息格式
                ObjectNode convertedMsg = mapper.createObjectNode();
                convertedMsg.put("role", role);

                if (contentNode.isTextual()) {
                    // 简单文本内容
                    ArrayNode contentArray = convertedMsg.putArray("content");
                    ObjectNode textBlock = contentArray.addObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", contentNode.asText());
                } else if (contentNode.isArray()) {
                    // 已经是内容块数组
                    convertedMsg.set("content", contentNode);
                } else {
                    // 其他情况
                    ArrayNode contentArray = convertedMsg.putArray("content");
                    ObjectNode textBlock = contentArray.addObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", contentNode.toString());
                }

                convertedMessages.add(convertedMsg);
            } else if ("tool".equals(role)) {
                // 工具结果转换为 user 消息
                ObjectNode toolResultMsg = mapper.createObjectNode();
                toolResultMsg.put("role", "user");
                ArrayNode contentArray = toolResultMsg.putArray("content");

                ObjectNode toolResultBlock = contentArray.addObject();
                toolResultBlock.put("type", "tool_result");
                toolResultBlock.put("tool_use_id", msg.path("tool_call_id").asText());
                toolResultBlock.put("content", msg.path("content").asText());

                convertedMessages.add(toolResultMsg);
            }
        }

        // 设置系统提示
        if (systemPrompt.length() > 0) {
            body.put("system", systemPrompt.toString().trim());
        }

        // 设置消息
        ArrayNode messagesArray = body.putArray("messages");
        convertedMessages.forEach(messagesArray::add);

        // 转换工具定义
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (ObjectNode tool : tools) {
                ObjectNode anthropicTool = mapper.createObjectNode();
                anthropicTool.put("name", tool.path("function").path("name").asText());
                anthropicTool.put("description", tool.path("function").path("description").asText());

                // 转换 parameters
                JsonNode parameters = tool.path("function").path("parameters");
                anthropicTool.set("input_schema", parameters);

                toolsArray.add(anthropicTool);
            }
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

        // Anthropic 流式解析状态（使用数组包装器以支持 lambda）
        final StringBuilder[] currentToolArgs = {new StringBuilder()};
        final String[] currentToolId = {null};
        final String[] currentToolName = {null};

        long[] promptTokens = {0};
        long[] completionTokens = {0};

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        response.body().forEach(line -> {
                            log.error("Anthropic API error: {}", line);
                        });
                        future.completeExceptionally(new IOException("HTTP " + response.statusCode()));
                        return;
                    }

                    response.body().forEach(line -> {
                        if (line.isEmpty() || !line.startsWith("data: ")) {
                            return;
                        }

                        String data = line.substring(6);

                        try {
                            JsonNode event = mapper.readTree(data);
                            String type = event.path("type").asText("");

                            switch (type) {
                                case "content_block_delta" -> {
                                    JsonNode delta = event.path("delta");
                                    String deltaType = delta.path("type").asText("");

                                    if ("text_delta".equals(deltaType)) {
                                        String text = delta.path("text").asText("");
                                        content.append(text);
                                        if (onToken != null) {
                                            onToken.accept(text);
                                        }
                                    } else if ("input_json_delta".equals(deltaType)) {
                                        // 工具参数增量
                                        String partialJson = delta.path("partial_json").asText("");
                                        currentToolArgs[0].append(partialJson);
                                    }
                                }

                                case "content_block_start" -> {
                                    JsonNode contentBlock = event.path("content_block");
                                    String blockType = contentBlock.path("type").asText("");

                                    if ("tool_use".equals(blockType)) {
                                        currentToolId[0] = contentBlock.path("id").asText();
                                        currentToolName[0] = contentBlock.path("name").asText();
                                        currentToolArgs[0] = new StringBuilder();
                                    }
                                }

                                case "content_block_stop" -> {
                                    // 工具调用完成
                                    if (currentToolId[0] != null && currentToolName[0] != null) {
                                        JsonNode argsNode;
                                        try {
                                            argsNode = mapper.readTree(currentToolArgs[0].toString());
                                        } catch (Exception e) {
                                            argsNode = mapper.createObjectNode();
                                        }
                                        toolCalls.add(new ToolCall(currentToolId[0], currentToolName[0], argsNode));
                                        currentToolId[0] = null;
                                        currentToolName[0] = null;
                                    }
                                }

                                case "message_delta" -> {
                                    JsonNode usage = event.path("usage");
                                    if (!usage.isMissingNode()) {
                                        completionTokens[0] = usage.path("output_tokens").asLong(0);
                                    }
                                }

                                case "message_start" -> {
                                    JsonNode message = event.path("message");
                                    JsonNode usage = message.path("usage");
                                    if (!usage.isMissingNode()) {
                                        promptTokens[0] = usage.path("input_tokens").asLong(0);
                                    }
                                }

                                case "message_stop" -> {
                                    future.complete(new LLMResponse(
                                            content.toString(),
                                            toolCalls,
                                            promptTokens[0],
                                            completionTokens[0]
                                    ));
                                }
                            }

                        } catch (Exception e) {
                            log.error("Error parsing Anthropic SSE data: {}", data, e);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    future.completeExceptionally(new IOException("Failed to get Anthropic response", throwable));
                    return null;
                });

        try {
            LLMResponse response = future.get(5, TimeUnit.MINUTES);
            totalPromptTokens.addAndGet(response.promptTokens());
            totalCompletionTokens.addAndGet(response.completionTokens());
            return response;
        } catch (Exception e) {
            throw new IOException("Failed to get Anthropic response", e);
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
}
