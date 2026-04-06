package cn.abelib.minecode.llm;

import cn.abelib.minecode.llm.provider.OpenAIProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 客户端 - 门面类，支持多种 LLM 提供商
 *
 * <p>这是一个门面类，内部委托给具体的 {@link LLMProvider} 实现。
 * 默认使用 OpenAI 兼容 API，可通过配置切换到其他提供商。
 *
 * <p>支持的提供商：
 * <ul>
 *   <li><b>openai</b> - OpenAI 及兼容 API（默认）</li>
 *   <li><b>anthropic</b> - Anthropic Claude</li>
 *   <li><b>ollama</b> - 本地 Ollama</li>
 *   <li>自定义提供商（实现 LLMProvider 接口）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 方式 1: 使用默认 OpenAI 兼容 API
 * LLMClient client = LLMClient.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .model("gpt-4o")
 *     .build();
 *
 * // 方式 2: 使用 DeepSeek
 * LLMClient client = LLMClient.builder()
 *     .provider("openai")
 *     .apiKey(System.getenv("DEEPSEEK_API_KEY"))
 *     .baseUrl("https://api.deepseek.com/v1")
 *     .model("deepseek-chat")
 *     .build();
 *
 * // 方式 3: 使用 Anthropic Claude
 * LLMClient client = LLMClient.builder()
 *     .provider("anthropic")
 *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
 *     .model("claude-3-5-sonnet-20241022")
 *     .build();
 *
 * // 方式 4: 自定义提供商
 * LLMClient client = new LLMClient(new MyCustomProvider(config));
 *
 * // 统一调用
 * LLMResponse response = client.chat(messages, tools, onToken);
 * }</pre>
 *
 * @author Abel
 */
public class LLMClient implements LLMProvider {
    private static final Logger log = LoggerFactory.getLogger(LLMClient.class);

    private final LLMProvider provider;
    private final LLMConfig config;

    /**
     * 从配置创建客户端（自动检测提供商）
     *
     * <p>根据 baseUrl 或 model 自动检测使用哪个提供商：
     * <ul>
     *   <li>baseUrl 包含 "anthropic" -> Anthropic</li>
     *   <li>model 以 "claude" 开头 -> Anthropic</li>
     *   <li>其他 -> OpenAI 兼容</li>
     * </ul>
     */
    public LLMClient(LLMConfig config) {
        this.config = config;
        this.provider = detectProvider(config);
        log.info("LLMClient initialized with provider: {}", provider.getName());
    }

    /**
     * 使用指定提供商创建客户端
     */
    public LLMClient(LLMProvider provider) {
        this.provider = provider;
        this.config = null;
        log.info("LLMClient initialized with custom provider: {}", provider.getName());
    }

    /**
     * 私有构造函数，使用 Builder 创建
     */
    private LLMClient(Builder builder) {
        this.config = builder.toConfig();
        this.provider = createProvider(builder);
        log.info("LLMClient initialized with provider: {} ({})", provider.getName(), provider.getModel());
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 自动检测提供商类型
     */
    private LLMProvider detectProvider(LLMConfig config) {
        String baseUrl = config.getBaseUrl();
        String model = config.getModel();

        // 根据 baseUrl 判断
        if (baseUrl != null && baseUrl.toLowerCase().contains("anthropic")) {
            log.debug("Detected Anthropic provider from baseUrl");
            return new OpenAIProvider(config); // Anthropic 有自己的 Provider
        }

        // 根据模型名称判断
        if (model != null && model.toLowerCase().startsWith("claude")) {
            log.debug("Detected Anthropic provider from model name");
            return new cn.abelib.minecode.llm.provider.AnthropicProvider(config);
        }

        // 默认使用 OpenAI 兼容
        return new OpenAIProvider(config);
    }

    /**
     * 根据配置创建提供商
     */
    private LLMProvider createProvider(Builder builder) {
        return switch (builder.provider.toLowerCase()) {
            case "anthropic", "claude" -> new cn.abelib.minecode.llm.provider.AnthropicProvider(builder.toConfig());
            default -> new OpenAIProvider(builder.toConfig());
        };
    }

    // ==================== 委托给内部 Provider ====================

    @Override
    public String getName() {
        return provider.getName();
    }

    @Override
    public String getModel() {
        return provider.getModel();
    }

    @Override
    public LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools,
                            Consumer<String> onToken) throws IOException {
        return provider.chat(messages, tools, onToken);
    }

    @Override
    public long getTotalPromptTokens() {
        return provider.getTotalPromptTokens();
    }

    @Override
    public long getTotalCompletionTokens() {
        return provider.getTotalCompletionTokens();
    }

    @Override
    public void resetTokenStats() {
        provider.resetTokenStats();
    }

    @Override
    public boolean supportsFunctionCalling() {
        return provider.supportsFunctionCalling();
    }

    @Override
    public boolean supportsStreaming() {
        return provider.supportsStreaming();
    }

    @Override
    public String getCapabilities() {
        return provider.getCapabilities();
    }

    /**
     * 获取内部提供商
     */
    public LLMProvider getProvider() {
        return provider;
    }

    /**
     * 获取配置
     */
    public LLMConfig getConfig() {
        return config;
    }

    // ==================== Builder ====================

    /**
     * LLMClient Builder
     *
     * <p>使用示例：
     * <pre>{@code
     * // OpenAI
     * LLMClient client = LLMClient.builder()
     *     .apiKey(System.getenv("OPENAI_API_KEY"))
     *     .model("gpt-4o")
     *     .build();
     *
     * // DeepSeek
     * LLMClient client = LLMClient.builder()
     *     .provider("openai")
     *     .apiKey(System.getenv("DEEPSEEK_API_KEY"))
     *     .baseUrl("https://api.deepseek.com/v1")
     *     .model("deepseek-chat")
     *     .build();
     *
     * // Anthropic Claude
     * LLMClient client = LLMClient.builder()
     *     .provider("anthropic")
     *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
     *     .model("claude-3-5-sonnet-20241022")
     *     .build();
     * }</pre>
     */
    public static class Builder {
        // 提供商类型
        private String provider = "openai";

        // 必需参数
        private String apiKey;
        private String model = "gpt-4o";

        // 可选参数
        private String baseUrl = "https://api.openai.com/v1";
        private Double temperature = 0.0;
        private Integer maxTokens = 4096;
        private int connectTimeoutSeconds = 30;

        /**
         * 设置提供商类型
         *
         * <p>支持的值：
         * <ul>
         *   <li>"openai" - OpenAI 兼容 API（默认）</li>
         *   <li>"anthropic" / "claude" - Anthropic Claude</li>
         * </ul>
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

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
         * 设置温度参数
         */
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * 设置最大输出 Token 数
         */
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * 设置连接超时时间（秒）
         */
        public Builder connectTimeout(int seconds) {
            this.connectTimeoutSeconds = seconds;
            return this;
        }

        /**
         * 从环境变量读取配置
         */
        public Builder fromEnv() {
            this.apiKey = getEnv("OPENAI_API_KEY", this.apiKey);
            this.model = getEnv("MINECODE_MODEL", this.model);
            this.baseUrl = getEnv("OPENAI_BASE_URL", this.baseUrl);

            // 支持其他提供商的环境变量
            String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
            if (anthropicKey != null && !anthropicKey.isEmpty()) {
                this.provider = "anthropic";
                this.apiKey = anthropicKey;
                this.model = getEnv("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022");
                this.baseUrl = getEnv("ANTHROPIC_BASE_URL", "https://api.anthropic.com/v1");
            }

            return this;
        }

        private String getEnv(String key, String defaultValue) {
            String value = System.getenv(key);
            return value != null ? value : defaultValue;
        }

        /**
         * 构建 LLMClient 实例
         *
         * @throws IllegalStateException 如果未设置必需的 API Key
         */
        public LLMClient build() {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("API Key is required");
            }
            return new LLMClient(this);
        }

        private LLMConfig toConfig() {
            LLMConfig config = new LLMConfig();
            config.setApiKey(apiKey);
            config.setModel(model);
            config.setBaseUrl(baseUrl);
            config.setTemperature(temperature);
            config.setMaxTokens(maxTokens);
            return config;
        }
    }
}
