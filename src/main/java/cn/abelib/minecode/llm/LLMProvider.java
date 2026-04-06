package cn.abelib.minecode.llm;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 提供商接口 - 抽象不同 LLM 后端
 *
 * <p>支持多种 LLM 提供商：
 * <ul>
 *   <li>OpenAI（及兼容 API：DeepSeek、Qwen、Kimi 等）</li>
 *   <li>Anthropic Claude</li>
 *   <li>Google Gemini</li>
 *   <li>本地模型（Ollama、vLLM 等）</li>
 * </ul>
 *
 * <p>实现此接口以支持新的 LLM 提供商。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 使用 OpenAI 兼容 API
 * LLMProvider openai = new OpenAIProvider(config);
 *
 * // 使用 Anthropic Claude
 * LLMProvider claude = new AnthropicProvider(config);
 *
 * // 统一调用
 * LLMResponse response = provider.chat(messages, tools, onToken);
 * }</pre>
 *
 * @author Abel
 */
public interface LLMProvider {

    /**
     * 获取提供商名称
     *
     * @return 提供商名称（如 "openai", "anthropic", "ollama"）
     */
    String getName();

    /**
     * 获取当前使用的模型名称
     *
     * @return 模型名称
     */
    String getModel();

    /**
     * 发送聊天请求（流式响应）
     *
     * @param messages 消息列表
     * @param tools    工具 Schema 列表（可为 null）
     * @param onToken  Token 回调（可为 null）
     * @return LLM 响应
     * @throws IOException 请求失败
     */
    LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools,
                     Consumer<String> onToken) throws IOException;

    /**
     * 发送聊天请求（非流式）
     *
     * <p>默认实现使用流式请求并收集所有 Token。
     * 子类可覆盖以优化非流式请求。
     *
     * @param messages 消息列表
     * @param tools    工具 Schema 列表（可为 null）
     * @return LLM 响应
     * @throws IOException 请求失败
     */
    default LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools) throws IOException {
        return chat(messages, tools, null);
    }

    /**
     * 发送简单聊天请求（无工具）
     *
     * @param messages 消息列表
     * @param onToken  Token 回调（可为 null）
     * @return LLM 响应
     * @throws IOException 请求失败
     */
    default LLMResponse chat(List<ObjectNode> messages, Consumer<String> onToken) throws IOException {
        return chat(messages, null, onToken);
    }

    /**
     * 获取总输入 Token 数
     *
     * @return 总输入 Token 数
     */
    long getTotalPromptTokens();

    /**
     * 获取总输出 Token 数
     *
     * @return 总输出 Token 数
     */
    long getTotalCompletionTokens();

    /**
     * 重置 Token 统计
     */
    default void resetTokenStats() {
        // 默认空实现
    }

    /**
     * 是否支持 Function Calling
     *
     * @return 是否支持
     */
    default boolean supportsFunctionCalling() {
        return true;
    }

    /**
     * 是否支持流式输出
     *
     * @return 是否支持
     */
    default boolean supportsStreaming() {
        return true;
    }

    /**
     * 获取提供商能力描述
     *
     * @return 能力描述
     */
    default String getCapabilities() {
        return String.format("Provider: %s, Model: %s, FunctionCalling: %s, Streaming: %s",
                getName(), getModel(),
                supportsFunctionCalling() ? "yes" : "no",
                supportsStreaming() ? "yes" : "no");
    }
}
