# MineCode 架构设计思路

本文档详细说明 MineCode 的设计来源和核心架构模式，帮助理解代码代理的设计原理。

---

## 一、项目概览

### 1.1 设计来源

MineCode 的核心设计来自 [NanoCoder](https://github.com/he-yufeng/NanoCoder) 对 Claude Code 的分析，将代码代理精简为最核心的 ReAct 循环。

### 1.2 代码统计

| 模块 | 文件数 | 说明 |
|------|--------|------|
| agent | 1 | 核心 ReAct 循环 |
| llm | 3 | OpenAI 兼容客户端 |
| tools | 7 | 工具抽象 + 6 个实现 |
| cli | 1 | 交互式 REPL |
| **总计** | **12** | ~1,200 行核心代码 |

### 1.3 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                         CLI Layer                           │
│                    (MineCodeCli.java)                       │
│                      REPL 交互                               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                        Agent Layer                          │
│                      (Agent.java)                           │
│                   核心 ReAct 循环                            │
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┴───────────────────┐
          ▼                                       ▼
┌──────────────────┐                    ┌──────────────────┐
│    LLM Layer     │                    │   Tool Layer     │
│  (LLMClient)     │                    │   (Tool)         │
│  OpenAI 兼容     │                    │   工具抽象        │
│  流式响应        │                    │   6 个内置工具    │
└──────────────────┘                    └──────────────────┘
```

---

## 二、核心设计：ReAct 循环

### 2.1 设计理念

**ReAct（Reasoning + Acting）** 是代码代理的核心模式：持续对话直到 LLM 自主决定完成任务。

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

### 2.2 Java 实现

核心代码仅 20 行：

```java
public String chat(String userInput) throws IOException {
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

**关键点**：
- LLM 返回工具调用时，执行工具并将结果加入对话历史
- LLM 返回纯文本时，认为任务完成
- 设置最大轮数防止无限循环

---

## 三、工具系统

### 3.1 工具抽象

```java
public abstract class Tool {
    protected final String name;         // 工具名称
    protected final String description;  // 工具描述
    protected final ObjectNode parameters;  // JSON Schema 参数定义

    public abstract String execute(JsonNode arguments);
    public ObjectNode toSchema();  // 生成 OpenAI Function Calling 格式
}
```

### 3.2 内置工具（6个）

| 工具 | 功能 | 关键参数 |
|-----|------|---------|
| `read_file` | 文件读取 | `file_path`, `offset`, `limit` |
| `write_file` | 文件写入 | `file_path`, `content` |
| `edit_file` | 搜索替换编辑 | `file_path`, `old_string`, `new_string` |
| `bash` | Shell 命令 | `command` |
| `glob` | 文件模式搜索 | `pattern` |
| `grep` | 内容搜索 | `pattern`, `path` |

### 3.3 核心创新：搜索替换编辑

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

**优势**：
- 精确：不会误改其他位置
- 安全：唯一性检查防止歧义
- 可审计：生成 diff 展示变更

---

## 四、LLM 客户端

### 4.1 设计要点

- **OpenAI 兼容**：支持 OpenAI、DeepSeek、Qwen、Kimi 等所有兼容 API
- **流式响应**：使用 SSE（Server-Sent Events）实时输出
- **Function Calling**：支持工具调用

### 4.2 核心实现

```java
public LLMResponse chat(List<ObjectNode> messages, List<ObjectNode> tools,
                        Consumer<String> onToken) throws IOException {
    ObjectNode requestBody = buildRequestBody(messages, tools);

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
            .build();

    return executeStreaming(request, onToken);
}
```

### 4.3 流式解析

```java
// 解析 SSE 数据
response.body().forEach(line -> {
    String data = line.startsWith("data: ") ? line.substring(6) : line;

    if ("[DONE]".equals(data)) {
        future.complete(new LLMResponse(content, toolCalls, promptTokens, completionTokens));
        return;
    }

    JsonNode chunk = mapper.readTree(data);
    // 处理文本内容
    String text = chunk.path("choices").get(0).path("delta").path("content").asText();
    content.append(text);
    if (onToken != null) onToken.accept(text);
});
```

---

## 五、项目结构

```
MineCode/
├── pom.xml
├── src/main/java/cn/abelib/minecode/
│   ├── cli/
│   │   └── MineCodeCli.java        # CLI 入口
│   ├── agent/
│   │   └── Agent.java              # 核心 ReAct 循环
│   ├── llm/
│   │   ├── LLMClient.java          # OpenAI 兼容客户端
│   │   ├── LLMResponse.java        # 响应封装
│   │   └── ToolCall.java           # 工具调用
│   └── tools/
│       ├── Tool.java               # 工具抽象基类
│       └── impl/
│           ├── ReadFileTool.java   # 文件读取
│           ├── WriteFileTool.java  # 文件写入
│           ├── EditFileTool.java   # 搜索替换编辑
│           ├── BashTool.java       # Shell 命令
│           ├── GlobTool.java       # 文件搜索
│           └── GrepTool.java       # 内容搜索
└── docs/
    └── ARCHITECTURE.md
```

---

## 六、扩展指南

### 6.1 添加新工具

```java
public class MyTool extends Tool {
    public MyTool() {
        super("my_tool", "工具描述", buildParameters());
    }

    private static ObjectNode buildParameters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();

        ObjectNode arg1 = mapper.createObjectNode();
        arg1.put("type", "string");
        arg1.put("description", "参数说明");
        properties.set("arg1", arg1);

        params.set("properties", properties);
        return params;
    }

    @Override
    public String execute(JsonNode arguments) {
        String arg1 = arguments.path("arg1").asText();
        return "执行结果";
    }
}

// 注册到 Agent
Agent agent = Agent.builder()
    .llm(llm)
    .tool(new MyTool())
    .build();
```

---

## 七、技术选型

| 组件 | 技术 | 原因 |
|-----|------|------|
| HTTP Client | Java HttpClient | JDK 内置，支持 SSE |
| JSON | Jackson | 生态成熟 |
| CLI | Picocli + JLine | REPL 支持 |
| Java 版本 | Java 21 | Record、switch 模式匹配 |

---

## 八、最佳实践

1. **先读后改** - 修改文件前总是先读取
2. **小改动用 edit_file** - 只有小改动才用搜索替换
3. **验证工作** - 修改后运行测试确认
4. **保持简洁** - 代码胜于文字
5. **尊重风格** - 匹配项目现有代码风格

---

## 参考资料

- [NanoCoder - GitHub](https://github.com/he-yufeng/NanoCoder) - Claude Code 极简实现
- [OpenAI Function Calling](https://platform.openai.com/docs/guides/function-calling)
