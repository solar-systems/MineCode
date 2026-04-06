package cn.abelib.minecode.permission;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限配置加载器
 *
 * <p>从配置文件加载权限规则。
 *
 * <p>配置文件格式（JSON）：
 * <pre>{@code
 * {
 *   "defaultDecision": "ASK_USER",
 *   "rules": [
 *     {"pattern": "Read", "decision": "ALLOW"},
 *     {"pattern": "Bash(rm -rf:*)", "decision": "DENY"},
 *     {"pattern": "Bash(sudo:*)", "decision": "ASK_USER"}
 *   ]
 * }
 * }</pre>
 *
 * @author Abel
 */
public class PermissionConfig {

    private String defaultDecision = "ASK_USER";
    private List<RuleConfig> rules = new ArrayList<>();

    public String getDefaultDecision() {
        return defaultDecision;
    }

    public void setDefaultDecision(String defaultDecision) {
        this.defaultDecision = defaultDecision;
    }

    public List<RuleConfig> getRules() {
        return rules;
    }

    public void setRules(List<RuleConfig> rules) {
        this.rules = rules;
    }

    /**
     * 规则配置
     */
    public static class RuleConfig {
        private String pattern;
        private String decision;
        private String description;

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * 转换为 PermissionManager
     */
    public PermissionManager toPermissionManager() {
        PermissionManager.Builder builder = PermissionManager.builder()
                .defaultDecision(PermissionDecision.valueOf(defaultDecision));

        for (RuleConfig ruleConfig : rules) {
            PermissionDecision decision = PermissionDecision.valueOf(ruleConfig.getDecision());
            PermissionRule rule = switch (decision) {
                case ALLOW -> PermissionRule.allow(ruleConfig.getPattern(), ruleConfig.getDescription());
                case DENY -> PermissionRule.deny(ruleConfig.getPattern(), ruleConfig.getDescription());
                case ASK_USER -> PermissionRule.askUser(ruleConfig.getPattern(), ruleConfig.getDescription());
            };
            builder.rule(rule);
        }

        return builder.build();
    }

    /**
     * 从 PermissionManager 创建配置
     */
    public static PermissionConfig fromManager(PermissionManager manager) {
        PermissionConfig config = new PermissionConfig();
        config.setDefaultDecision(manager.getDefaultDecision().name());

        List<RuleConfig> ruleConfigs = new ArrayList<>();
        for (PermissionRule rule : manager.getRules()) {
            RuleConfig rc = new RuleConfig();
            rc.setPattern(rule.getPattern());
            rc.setDecision(rule.getDecision().name());
            rc.setDescription(rule.getDescription());
            ruleConfigs.add(rc);
        }
        config.setRules(ruleConfigs);

        return config;
    }
}
