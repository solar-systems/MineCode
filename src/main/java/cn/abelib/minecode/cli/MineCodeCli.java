package cn.abelib.minecode.cli;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.config.MineCodeConfig;
import cn.abelib.minecode.hook.BuiltinHooks;
import cn.abelib.minecode.hook.Hook;
import cn.abelib.minecode.hook.HookBuilder;
import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.llm.LLMConfig;
import cn.abelib.minecode.permission.PermissionDecision;
import cn.abelib.minecode.session.SessionManager;
import cn.abelib.minecode.skill.SkillContext;
import cn.abelib.minecode.skill.SkillRegistry;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jline.reader.*;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MineCode 命令行界面
 *
 * <p>功能：
 * <ul>
 *   <li>交互式 REPL</li>
 *   <li>命令解析</li>
 *   <li>流式输出显示</li>
 *   <li>会话管理</li>
 * </ul>
 *
 * @author Abel
 */
@Command(name = "minecode", mixinStandardHelpOptions = true, version = "MineCode 1.0.0",
        description = "Minimal AI coding agent in Java")
public class MineCodeCli implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MineCodeCli.class);

    // 颜色样式常量
    private static final AttributedStyle STYLE_CYAN = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle STYLE_GREEN = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
    private static final AttributedStyle STYLE_YELLOW = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle STYLE_RED = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
    private static final AttributedStyle STYLE_BLUE = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE);
    private static final AttributedStyle STYLE_DIM = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT | AttributedStyle.BLACK);
    private static final AttributedStyle STYLE_BOLD_CYAN = STYLE_CYAN.bold();

    @Option(names = {"-m", "--model"}, description = "LLM model to use")
    private String model;

    @Option(names = {"-s", "--session"}, description = "Session ID to resume")
    private String sessionId;

    @Option(names = {"--no-save"}, description = "Disable auto-save")
    private boolean noSave = false;

    @Option(names = {"--config"}, description = "Show current configuration")
    private boolean showConfig = false;

    @Option(names = {"--verbose"}, description = "Enable verbose logging")
    private boolean verbose = false;

    private MineCodeConfig config;
    private LLMClient llmClient;
    private Agent agent;
    private SessionManager sessionManager;
    private SkillRegistry skillRegistry;
    private Terminal terminal;
    private LineReader reader;

    // 内置 Hooks
    private BuiltinHooks.TokenStatsHook tokenStatsHook;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MineCodeCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            initialize();

            if (showConfig) {
                config.printConfig();
                return;
            }

            printWelcome();
            runRepl();

        } catch (Exception e) {
            log.error("Fatal error", e);
            System.err.println("Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void initialize() throws IOException {
        // 加载配置
        config = new MineCodeConfig();

        // 命令行参数覆盖配置
        if (model != null) {
            config.set(MineCodeConfig.MODEL, model);
        }

        // 使用 Builder 创建 LLM 客户端
        LLMConfig llmConfig = config.toLLMConfig();
        if (llmConfig.getApiKey() == null || llmConfig.getApiKey().isEmpty()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set.");
            System.err.println("Please set it using: export OPENAI_API_KEY=your-api-key");
            System.exit(1);
        }

        llmClient = LLMClient.builder()
                .apiKey(llmConfig.getApiKey())
                .model(llmConfig.getModel())
                .baseUrl(llmConfig.getBaseUrl())
                .temperature(llmConfig.getTemperature())
                .maxTokens(llmConfig.getMaxTokens())
                .build();

        // 准备 Hooks
        List<Hook> hooks = new ArrayList<>();
        hooks.add(new BuiltinHooks.LoggingHook(verbose));

        // Token 统计 Hook
        tokenStatsHook = new BuiltinHooks.TokenStatsHook();
        hooks.add(tokenStatsHook);

        // 权限 Hook - 用户确认危险操作
        Hook permissionHook = HookBuilder.permission(builder -> builder
                .allow("read_file")
                .allow("glob")
                .allow("grep")
                .allow("write_file")
                .allow("edit_file")
                .askUser("bash(rm *)")
                .askUser("bash(sudo *)")
                .askUser("bash(mkfs *)")
                .userConfirmHandler((tool, args) -> {
                    // 简单的控制台确认
                    System.out.println("\n⚠ 危险操作确认:");
                    System.out.println("  工具: " + tool);
                    System.out.println("  参数: " + (args != null && args.length() > 100 ? args.substring(0, 100) + "..." : args));
                    System.out.print("是否允许执行? [y/N]: ");
                    try {
                        String input = System.console() != null ? System.console().readLine() : "n";
                        return "y".equalsIgnoreCase(input) || "yes".equalsIgnoreCase(input)
                                ? PermissionDecision.ALLOW
                                : PermissionDecision.DENY;
                    } catch (Exception e) {
                        return PermissionDecision.DENY;
                    }
                })
        );
        hooks.add(permissionHook);

        // 使用 Builder 创建 Agent
        int maxContext = config.getInt(MineCodeConfig.MAX_CONTEXT);
        int maxRounds = config.getInt(MineCodeConfig.MAX_ROUNDS);

        agent = Agent.builder()
                .llm(llmClient)
                .name("MineCode")
                .description("AI coding assistant")
                .hooks(hooks)
                .maxContextTokens(maxContext)
                .maxRounds(maxRounds)
                .build();

        // 会话管理
        sessionManager = new SessionManager();
        sessionManager.setModel(llmConfig.getModel());

        // 初始化技能注册器
        skillRegistry = new SkillRegistry();

        // 加载会话
        if (sessionId != null) {
            List<ObjectNode> messages = sessionManager.loadSession(sessionId);
            if (messages != null) {
                for (ObjectNode msg : messages) {
                    agent.getMessages().add(msg);
                }
                System.out.println(color(STYLE_GREEN, "Resumed session: " + sessionId));
            } else {
                System.out.println(color(STYLE_RED, "Session not found: " + sessionId));
            }
        } else {
            sessionManager.createNewSession(llmConfig.getModel());
        }

        // 初始化终端
        terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(new DefaultHistory())
                .build();
    }

    private void runRepl() {
        while (true) {
            try {
                String prompt = color(STYLE_CYAN, "minecode> ");
                String line = reader.readLine(prompt);

                if (line == null || line.trim().isEmpty()) {
                    continue;
                }

                // 处理内置命令
                if (handleBuiltinCommand(line.trim())) {
                    continue;
                }

                // 发送给 Agent
                processUserMessage(line);

            } catch (UserInterruptException e) {
                // Ctrl+C
                System.out.println();
                continue;
            } catch (EndOfFileException e) {
                // Ctrl+D
                System.out.println("\nGoodbye!");
                break;
            } catch (Exception e) {
                log.error("Error in REPL", e);
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    /**
     * 处理内置命令
     *
     * @return true 如果是内置命令并已处理
     */
    private boolean handleBuiltinCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : null;

        // 特殊命令：退出和设置需要直接处理
        switch (cmd) {
            case "/exit", "/quit", "/q" -> {
                executeSkill("save", null);
                System.out.println("Goodbye!");
                System.exit(0);
            }
            case "/set" -> {
                if (args == null) {
                    System.out.println(color(STYLE_RED, "Usage: /set <key> <value>"));
                } else {
                    setConfig(args);
                }
                return true;
            }
        }

        // 其他命令通过 SkillRegistry 处理
        if (cmd.startsWith("/")) {
            String skillName = cmd.substring(1);  // 移除 "/" 前缀

            // 处理别名
            skillName = resolveAlias(skillName);

            if (skillRegistry.exists(skillName)) {
                executeSkill(skillName, args);
                return true;
            }

            System.out.println(color(STYLE_RED, "Unknown command: " + cmd));
            System.out.println("Type /help for available commands.");
            return true;
        }

        return false;
    }

    /**
     * 解析命令别名
     */
    private String resolveAlias(String cmd) {
        return switch (cmd) {
            case "h", "?" -> "help";
            case "cls" -> "clear";
            default -> cmd;
        };
    }

    /**
     * 执行技能
     */
    private void executeSkill(String name, String args) {
        try {
            SkillContext context = SkillContext.builder()
                    .agent(agent)
                    .llmClient(llmClient)
                    .sessionManager(sessionManager)
                    .config(config)
                    .terminal(terminal)
                    .workingDirectory(Path.of("").toAbsolutePath())
                    .build();

            String result = skillRegistry.execute(name, context, args);
            if (result != null && !result.isEmpty()) {
                System.out.println(result);
            }
        } catch (SkillRegistry.SkillNotFoundException e) {
            System.out.println(color(STYLE_RED, "Unknown skill: " + name));
        } catch (SkillRegistry.SkillExecutionException e) {
            System.out.println(color(STYLE_RED, "Error: " + e.getMessage()));
        }
    }

    private void processUserMessage(String input) {
        System.out.println(); // 空行分隔

        AtomicBoolean firstToken = new AtomicBoolean(true);

        try {
            String result = agent.chat(input, new Agent.ChatCallback() {
                @Override
                public void onToken(String token) {
                    if (firstToken.get()) {
                        System.out.print(color(STYLE_YELLOW, "Assistant: "));
                        firstToken.set(false);
                    }
                    System.out.print(token);
                    System.out.flush();
                }

                @Override
                public void onTool(String name, com.fasterxml.jackson.databind.JsonNode arguments, String result) {
                    System.out.println(color(STYLE_BLUE, "\n  → Tool: " + name));
                    System.out.flush();
                }
            });

            System.out.println();
            System.out.println();

            // 自动保存
            if (config.getBoolean(MineCodeConfig.AUTO_SAVE) && !noSave) {
                sessionManager.autoSave(agent.getMessages());
            }

        } catch (IOException e) {
            log.error("Failed to process message", e);
            System.out.println(color(STYLE_RED, "Error: " + e.getMessage()));
        }
    }

    private void setConfig(String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            System.out.println(color(STYLE_RED, "Usage: /set <key> <value>"));
            return;
        }

        String key = parts[0];
        String value = parts[1];

        config.set(key, value);
        System.out.println(color(STYLE_GREEN, "Set " + key + " = " + value));
    }

    private void printWelcome() {
        System.out.println();
        System.out.println(color(STYLE_BOLD_CYAN,
                "  __  __ _    _  ___   ___  ___   ___ _  _ "));
        System.out.println(color(STYLE_BOLD_CYAN,
                " |  \\/  | |  | ||   \\ / _ \\/ __| / __| || |"));
        System.out.println(color(STYLE_BOLD_CYAN,
                " | |\\/| | |__| || |) | (_) \\__ \\| (__| __ |"));
        System.out.println(color(STYLE_BOLD_CYAN,
                " |_|  |_|____|_||___/ \\___/|___/ \\___|_||_|"));
        System.out.println();
        System.out.println(color(STYLE_DIM,
                "  Minimal AI coding agent | Model: " + config.get(MineCodeConfig.MODEL)));
        System.out.println(color(STYLE_DIM,
                "  Type /help for commands, Ctrl+D to exit"));
        System.out.println();
    }

    private String color(AttributedStyle style, String text) {
        return new AttributedString(text, style).toAnsi();
    }

    private void cleanup() {
        try {
            if (agent != null) {
                agent.close();
            }
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception e) {
            log.debug("Cleanup error", e);
        }
    }
}
