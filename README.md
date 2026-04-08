# MineCode

**MineCode** 是一个极简的 AI 代码代理，使用 Java 21 实现。项目灵感来自 [NanoCoder](https://github.com/he-yufeng/NanoCoder)（Claude Code 设计模式分析），将核心设计提炼为一个可读、可学习的代码代理框架。

## 核心理念

- **教育目的** - 展示代码代理的核心 ReAct 循环架构
- **极简设计** - 只保留核心功能，代码清晰易懂
- **可扩展** - 提供清晰的工具扩展接口

---

## 核心设计：ReAct 循环

```
用户消息 -> LLM (带工具) -> 工具调用? -> 执行 -> 循环
                      -> 文本响应? -> 返回用户
```

**核心代码**：

```java
public String chat(String userInput) throws IOException {
    messages.add(userMessage);

    for (int round = 0; round < maxRounds; round++) {
        LLMResponse response = llm.chat(messages, tools);

        // 无工具调用 -> LLM 认为任务完成
        if (!response.hasToolCalls()) {
            return response.content();
        }

        // 执行工具并继续循环
        for (ToolCall tc : response.toolCalls()) {
            String result = executeTool(tc);
            addToolResult(tc, result);
        }
    }
    return "(达到最大轮数)";
}
```

---

## 快速开始

### 环境要求

- Java 21+
- Maven 3.6+

### 配置

```bash
# OpenAI
export OPENAI_API_KEY=sk-...

# DeepSeek
export OPENAI_API_KEY=sk-... OPENAI_BASE_URL=https://api.deepseek.com/v1

# 其他 OpenAI 兼容 API
export OPENAI_API_KEY=sk-... OPENAI_BASE_URL=https://your-api-endpoint/v1
```

### 运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/minecode-1.0.0-SNAPSHOT.jar

# 指定模型
java -jar target/minecode-1.0.0-SNAPSHOT.jar -m gpt-4o
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

        // 2. 创建 Agent（注册工具）
        Agent agent = Agent.builder()
            .llm(llm)
            .tool(new ReadFileTool())
            .tool(new WriteFileTool())
            .tool(new EditFileTool())
            .tool(new BashTool())
            .tool(new GlobTool())
            .tool(new GrepTool())
            .maxRounds(30)
            .build();

        // 3. 执行对话
        String response = agent.chat("帮我写一个 Hello World 程序");
        System.out.println(response);
    }
}
```

---

## 内置工具（6个）

| 工具 | 功能 |
|-----|------|
| `read_file` | 文件读取（带行号） |
| `write_file` | 文件写入 |
| `edit_file` | 搜索替换编辑（Claude Code 核心创新） |
| `bash` | Shell 命令执行 |
| `glob` | 文件模式搜索 |
| `grep` | 内容搜索 |

### 核心创新：搜索替换编辑

传统文件编辑方式的问题：

| 方式 | 问题 |
|------|------|
| 整体重写 | Token 消耗大，不精确 |
| 行号编辑 | 行号容易变化，不稳定 |
| diff 补丁 | 解析复杂，容易出错 |

**Claude Code 创新**：LLM 指定一个精确的子字符串及其替换，子字符串必须在文件中唯一出现。

```java
// edit_file 工具核心逻辑
int occurrences = countOccurrences(content, oldString);

if (occurrences == 0) return "Error: not found";
if (occurrences > 1) return "Error: not unique";

content = content.replace(oldString, newString);
return generateDiff(oldContent, newContent);
```

---

## 项目结构

```
MineCode/
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
│       ├── Tool.java               # 工具基类
│       └── impl/                   # 工具实现（6个）
└── docs/
    └── ARCHITECTURE.md             # 架构设计文档
```

---

## 扩展指南

### 添加新工具

```java
public class MyTool extends Tool {
    public MyTool() {
        super("my_tool", "工具描述", buildParameters());
    }

    private static ObjectNode buildParameters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");
        // ... 定义参数 schema
        return params;
    }

    @Override
    public String execute(JsonNode arguments) {
        // 实现工具逻辑
        return "结果";
    }
}

// 注册到 Agent
Agent agent = Agent.builder()
    .llm(llm)
    .tool(new MyTool())
    .build();
```

---

## 技术选型

| 组件 | 技术 | 原因 |
|-----|------|------|
| HTTP Client | Java HttpClient | JDK 内置，支持 SSE |
| JSON | Jackson | 生态成熟 |
| CLI | Picocli + JLine | REPL 支持 |

---

## 设计文档

详细的设计思路和架构分析请参阅 [ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 参考资料

- [NanoCoder - GitHub](https://github.com/he-yufeng/NanoCoder) - Claude Code 极简实现

---

## License

MIT License
