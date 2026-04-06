package cn.abelib.minecode.llm;

/**
 * LLM 配置
 *
 * <p>使用示例：
 * <pre>{@code
 * // 使用 Builder
 * LLMConfig config = LLMConfig.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .model("gpt-4o")
 *     .baseUrl("https://api.openai.com/v1")
 *     .temperature(0.0)
 *     .maxTokens(4096)
 *     .build();
 *
 * // 从环境变量
 * LLMConfig config = LLMConfig.fromEnv();
 * }</pre>
 *
 * @author Abel
 */
public class LLMConfig {
    private String model;
    private String apiKey;
    private String baseUrl;
    private Double temperature;
    private Integer maxTokens;
    private int maxContextTokens = 128_000;

    public LLMConfig() {
    }

    /**
     * 从环境变量创建配置
     *
     * <p>支持的环境变量：
     * <ul>
     *   <li>MINECODE_MODEL - 模型名称（默认 gpt-4o）</li>
     *   <li>OPENAI_API_KEY - OpenAI API Key</li>
     *   <li>OPENAI_BASE_URL - API 基础 URL</li>
     *   <li>ANTHROPIC_API_KEY - Anthropic API Key（优先）</li>
     *   <li>ANTHROPIC_MODEL - Anthropic 模型名称</li>
     *   <li>MINECODE_TEMPERATURE - 温度参数</li>
     *   <li>MINECODE_MAX_TOKENS - 最大输出 Token</li>
     *   <li>MINECODE_MAX_CONTEXT - 最大上下文 Token</li>
     * </ul>
     */
    public static LLMConfig fromEnv() {
        LLMConfig config = new LLMConfig();

        // 优先检查 Anthropic
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        if (anthropicKey != null && !anthropicKey.isEmpty()) {
            config.setApiKey(anthropicKey);
            config.setModel(getEnv("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022"));
            config.setBaseUrl(getEnv("ANTHROPIC_BASE_URL", "https://api.anthropic.com/v1"));
        } else {
            config.setModel(getEnv("MINECODE_MODEL", "gpt-4o"));
            config.setApiKey(getEnv("OPENAI_API_KEY", ""));
            config.setBaseUrl(getEnv("OPENAI_BASE_URL", null));
        }

        config.setTemperature(Double.parseDouble(getEnv("MINECODE_TEMPERATURE", "0")));
        config.setMaxTokens(Integer.parseInt(getEnv("MINECODE_MAX_TOKENS", "4096")));
        config.setMaxContextTokens(Integer.parseInt(getEnv("MINECODE_MAX_CONTEXT", "128000")));
        return config;
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }

    /**
     * LLMConfig Builder
     */
    public static class Builder {
        private String model = "gpt-4o";
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private Double temperature = 0.0;
        private Integer maxTokens = 4096;
        private int maxContextTokens = 128_000;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder maxContextTokens(int maxContextTokens) {
            this.maxContextTokens = maxContextTokens;
            return this;
        }

        /**
         * 从环境变量读取配置
         */
        public Builder fromEnv() {
            this.apiKey = getEnv("OPENAI_API_KEY", this.apiKey);
            this.model = getEnv("MINECODE_MODEL", this.model);
            this.baseUrl = getEnv("OPENAI_BASE_URL", this.baseUrl);

            String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
            if (anthropicKey != null && !anthropicKey.isEmpty()) {
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

        public LLMConfig build() {
            LLMConfig config = new LLMConfig();
            config.setModel(model);
            config.setApiKey(apiKey);
            config.setBaseUrl(baseUrl);
            config.setTemperature(temperature);
            config.setMaxTokens(maxTokens);
            config.setMaxContextTokens(maxContextTokens);
            return config;
        }
    }
}
