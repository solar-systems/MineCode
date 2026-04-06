package cn.abelib.minecode.config;

import cn.abelib.minecode.llm.LLMConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MineCode 配置管理
 *
 * <p>配置来源优先级：
 * <ol>
 *   <li>环境变量（最高优先级）</li>
 *   <li>.env 文件（当前目录）</li>
 *   <li>配置文件 ~/.minecode/config.json</li>
 *   <li>默认值</li>
 * </ol>
 *
 * @author Abel
 */
public class MineCodeConfig {
    private static final Logger log = LoggerFactory.getLogger(MineCodeConfig.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Path configFile;
    private final Path envFile;
    private final Map<String, String> config;

    // 配置键
    public static final String MODEL = "model";
    public static final String API_KEY = "api_key";
    public static final String BASE_URL = "base_url";
    public static final String TEMPERATURE = "temperature";
    public static final String MAX_TOKENS = "max_tokens";
    public static final String MAX_CONTEXT = "max_context";
    public static final String MAX_ROUNDS = "max_rounds";
    public static final String THEME = "theme";
    public static final String AUTO_SAVE = "auto_save";

    // 默认值
    private static final Map<String, String> DEFAULTS = Map.of(
            MODEL, "gpt-4o",
            BASE_URL, "https://api.openai.com/v1",
            TEMPERATURE, "0",
            MAX_TOKENS, "4096",
            MAX_CONTEXT, "128000",
            MAX_ROUNDS, "50",
            THEME, "dark",
            AUTO_SAVE, "true"
    );

    public MineCodeConfig() {
        this.configFile = Path.of(System.getProperty("user.home"), ".minecode", "config.json");
        this.envFile = Path.of(System.getProperty("user.dir"), ".env");
        this.config = new HashMap<>();
        load();
    }

    /**
     * 加载配置
     */
    public void load() {
        // 先加载默认值
        config.putAll(DEFAULTS);

        // 加载配置文件
        if (Files.exists(configFile)) {
            try {
                ObjectNode json = (ObjectNode) mapper.readTree(Files.readString(configFile));
                json.fields().forEachRemaining(entry -> config.put(entry.getKey(), entry.getValue().asText()));
                log.debug("Config loaded from: {}", configFile);
            } catch (IOException e) {
                log.warn("Failed to load config file: {}", e.getMessage());
            }
        }

        // 加载 .env 文件
        loadFromEnvFile();

        // 最后加载环境变量（覆盖）
        loadFromEnv();
    }

    /**
     * 从 .env 文件加载配置
     */
    private void loadFromEnvFile() {
        if (!Files.exists(envFile)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(envFile);
            for (String line : lines) {
                line = line.trim();
                // 跳过注释和空行
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int eqIndex = line.indexOf('=');
                if (eqIndex > 0) {
                    String key = line.substring(0, eqIndex).trim();
                    String value = line.substring(eqIndex + 1).trim();

                    // 移除引号
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    // 映射到配置键
                    mapEnvToConfig(key, value);
                }
            }
            log.debug("Config loaded from .env file: {}", envFile);
        } catch (IOException e) {
            log.warn("Failed to load .env file: {}", e.getMessage());
        }
    }

    /**
     * 映射环境变量名到配置键
     */
    private void mapEnvToConfig(String envKey, String value) {
        switch (envKey) {
            case "MINECODE_MODEL" -> config.put(MODEL, value);
            case "OPENAI_API_KEY" -> config.put(API_KEY, value);
            case "OPENAI_BASE_URL" -> config.put(BASE_URL, value);
            case "MINECODE_TEMPERATURE" -> config.put(TEMPERATURE, value);
            case "MINECODE_MAX_TOKENS" -> config.put(MAX_TOKENS, value);
            case "MINECODE_MAX_CONTEXT" -> config.put(MAX_CONTEXT, value);
            case "MINECODE_MAX_ROUNDS" -> config.put(MAX_ROUNDS, value);
        }
    }

    private void loadFromEnv() {
        putIfPresent(MODEL, "MINECODE_MODEL");
        putIfPresent(API_KEY, "OPENAI_API_KEY");
        putIfPresent(BASE_URL, "OPENAI_BASE_URL");
        putIfPresent(TEMPERATURE, "MINECODE_TEMPERATURE");
        putIfPresent(MAX_TOKENS, "MINECODE_MAX_TOKENS");
        putIfPresent(MAX_CONTEXT, "MINECODE_MAX_CONTEXT");
        putIfPresent(MAX_ROUNDS, "MINECODE_MAX_ROUNDS");
    }

    private void putIfPresent(String configKey, String envKey) {
        String value = System.getenv(envKey);
        if (value != null && !value.isEmpty()) {
            config.put(configKey, value);
        }
    }

    /**
     * 保存配置到文件
     */
    public void save() {
        try {
            Files.createDirectories(configFile.getParent());

            ObjectNode json = mapper.createObjectNode();
            config.forEach(json::put);

            Files.writeString(configFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
            log.info("Config saved to: {}", configFile);

        } catch (IOException e) {
            log.error("Failed to save config", e);
        }
    }

    /**
     * 获取配置值
     */
    public String get(String key) {
        return config.get(key);
    }

    /**
     * 获取配置值（带默认值）
     */
    public String get(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }

    /**
     * 设置配置值
     */
    public void set(String key, String value) {
        config.put(key, value);
    }

    /**
     * 获取整数配置
     */
    public int getInt(String key) {
        return Integer.parseInt(get(key, "0"));
    }

    /**
     * 获取双精度配置
     */
    public double getDouble(String key) {
        return Double.parseDouble(get(key, "0"));
    }

    /**
     * 获取布尔配置
     */
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key, "false"));
    }

    /**
     * 获取 LLM 配置
     */
    public LLMConfig toLLMConfig() {
        LLMConfig llmConfig = new LLMConfig();
        llmConfig.setModel(get(MODEL));
        llmConfig.setApiKey(get(API_KEY));
        llmConfig.setBaseUrl(get(BASE_URL));
        llmConfig.setTemperature(getDouble(TEMPERATURE));
        llmConfig.setMaxTokens(getInt(MAX_TOKENS));
        llmConfig.setMaxContextTokens(getInt(MAX_CONTEXT));
        return llmConfig;
    }

    /**
     * 打印当前配置
     */
    public void printConfig() {
        System.out.println("Current Configuration:");
        System.out.println("  Model:        " + get(MODEL));
        System.out.println("  Base URL:     " + get(BASE_URL));
        System.out.println("  Temperature:  " + get(TEMPERATURE));
        System.out.println("  Max Tokens:   " + get(MAX_TOKENS));
        System.out.println("  Max Context:  " + get(MAX_CONTEXT));
        System.out.println("  Max Rounds:   " + get(MAX_ROUNDS));
        System.out.println("  Theme:        " + get(THEME));
        System.out.println("  Auto Save:    " + get(AUTO_SAVE));
        // 不打印 API Key
        System.out.println("  API Key:      " + (get(API_KEY) != null ? "****" + get(API_KEY).substring(Math.max(0, get(API_KEY).length() - 4)) : "(not set)"));
    }
}
