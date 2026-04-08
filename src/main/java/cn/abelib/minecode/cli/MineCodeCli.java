package cn.abelib.minecode.cli;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.tools.Tool;
import cn.abelib.minecode.tools.impl.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.jline.reader.*;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MineCode 命令行界面 - 最简版本
 *
 * <p>功能：
 * <ul>
 *   <li>交互式 REPL</li>
 *   <li>流式输出显示</li>
 * </ul>
 *
 * @author Abel
 */
@picocli.CommandLine.Command(name = "minecode", mixinStandardHelpOptions = true, version = "MineCode 1.0.0",
        description = "Minimal AI coding agent in Java")
public class MineCodeCli implements Runnable {

    private static final AttributedStyle STYLE_CYAN = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
    private static final AttributedStyle STYLE_YELLOW = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle STYLE_BLUE = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE);
    private static final AttributedStyle STYLE_BOLD_CYAN = STYLE_CYAN.bold();

    @picocli.CommandLine.Option(names = {"-m", "--model"}, description = "LLM model to use")
    private String model;

    @picocli.CommandLine.Option(names = {"--base-url"}, description = "API base URL")
    private String baseUrl;

    private LLMClient llmClient;
    private Agent agent;
    private Terminal terminal;
    private LineReader reader;
    private Path workingDirectory;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MineCodeCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            initialize();
            printWelcome();
            runRepl();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void initialize() throws IOException {
        workingDirectory = Path.of("").toAbsolutePath();

        // 从环境变量读取配置
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: OPENAI_API_KEY environment variable not set.");
            System.err.println("Please set it using: export OPENAI_API_KEY=your-api-key");
            System.exit(1);
        }

        // 构建 LLM 客户端
        LLMClient.Builder llmBuilder = LLMClient.builder()
                .apiKey(apiKey);

        if (model != null) {
            llmBuilder.model(model);
        } else {
            llmBuilder.fromEnv();
        }

        if (baseUrl != null) {
            llmBuilder.baseUrl(baseUrl);
        }

        llmClient = llmBuilder.build();

        // 构建 Agent（注册核心工具）
        Agent.Builder agentBuilder = Agent.builder()
                .llm(llmClient)
                .tool(new ReadFileTool())
                .tool(new WriteFileTool())
                .tool(new EditFileTool())
                .tool(new BashTool())
                .tool(new GlobTool())
                .tool(new GrepTool());

        agent = agentBuilder.build();

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

                line = line.trim();

                // 内置命令
                if (line.equals("/exit") || line.equals("/quit") || line.equals("/q")) {
                    System.out.println("Goodbye!");
                    break;
                }

                if (line.equals("/clear")) {
                    agent.reset();
                    System.out.println(color(STYLE_CYAN, "对话已清空"));
                    continue;
                }

                if (line.equals("/help")) {
                    printHelp();
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
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void processUserMessage(String input) {
        System.out.println();

        AtomicBoolean firstToken = new AtomicBoolean(true);

        try {
            String result = agent.chat(input, token -> {
                if (firstToken.get()) {
                    System.out.print(color(STYLE_YELLOW, "Assistant: "));
                    firstToken.set(false);
                }
                System.out.print(token);
                System.out.flush();
            });

            System.out.println();
            System.out.println();

        } catch (IOException e) {
            System.out.println(color(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED), "Error: " + e.getMessage()));
        }
    }

    private void printHelp() {
        System.out.println();
        System.out.println(color(STYLE_BOLD_CYAN, "MineCode - 极简 AI 代码代理"));
        System.out.println();
        System.out.println("命令:");
        System.out.println("  /help    - 显示帮助");
        System.out.println("  /clear   - 清空对话");
        System.out.println("  /exit    - 退出程序");
        System.out.println();
        System.out.println("可用工具: read_file, write_file, edit_file, bash, glob, grep");
        System.out.println();
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
        System.out.println(color(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT | AttributedStyle.BLACK),
                "  Minimal AI coding agent | Model: " + llmClient.getModel()));
        System.out.println(color(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT | AttributedStyle.BLACK),
                "  Type /help for commands, Ctrl+D to exit"));
        System.out.println();
    }

    private String color(AttributedStyle style, String text) {
        return new AttributedString(text, style).toAnsi();
    }

    private void cleanup() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
