package cn.abelib.minecode.skill;

import cn.abelib.minecode.agent.Agent;
import cn.abelib.minecode.config.MineCodeConfig;
import cn.abelib.minecode.llm.LLMClient;
import cn.abelib.minecode.session.SessionManager;
import org.jline.terminal.Terminal;

import java.nio.file.Path;

/**
 * 技能执行上下文
 *
 * <p>包含技能执行所需的所有依赖：
 * <ul>
 *   <li>Agent - LLM 代理</li>
 *   <li>LLMClient - LLM 客户端</li>
 *   <li>SessionManager - 会话管理器</li>
 *   <li>Config - 配置</li>
 *   <li>Terminal - 终端</li>
 * </ul>
 *
 * @author Abel
 */
public class SkillContext {

    private final Agent agent;
    private final LLMClient llmClient;
    private final SessionManager sessionManager;
    private final MineCodeConfig config;
    private final Terminal terminal;
    private final Path workingDirectory;

    public SkillContext(Agent agent, LLMClient llmClient, SessionManager sessionManager,
                        MineCodeConfig config, Terminal terminal, Path workingDirectory) {
        this.agent = agent;
        this.llmClient = llmClient;
        this.sessionManager = sessionManager;
        this.config = config;
        this.terminal = terminal;
        this.workingDirectory = workingDirectory;
    }

    public Agent getAgent() {
        return agent;
    }

    public LLMClient getLlmClient() {
        return llmClient;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public MineCodeConfig getConfig() {
        return config;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * 获取模型名称
     */
    public String getModel() {
        return config.get(MineCodeConfig.MODEL);
    }

    /**
     * 打印到终端
     */
    public void print(String message) {
        if (terminal != null) {
            terminal.writer().println(message);
            terminal.writer().flush();
        } else {
            System.out.println(message);
        }
    }

    /**
     * 打印错误
     */
    public void printError(String message) {
        System.err.println("错误: " + message);
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Agent agent;
        private LLMClient llmClient;
        private SessionManager sessionManager;
        private MineCodeConfig config;
        private Terminal terminal;
        private Path workingDirectory;

        public Builder agent(Agent agent) {
            this.agent = agent;
            return this;
        }

        public Builder llmClient(LLMClient llmClient) {
            this.llmClient = llmClient;
            return this;
        }

        public Builder sessionManager(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        public Builder config(MineCodeConfig config) {
            this.config = config;
            return this;
        }

        public Builder terminal(Terminal terminal) {
            this.terminal = terminal;
            return this;
        }

        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public SkillContext build() {
            return new SkillContext(agent, llmClient, sessionManager, config, terminal, workingDirectory);
        }
    }
}
