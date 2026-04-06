package cn.abelib.minecode.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Config 模块测试
 *
 * @author Abel
 */
class ConfigTest {

    private MineCodeConfig config;

    @BeforeEach
    void setUp() {
        config = new MineCodeConfig();
    }

    // ==================== Default Values Tests ====================

    @Test
    void testDefaultValues() {
        assertEquals("gpt-4o", config.get(MineCodeConfig.MODEL));
        assertEquals("https://api.openai.com/v1", config.get(MineCodeConfig.BASE_URL));
        assertEquals("0", config.get(MineCodeConfig.TEMPERATURE));
        assertEquals("4096", config.get(MineCodeConfig.MAX_TOKENS));
        assertEquals("128000", config.get(MineCodeConfig.MAX_CONTEXT));
        assertEquals("50", config.get(MineCodeConfig.MAX_ROUNDS));
        assertEquals("dark", config.get(MineCodeConfig.THEME));
        assertEquals("true", config.get(MineCodeConfig.AUTO_SAVE));
    }

    // ==================== Get Methods Tests ====================

    @Test
    void testGet_existingKey() {
        String value = config.get(MineCodeConfig.MODEL);
        assertNotNull(value);
        assertEquals("gpt-4o", value);
    }

    @Test
    void testGet_nonExistingKey() {
        String value = config.get("non_existing_key");
        assertNull(value);
    }

    @Test
    void testGet_withDefaultValue_existing() {
        String value = config.get(MineCodeConfig.MODEL, "default_value");
        assertEquals("gpt-4o", value);
    }

    @Test
    void testGet_withDefaultValue_nonExisting() {
        String value = config.get("non_existing_key", "default_value");
        assertEquals("default_value", value);
    }

    // ==================== Type Conversion Tests ====================

    @Test
    void testGetInt() {
        int maxTokens = config.getInt(MineCodeConfig.MAX_TOKENS);
        assertEquals(4096, maxTokens);
    }

    @Test
    void testGetInt_nonExisting() {
        int value = config.getInt("non_existing_int");
        assertEquals(0, value);
    }

    @Test
    void testGetDouble() {
        double temperature = config.getDouble(MineCodeConfig.TEMPERATURE);
        assertEquals(0.0, temperature, 0.001);
    }

    @Test
    void testGetDouble_nonExisting() {
        double value = config.getDouble("non_existing_double");
        assertEquals(0.0, value, 0.001);
    }

    @Test
    void testGetBoolean_true() {
        boolean autoSave = config.getBoolean(MineCodeConfig.AUTO_SAVE);
        assertTrue(autoSave);
    }

    @Test
    void testGetBoolean_false() {
        config.set("test_bool", "false");
        boolean value = config.getBoolean("test_bool");
        assertFalse(value);
    }

    @Test
    void testGetBoolean_nonExisting() {
        boolean value = config.getBoolean("non_existing_bool");
        assertFalse(value);
    }

    // ==================== Set Method Tests ====================

    @Test
    void testSet() {
        config.set(MineCodeConfig.MODEL, "claude-3");
        assertEquals("claude-3", config.get(MineCodeConfig.MODEL));
    }

    @Test
    void testSet_newKey() {
        config.set("custom_key", "custom_value");
        assertEquals("custom_value", config.get("custom_key"));
    }

    @Test
    void testSet_overwrite() {
        config.set(MineCodeConfig.MODEL, "gpt-4");
        assertEquals("gpt-4", config.get(MineCodeConfig.MODEL));

        config.set(MineCodeConfig.MODEL, "gpt-4o-mini");
        assertEquals("gpt-4o-mini", config.get(MineCodeConfig.MODEL));
    }

    // ==================== ToLLMConfig Tests ====================

    @Test
    void testToLLMConfig() {
        config.set(MineCodeConfig.MODEL, "gpt-4");
        config.set(MineCodeConfig.BASE_URL, "https://api.example.com/v1");
        config.set(MineCodeConfig.TEMPERATURE, "0.5");
        config.set(MineCodeConfig.MAX_TOKENS, "8192");

        cn.abelib.minecode.llm.LLMConfig llmConfig = config.toLLMConfig();

        assertNotNull(llmConfig);
        assertEquals("gpt-4", llmConfig.getModel());
        assertEquals("https://api.example.com/v1", llmConfig.getBaseUrl());
        assertEquals(0.5, llmConfig.getTemperature(), 0.001);
        assertEquals(8192, llmConfig.getMaxTokens());
    }

    // ==================== Config Keys Tests ====================

    @Test
    void testConfigKeys() {
        assertEquals("model", MineCodeConfig.MODEL);
        assertEquals("api_key", MineCodeConfig.API_KEY);
        assertEquals("base_url", MineCodeConfig.BASE_URL);
        assertEquals("temperature", MineCodeConfig.TEMPERATURE);
        assertEquals("max_tokens", MineCodeConfig.MAX_TOKENS);
        assertEquals("max_context", MineCodeConfig.MAX_CONTEXT);
        assertEquals("max_rounds", MineCodeConfig.MAX_ROUNDS);
        assertEquals("theme", MineCodeConfig.THEME);
        assertEquals("auto_save", MineCodeConfig.AUTO_SAVE);
    }

    // ==================== Print Config Tests ====================

    @Test
    void testPrintConfig() {
        // Should not throw exception
        assertDoesNotThrow(() -> config.printConfig());
    }

    @Test
    void testPrintConfig_withApiKey() {
        config.set(MineCodeConfig.API_KEY, "sk-test123456789");

        // Should not throw exception and should mask API key
        assertDoesNotThrow(() -> config.printConfig());
    }

    // ==================== Load Config Tests ====================

    @Test
    void testLoad_resetsToDefaults() {
        config.set(MineCodeConfig.MODEL, "custom-model");
        assertEquals("custom-model", config.get(MineCodeConfig.MODEL));

        config.load();

        // Should reset to default
        assertEquals("gpt-4o", config.get(MineCodeConfig.MODEL));
    }

    // ==================== Integer Parsing Edge Cases ====================

    @Test
    void testGetInt_largeValue() {
        config.set(MineCodeConfig.MAX_CONTEXT, "999999999");
        int value = config.getInt(MineCodeConfig.MAX_CONTEXT);
        assertEquals(999999999, value);
    }

    @Test
    void testGetInt_zero() {
        config.set("zero_value", "0");
        int value = config.getInt("zero_value");
        assertEquals(0, value);
    }

    @Test
    void testGetInt_negative() {
        config.set("negative_value", "-100");
        int value = config.getInt("negative_value");
        assertEquals(-100, value);
    }

    // ==================== Double Parsing Edge Cases ====================

    @Test
    void testGetDouble_decimal() {
        config.set(MineCodeConfig.TEMPERATURE, "0.7");
        double value = config.getDouble(MineCodeConfig.TEMPERATURE);
        assertEquals(0.7, value, 0.001);
    }

    @Test
    void testGetDouble_integer() {
        config.set("int_as_double", "42");
        double value = config.getDouble("int_as_double");
        assertEquals(42.0, value, 0.001);
    }

    // ==================== Boolean Parsing Edge Cases ====================

    @Test
    void testGetBoolean_variousTrueValues() {
        String[] trueValues = {"true", "TRUE", "True", "yes", "YES", "1"};

        for (String trueVal : trueValues) {
            // Only "true" (case-insensitive) should be true
            config.set("test_bool", trueVal);
            boolean expected = trueVal.equalsIgnoreCase("true");
            assertEquals(expected, config.getBoolean("test_bool"),
                    "Expected '" + trueVal + "' to be " + expected);
        }
    }

    @Test
    void testGetBoolean_variousFalseValues() {
        String[] falseValues = {"false", "FALSE", "no", "NO", "0", "anything"};

        for (String falseVal : falseValues) {
            config.set("test_bool", falseVal);
            // Only "true" is true, everything else is false
            boolean expected = falseVal.equalsIgnoreCase("true");
            assertEquals(expected, config.getBoolean("test_bool"));
        }
    }
}
