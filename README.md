# MineCode

**MineCode** 是一个极简的 AI 代码生成代理，使用 Java 17 实现。项目灵感来自 [NanoCoder](https://github.com/he-yufeng/NanoCoder)（基于 Claude Code 设计）和 [AgentScope](https://github.com/alibaba/AgentScope) 的架构思想，将其核心设计模式提炼为一个可读、可学习、可扩展的代码代理框架。

## 核心理念

- **教育目的** - 展示代码代理的核心架构模式
- **可扩展性** - 提供清晰的工具和 Hook 扩展接口
- **生产可用** - 支持多种 LLM 后端

---

## 快速开始

### 环境要求

- Java 17+
- Maven 3.6+

### 配置

```bash
# OpenAI
export OPENAI_API_KEY=sk-...

# DeepSeek
export OPENAI_API_KEY=sk-... OPENAI_BASE_URL=https://api.deepseek.com

# Anthropic Claude
export ANTHROPIC_API_KEY=sk-...

# Ollama (本地)
export OPENAI_API_KEY=ollama OPENAI_BASE_URL=http://localhost:11434/v1
```

### 运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/minecode-1.0.0.jar

# 或使用 Maven
mvn exec:java -Dexec.mainClass="cn.abelib.minecode.cli.MineCodeCli"
```

### 代码示例

```java
public class Example {
    public static void main(String[] args) throws Exception {
        // 1. 创建 LLM 客户端
        LLMClient llm = LLMClient.builder()
            .fromEnv()
            .model("gpt-4o")
            .build();

        // 2. 创建 Agent
        Agent agent = Agent.builder()
            .llm(llm)
            .hook(new BuiltinHooks.LoggingHook(true))
            .hook(new BuiltinHooks.TokenStatsHook())
            .hook(new BuiltinHooks.LoopDetectionHook())
            .maxRounds(30)
            .build();

        // 3. 执行对话
        String response = agent.chat("帮我写一个 Hello World 程序");
        System.out.println(response);

        // 4. 关闭资源
        agent.close();
    }
}
```

---

## 功能完成度

| 模块 | 状态 | 说明 |
|------|------|------|
| Agent 核心 | ✅ | ReAct 循环、并行执行、中断控制 |
| LLM 客户端 | ✅ | 流式响应、多提供商支持 |
| 工具系统 | ✅ | 7 个内置工具 + @Tool 注解 |
| 上下文管理 | ✅ | 3 层压缩 |
| Hook 系统 | ✅ | 9 种事件、6 个内置 Hook、Sealed Class |
| 会话管理 | ✅ | JSONL 增量保存、多用户支持 |
| 任务规划 | ✅ | PlanNotebook、LLM 自动分解 |
| 工具组管理 | ✅ | 动态激活/禁用、预设模式 |
| 技能系统 | ✅ | 13 个内置技能 |
| 权限系统 | ✅ | 规则匹配、用户确认 |
| 错误重试 | ✅ | 智能错误分类、指数退避 |
| CLI | ✅ | REPL、命令解析 |

---

## LLM 提供商支持

| 提供商 | 类 | 说明 |
|--------|-----|------|
| OpenAI 兼容 | `OpenAIProvider` | OpenAI、DeepSeek、Qwen、Kimi 等 |
| Anthropic | `AnthropicProvider` | Claude 系列 |
| Ollama | `OllamaProvider` | 本地模型 |

```java
// OpenAI
LLMClient client = LLMClient.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-4o")
    .build();

// Anthropic Claude
LLMClient client = LLMClient.builder()
    .provider("anthropic")
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .model("claude-3-5-sonnet-20241022")
    .build();

// 自定义提供商
LLMClient client = new LLMClient(new MyCustomProvider(config));
```

---

## 内置组件

### 工具（7个）

| 工具 | 功能 |
|-----|------|
| `bash` | Shell 命令执行 |
| `read_file` | 文件读取 |
| `write_file` | 文件写入 |
| `edit_file` | 搜索替换编辑 |
| `glob` | 文件模式搜索 |
| `grep` | 内容搜索 |
| `agent` | 子代理派生 |

### Hook（6个）

| Hook | 功能 |
|------|------|
| `LoggingHook` | 执行日志 |
| `TokenStatsHook` | Token 统计 |
| `TimingHook` | 执行时间统计 |
| `HumanInTheLoopHook` | 人工介入确认 |
| `LoopDetectionHook` | 无限循环检测 |
| `RetryHook` | 错误自动重试 |

### 技能（13个）

| 技能 | 用法 |
|------|------|
| `/help` | 显示帮助 |
| `/commit` | Git 提交 |
| `/review` | 代码审查 |
| `/explain` | 解释代码 |
| `/refactor` | 重构建议 |
| `/test` | 生成测试 |
| `/tokens` | Token 统计 |
| `/clear` | 清空对话 |
| `/save` | 保存会话 |
| `/load` | 加载会话 |
| `/sessions` | 列出会话 |
| `/config` | 显示配置 |
| `/new` | 新建会话 |

---

## 扩展指南

### 添加新工具

```java
// 方式一：继承 Tool 基类
public class MyTool extends Tool {
    public MyTool() {
        super("my_tool", "工具描述", buildParameters());
    }

    @Override
    public String execute(JsonNode arguments) {
        return "结果";
    }
}

// 方式二：使用 @Tool 注解
public class MyTools {
    @Tool(name = "my_tool", description = "工具描述")
    public String myMethod(
        @ToolParam(name = "arg1", description = "参数1") String arg1) {
        return "结果";
    }
}
```

### 添加自定义 Hook

```java
Hook hook = HookBuilder.custom("MyHook", event -> {
    if (event instanceof PreActingEvent e) {
        log.info("执行工具: {}", e.getToolCall().name());
    }
});

// 或实现 Hook 接口（使用 switch 模式匹配）
public class MyHook implements Hook {
    @Override
    public HookEvent onEvent(HookEvent event) {
        return switch (event) {
            case PreActingEvent e -> { /* ... */ yield e; }
            case PostActingEvent e -> { /* ... */ yield e; }
            default -> event;
        };
    }
}
```

### 添加自定义技能

```java
public class MySkill implements Skill {
    @Override
    public String name() { return "my"; }

    @Override
    public String description() { return "自定义技能"; }

    @Override
    public String execute(SkillContext context, String args) {
        return "执行结果";
    }
}
```

---

## 代码统计

| 指标 | 数值 |
|------|------|
| Java 源文件 | 85 个 |
| 主代码 | 14,155 行 |
| 测试代码 | 2,200 行 |

---

## 技术选型

| 组件 | 技术 | 原因 |
|-----|------|------|
| HTTP Client | Java HttpClient | JDK 内置，支持 SSE |
| JSON | Jackson | 生态成熟 |
| CLI | Picocli + JLine | REPL 支持 |
| 日志 | SLF4J + Logback | 行业标准 |
| 并发 | CompletableFuture | 并行工具执行 |
| Java 版本 | Java 17 | sealed class、switch 模式匹配 |

---

## 设计文档

详细的设计思路和架构分析请参阅 [ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 参考资料

- [NanoCoder - GitHub](https://github.com/he-yufeng/NanoCoder) - Claude Code 极简实现
- [AgentScope-Java](https://github.com/alibaba/AgentScope) - 阿里巴巴多智能体框架
- [Claude Code 源码分析](https://zhuanlan.zhihu.com/p/1898797658343862272)

---

## License

MIT License
