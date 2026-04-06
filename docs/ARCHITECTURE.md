# MineCode 架构设计思路

本文档详细说明 MineCode 的设计来源和核心架构模式，帮助理解代码代理的设计原理。

---

## 一、项目概览

### 1.1 设计来源

MineCode 整合了两个主要设计来源：

| 来源 | 类型 | 核心贡献 |
|------|------|---------|
| [Claude Code](https://github.com/he-yufeng/NanoCoder) | 📖 NanoCoder 分析 | Agent 循环、工具系统、上下文压缩 |
| [AgentScope](https://github.com/alibaba/AgentScope) | 🔧 阿里巴巴框架 | Hook 系统、Builder 模式、任务规划 |

### 1.2 代码统计

| 模块 | 文件数 | 代码行数 |
|------|--------|---------|
| agent | 3 | 1,380 |
| llm | 8 | 1,790 |
| tools | 17 | 2,444 |
| hook | 18 | 3,356 |
| session | 8 | 1,366 |
| plan | 4 | 818 |
| skill | 16 | 1,131 |
| permission | 7 | 901 |
| cli | 1 | 422 |
| context | 1 | 290 |
| prompt | 1 | 65 |
| config | 1 | 192 |
| **总计** | **85** | **14,155** |

测试代码：2,200 行

### 1.3 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                         CLI Layer                           │
│                    (MineCodeCli.java)                       │
│                   命令解析 / REPL 交互 / 技能调用              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        Agent Layer                          │
│                      (Agent.java)                           │
│              核心代理循环 / 并行工具执行 / Hook 拦截           │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│    LLM Layer     │ │  Context Layer   │ │   Tool Layer     │
│  (LLMClient)     │ │ (ContextManager) │ │   (Tool)         │
│  多提供商支持     │ │  3层压缩         │ │   工具抽象        │
│  流式响应        │ │  Token估算       │ │   工具注册        │
└──────────────────┘ └──────────────────┘ └──────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Supporting Systems                       │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────┐ │
│  │   Hook     │ │  Session   │ │   Plan     │ │Permission│ │
│  │   System   │ │   Manager  │ │  Notebook  │ │  System  │ │
│  └────────────┘ └────────────┘ └────────────┘ └──────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、Claude Code 设计思路（来自 NanoCoder）

Claude Code 是 Anthropic 官方的 AI 编程助手。NanoCoder 项目将其精简为核心设计模式，MineCode 在此基础上用 Java 实现。

### 2.1 核心架构：ReAct 循环

**设计理念**：持续对话直到 LLM 自主决定完成任务。

```
┌─────────────────────────────────────────────────────────────────┐
│                     ReAct 循环                                  │
│                                                                 │
│    用户消息                                                      │
│        │                                                        │
│        ▼                                                        │
│    ┌─────────────────────────────────────┐                      │
│    │           LLM 推理                   │                      │
│    │  (带工具定义)                        │                      │
│    └──────────────────┬──────────────────┘                      │
│                       │                                         │
│           ┌───────────┴───────────┐                            │
│           │                       │                             │
│           ▼                       ▼                             │
│    ┌────────────┐          ┌────────────┐                      │
│    │ 工具调用   │          │ 文本响应   │                      │
│    │ (ToolCall) │          │ (完成)     │                      │
│    └─────┬──────┘          └─────┬──────┘                      │
│          │                       │                              │
│          ▼                       │                              │
│    ┌────────────┐                │                              │
│    │ 执行工具   │                │                              │
│    │ 获取结果   │                │                              │
│    └─────┬──────┘                │                              │
│          │                       │                              │
│          └───────────────────────┘                              │
│                      │                                          │
│                      ▼                                          │
│               继续循环                                            │
└─────────────────────────────────────────────────────────────────┘
```

**Java 实现要点**：

```java
public String chat(String userInput) {
    messages.add(userMessage);

    for (int round = 0; round < maxRounds; round++) {
        LLMResponse response = llm.chat(messages, tools);

        // 无工具调用 -> LLM 认为任务完成
        if (!response.hasToolCalls()) {
            return response.content();
        }

        // 有工具调用 -> 执行并继续循环
        for (ToolCall tc : response.toolCalls()) {
            String result = executeTool(tc);
            addToolResult(tc, result);
        }
    }

    return "(达到最大轮数)";
}
```

### 2.2 工具系统

**抽象设计**：

```java
public abstract class Tool {
    protected final String name;
    protected final String description;
    protected final ObjectNode parameters;  // JSON Schema

    public abstract String execute(JsonNode arguments);
    public ObjectNode toSchema();  // OpenAI Function Calling 格式
}
```

**内置工具（7个）**：

| 工具 | 功能 |
|-----|------|
| `bash` | Shell 命令执行 + 安全检查 + cd 追踪 |
| `read_file` | 文件读取 |
| `write_file` | 文件写入 |
| `edit_file` | 搜索替换编辑（Claude Code 关键创新） |
| `glob` | 文件模式搜索 |
| `grep` | 内容搜索 |
| `agent` | 子代理派生 |

### 2.3 核心创新：搜索替换编辑

**问题**：传统文件编辑方式的缺陷

| 方式 | 问题 |
|------|------|
| 整体重写 | Token 消耗大，不精确 |
| 行号编辑 | 行号容易变化，不稳定 |
| diff 补丁 | 解析复杂，容易出错 |

**Claude Code 创新**：LLM 指定一个精确的子字符串及其替换，子字符串必须在文件中唯一出现。

```java
public String execute(JsonNode args) {
    String oldString = args.get("old_string").asText();
    String newString = args.get("new_string").asText();

    int occurrences = countOccurrences(content, oldString);

    // 唯一性校验：必须精确匹配一处
    if (occurrences == 0) return "Error: not found";
    if (occurrences > 1) return "Error: not unique";

    content = content.replace(oldString, newString);
    return generateDiff(oldContent, newContent);
}
```

### 2.4 3层上下文压缩策略

当对话历史过长时，使用分层压缩：

```
┌─────────────────────────────────────────────────────────────────┐
│                      Context Manager                             │
│                                                                 │
│  Token 使用: 0-50%                                              │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Layer 1: Tool Snip                                      │   │
│  │ 裁剪冗长工具输出为首尾各 3 行                              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                         ▼                                       │
│  Token 使用: 50-70%                                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Layer 2: Summarize                                       │   │
│  │ LLM 驱动的旧对话摘要                                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                         ▼                                       │
│  Token 使用: 70-90%                                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Layer 3: Hard Collapse                                   │   │
│  │ 强制折叠：保留摘要 + 最近 4 条消息                         │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、AgentScope 设计思路

AgentScope 是阿里巴巴开源的多智能体框架，提供了丰富的架构模式。

### 3.1 Hook 系统

**设计目标**：在 Agent 执行过程中进行拦截和扩展。

**事件类型（9个具体事件类）**：

| 事件类 | 触发时机 | 主要字段 |
|--------|---------|---------|
| `PreCallEvent` | 用户调用前 | `userInput` |
| `PostCallEvent` | 调用完成后 | `response`, `roundsUsed` |
| `PreReasoningEvent` | LLM 推理前 | `messages` |
| `PostReasoningEvent` | LLM 返回后 | `content`, `toolCalls`, `tokens` |
| `ReasoningChunkEvent` | 流式 Token | `token` |
| `PreActingEvent` | 工具执行前 | `toolCall` |
| `PostActingEvent` | 工具执行后 | `toolCall`, `result`, `durationMs` |
| `ErrorEvent` | 发生错误时 | `error`, `phase` |
| `InterruptedEvent` | 执行中断时 | `reason`, `message`, `executionTimeMs` |

**事件流程**：

```
用户调用 agent.chat(input)
    │
    ▼
┌─────────────────┐
│  PreCallEvent   │ ← 可修改 userInput，可中断
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│PreReasoningEvent│ ← 可修改发送给 LLM 的消息
└────────┬────────┘
         │
         ▼
┌──────────────────┐
│PostReasoningEvent│ ← 支持 gotoReasoning 控制流
└────────┬─────────┘
         │
         ▼ (有工具调用？)
┌─────────────────┐
│ PreActingEvent  │ ← 可修改工具参数，可中断
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│PostActingEvent  │ ← 获取工具执行结果
└────────┬────────┘
         │
         ▼ (循环直到完成)
┌─────────────────┐
│  PostCallEvent  │ ← 获取最终响应
└─────────────────┘
```

**Sealed Class 类型安全**：

```java
// Java 17 sealed class 提供编译时类型检查
public abstract sealed class HookEvent
        permits PreCallEvent, PostCallEvent,
                   ReasoningEvent, ActingEvent,
                   ErrorEvent, InterruptedEvent {

    // 中间抽象层
    public abstract sealed class ReasoningEvent extends HookEvent
            permits PreReasoningEvent, PostReasoningEvent, ReasoningChunkEvent {}

    public abstract sealed class ActingEvent extends HookEvent
            permits PreActingEvent, PostActingEvent {}
}

// 使用 switch 模式匹配
HookEvent result = switch (event) {
    case PreReasoningEvent e -> handlePreReasoning(e);
    case PostReasoningEvent e -> handlePostReasoning(e);
    case PreActingEvent e -> handlePreActing(e);
    case ActingEvent e -> handleAnyActing(e);  // 匹配所有 Acting 事件
    case InterruptedEvent e -> handleInterrupt(e);
    default -> event;
};
```

**内置 Hook（6个）**：

| Hook | 功能 |
|------|------|
| `LoggingHook` | 执行日志 |
| `TokenStatsHook` | Token 统计 |
| `TimingHook` | 执行时间统计 |
| `HumanInTheLoopHook` | 人工介入确认 |
| `LoopDetectionHook` | 无限循环检测 |
| `RetryHook` | 错误自动重试 |

### 3.2 LLM 提供商抽象

**接口设计**：

```java
public interface LLMProvider {
    String getName();
    String getModel();
    LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools,
                     Consumer<String> onToken);
    long getTotalPromptTokens();
    long getTotalCompletionTokens();
}
```

**支持提供商（3个）**：

| 提供商 | 类 | 说明 |
|--------|-----|------|
| OpenAI 兼容 | `OpenAIProvider` | OpenAI、DeepSeek、Qwen、Kimi 等 |
| Anthropic | `AnthropicProvider` | Claude 系列 |
| Ollama | `OllamaProvider` | 本地模型 |

### 3.3 Builder 模式

```java
// Agent Builder
Agent agent = Agent.builder()
    .llm(llmClient)                    // 必需
    .name("CodeAssistant")             // 可选
    .maxContextTokens(128000)          // 可选
    .maxRounds(50)                     // 可选
    .hook(new LoggingHook())           // 添加 Hook
    .tool(new MyCustomTool())          // 添加工具
    .toolPreset(ToolPreset.STANDARD)   // 应用工具预设
    .build();

// LLMClient Builder
LLMClient client = LLMClient.builder()
    .provider("anthropic")             // 指定提供商
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .model("claude-3-5-sonnet-20241022")
    .build();
```

### 3.4 任务规划系统

**任务状态机**：

```
┌────────────┐     ┌─────────────┐     ┌────────────┐
│ PENDING    │────►│ IN_PROGRESS │────►│ COMPLETED  │
└────────────┘     └─────────────┘     └────────────┘
                         │
                         ▼ 失败
                  ┌────────────┐
                  │  FAILED    │
                  └────────────┘
```

**使用示例**：

```java
Agent agent = Agent.builder().llm(llm).build();
String result = agent.chatWithPlan("重构用户认证模块", new Agent.PlanCallback() {
    void onPlanCreated(List<Task> tasks) { /* 计划创建完成 */ }
    void onTaskStarted(Task task) { /* 任务开始 */ }
    void onTaskCompleted(Task task, String result) { /* 任务完成 */ }
    void onTaskFailed(Task task, Exception error) { /* 任务失败 */ }
});
```

### 3.5 工具组管理

将工具按功能分组，动态激活/禁用。

**预设模式**：

| 模式 | 包含工具 |
|------|---------|
| `READ_ONLY` | read_file, glob, grep |
| `STANDARD` | read/write/edit_file, glob, grep, agent |
| `SAFE_EDIT` | read/write/edit_file, glob, grep（无 bash） |
| `FULL_ACCESS` | 所有工具 |

### 3.6 会话管理

**核心特性**：

| 特性 | 说明 |
|------|------|
| 增量追加保存 | JSONL 格式，只追加新消息 |
| Session 抽象层 | 支持切换存储后端 |
| StateModule 接口 | 组件可注册状态保存 |
| SessionKey 复合键 | 支持 `userId + sessionId` 多用户 |

### 3.7 中断控制

**中断原因分类**：

| 原因 | 可恢复 |
|------|--------|
| `USER_CANCEL` | ✅ |
| `TIMEOUT` | ✅ |
| `ERROR` | ❌ |
| `EXTERNAL_SIGNAL` | ✅ |
| `HOOK_INTERRUPT` | ✅ |
| `RESOURCE_LIMIT` | ❌ |

### 3.8 错误重试

**智能错误分类**：

```java
public interface ErrorClassifier {
    RetryDecision classify(Throwable error, ToolCall toolCall, String result);
}

public enum RetryDecision {
    RETRY,              // 立即重试
    RETRY_WITH_BACKOFF, // 指数退避重试
    NO_RETRY,           // 不重试
    SKIP                // 跳过
}
```

---

## 四、项目结构

```
MineCode/
├── pom.xml
├── src/main/java/cn/abelib/minecode/
│   ├── cli/                        # 命令行接口
│   │   └── MineCodeCli.java        # REPL + 命令解析
│   ├── agent/                      # 代理核心
│   │   ├── Agent.java              # 代理循环
│   │   ├── InterruptContext.java   # 中断控制
│   │   └── InterruptReason.java    # 中断原因枚举
│   ├── llm/                        # LLM 层
│   │   ├── LLMClient.java          # 客户端门面
│   │   ├── LLMProvider.java        # 提供商接口
│   │   ├── LLMConfig.java          # 配置
│   │   ├── LLMResponse.java        # 响应
│   │   ├── ToolCall.java           # 工具调用
│   │   └── provider/               # 提供商实现
│   │       ├── OpenAIProvider.java
│   │       ├── AnthropicProvider.java
│   │       └── OllamaProvider.java
│   ├── context/                    # 上下文管理
│   │   └── ContextManager.java     # 3层压缩
│   ├── tools/                      # 工具系统
│   │   ├── Tool.java               # 抽象基类
│   │   ├── ToolRegistry.java       # 工具注册
│   │   ├── ToolGroup.java          # 工具组
│   │   ├── ToolGroupManager.java   # 工具组管理
│   │   ├── ToolPreset.java         # 预设模式
│   │   ├── ToolScanner.java        # 注解扫描器
│   │   ├── annotation/             # 注解定义
│   │   └── impl/                   # 具体实现（7个）
│   ├── hook/                       # Hook 系统
│   │   ├── Hook.java               # Hook 接口
│   │   ├── HookEvent.java          # 事件基类 (sealed)
│   │   ├── HookManager.java        # Hook 管理器
│   │   ├── HookBuilder.java        # Hook 构建器
│   │   ├── ErrorClassifier.java    # 错误分类器
│   │   ├── RetryContext.java       # 重试上下文
│   │   ├── BuiltinHooks.java       # 内置 Hook（6个）
│   │   ├── ReasoningEvent.java     # 推理事件基类 (sealed)
│   │   ├── ActingEvent.java        # 工具事件基类 (sealed)
│   │   └── *Event.java             # 具体事件类（9个）
│   ├── session/                    # 会话持久化
│   │   ├── Session.java            # 会话接口
│   │   ├── SessionKey.java         # 会话复合键
│   │   ├── SessionManager.java     # 会话管理器
│   │   ├── SessionStorage.java     # 存储抽象
│   │   ├── JsonlSessionStorage.java # JSONL 实现
│   │   └── StateModule.java        # 状态模块接口
│   ├── plan/                       # 任务规划
│   │   ├── PlanNotebook.java       # 核心类
│   │   ├── Task.java               # 任务模型
│   │   ├── TaskStatus.java         # 状态枚举
│   │   └── PlanHook.java           # 自动注入 Hook
│   ├── permission/                 # 权限系统
│   │   ├── PermissionManager.java  # 权限管理器
│   │   ├── PermissionRule.java     # 权限规则
│   │   ├── PermissionHook.java     # 权限 Hook
│   │   └── PermissionPreset.java   # 权限预设
│   ├── skill/                      # 技能系统
│   │   ├── Skill.java              # 技能接口
│   │   ├── SkillRegistry.java      # 技能注册器
│   │   ├── SkillContext.java       # 执行上下文
│   │   └── builtin/                # 内置技能（13个）
│   ├── prompt/                     # 提示生成
│   │   └── SystemPrompt.java
│   └── config/                     # 配置管理
│       └── MineCodeConfig.java
└── docs/
    └── ARCHITECTURE.md             # 本文档
```

---

## 五、技术选型

| 组件 | 技术 | 原因 |
|-----|------|------|
| HTTP Client | Java HttpClient | JDK 内置，支持 SSE |
| JSON | Jackson | 生态成熟 |
| CLI | Picocli + JLine | REPL 支持 |
| 日志 | SLF4J + Logback | 行业标准 |
| 并发 | CompletableFuture | 并行工具执行 |
| Java 版本 | Java 17 | sealed class、switch 模式匹配 |

---

## 六、最佳实践

1. **先读后改** - 修改文件前总是先读取
2. **小改动用 edit_file** - 只有小改动才用搜索替换
3. **验证工作** - 修改后运行测试确认
4. **保持简洁** - 代码胜于文字
5. **尊重风格** - 匹配项目现有代码风格

---

## 参考资料

- [NanoCoder - GitHub](https://github.com/he-yufeng/NanoCoder) - Claude Code 极简实现
- [AgentScope-Java](https://github.com/alibaba/AgentScope) - 阿里巴巴多智能体框架
- [Claude Code 源码分析](https://zhuanlan.zhihu.com/p/1898797658343862272)
- [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)
