package cn.abelib.minecode.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * 权限管理器
 *
 * <p>负责管理权限规则并做出权限决策。
 *
 * <p>决策流程：
 * <ol>
 *   <li>按顺序检查所有规则</li>
 *   <li>第一个匹配的规则决定结果</li>
 *   <li>如果没有匹配的规则，使用默认策略</li>
 * </ol>
 *
 * <p>使用示例：
 * <pre>{@code
 * PermissionManager manager = PermissionManager.builder()
 *     .defaultDecision(PermissionDecision.ASK_USER)
 *     .allow("Read")
 *     .allow("Glob")
 *     .allow("Grep")
 *     .deny("Bash(rm -rf:*)")
 *     .askUser("Bash(sudo:*)")
 *     .askUser("Write(/etc/*)")
 *     .build();
 *
 * // 检查权限
 * PermissionDecision decision = manager.check("Bash", "ls -la");
 * if (decision == PermissionDecision.ALLOW) {
 *     // 执行操作
 * } else if (decision == PermissionDecision.ASK_USER) {
 *     // 请求用户确认
 * }
 * }</pre>
 *
 * @author Abel
 */
public class PermissionManager {
    private static final Logger log = LoggerFactory.getLogger(PermissionManager.class);

    private final List<PermissionRule> rules;
    private final PermissionDecision defaultDecision;
    private final BiFunction<String, String, PermissionDecision> userConfirmHandler;

    public PermissionManager() {
        this(PermissionDecision.ASK_USER, null);
    }

    public PermissionManager(PermissionDecision defaultDecision,
                             BiFunction<String, String, PermissionDecision> userConfirmHandler) {
        this.rules = new CopyOnWriteArrayList<>();
        this.defaultDecision = defaultDecision;
        this.userConfirmHandler = userConfirmHandler;
    }

    /**
     * 添加允许规则
     */
    public PermissionManager allow(String pattern) {
        rules.add(PermissionRule.allow(pattern));
        return this;
    }

    /**
     * 添加拒绝规则
     */
    public PermissionManager deny(String pattern) {
        rules.add(PermissionRule.deny(pattern));
        return this;
    }

    /**
     * 添加需确认规则
     */
    public PermissionManager askUser(String pattern) {
        rules.add(PermissionRule.askUser(pattern));
        return this;
    }

    /**
     * 添加规则
     */
    public PermissionManager addRule(PermissionRule rule) {
        rules.add(rule);
        return this;
    }

    /**
     * 添加多个规则
     */
    public PermissionManager addRules(List<PermissionRule> newRules) {
        rules.addAll(newRules);
        return this;
    }

    /**
     * 移除规则
     */
    public PermissionManager removeRule(PermissionRule rule) {
        rules.remove(rule);
        return this;
    }

    /**
     * 清空所有规则
     */
    public void clearRules() {
        rules.clear();
    }

    /**
     * 获取所有规则
     */
    public List<PermissionRule> getRules() {
        return new ArrayList<>(rules);
    }

    /**
     * 获取默认决策
     */
    public PermissionDecision getDefaultDecision() {
        return defaultDecision;
    }

    /**
     * 检查权限（无参数）
     */
    public PermissionDecision check(String toolName) {
        return check(toolName, null);
    }

    /**
     * 检查权限
     *
     * @param toolName 工具名称
     * @param toolArgs 工具参数
     * @return 权限决策
     */
    public PermissionDecision check(String toolName, String toolArgs) {
        log.debug("Checking permission for {}({})", toolName, toolArgs);

        for (PermissionRule rule : rules) {
            if (rule.matches(toolName, toolArgs)) {
                log.debug("Rule matched: {} -> {}", rule.getPattern(), rule.getDecision());
                return rule.getDecision();
            }
        }

        log.debug("No matching rule, using default: {}", defaultDecision);
        return defaultDecision;
    }

    /**
     * 检查权限并请求用户确认
     *
     * <p>如果决策为 ASK_USER 且设置了 userConfirmHandler，则调用处理器获取最终决策。
     *
     * @param toolName 工具名称
     * @param toolArgs 工具参数
     * @return 最终权限决策
     */
    public PermissionDecision checkWithConfirm(String toolName, String toolArgs) {
        PermissionDecision decision = check(toolName, toolArgs);

        if (decision == PermissionDecision.ASK_USER && userConfirmHandler != null) {
            decision = userConfirmHandler.apply(toolName, toolArgs);
            log.info("User confirmed {}({}): {}", toolName, toolArgs, decision);
        }

        return decision;
    }

    /**
     * 判断是否允许执行
     */
    public boolean isAllowed(String toolName, String toolArgs) {
        PermissionDecision decision = checkWithConfirm(toolName, toolArgs);
        return decision == PermissionDecision.ALLOW;
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder 类
     */
    public static class Builder {
        private final List<PermissionRule> rules = new ArrayList<>();
        private PermissionDecision defaultDecision = PermissionDecision.ASK_USER;
        private BiFunction<String, String, PermissionDecision> userConfirmHandler;

        public Builder defaultDecision(PermissionDecision decision) {
            this.defaultDecision = decision;
            return this;
        }

        public Builder userConfirmHandler(BiFunction<String, String, PermissionDecision> handler) {
            this.userConfirmHandler = handler;
            return this;
        }

        public Builder allow(String pattern) {
            rules.add(PermissionRule.allow(pattern));
            return this;
        }

        public Builder deny(String pattern) {
            rules.add(PermissionRule.deny(pattern));
            return this;
        }

        public Builder askUser(String pattern) {
            rules.add(PermissionRule.askUser(pattern));
            return this;
        }

        public Builder rule(PermissionRule rule) {
            rules.add(rule);
            return this;
        }

        public Builder rules(List<PermissionRule> rules) {
            this.rules.addAll(rules);
            return this;
        }

        /**
         * 应用预设权限配置
         */
        public Builder preset(PermissionPreset preset) {
            switch (preset) {
                case READ_ONLY -> {
                    allow("read_file");
                    allow("glob");
                    allow("grep");
                    deny("write_file");
                    deny("edit_file");
                    deny("bash");
                }
                case STANDARD -> {
                    allow("read_file");
                    allow("glob");
                    allow("grep");
                    allow("write_file");
                    allow("edit_file");
                    askUser("bash(rm *)");
                    askUser("bash(sudo *)");
                }
                case FULL_ACCESS -> {
                    allow("read_file");
                    allow("glob");
                    allow("grep");
                    allow("write_file");
                    allow("edit_file");
                    allow("bash");
                }
                case SAFE_EDIT -> {
                    allow("read_file");
                    allow("glob");
                    allow("grep");
                    allow("edit_file");
                    deny("write_file(/etc/*)");
                    deny("write_file(/usr/*)");
                    deny("bash(rm -rf *)");
                    askUser("bash(sudo *)");
                }
            }
            return this;
        }

        public PermissionManager build() {
            PermissionManager manager = new PermissionManager(defaultDecision, userConfirmHandler);
            manager.addRules(rules);
            return manager;
        }
    }
}
