package cn.abelib.minecode.skill;

import cn.abelib.minecode.skill.builtin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 技能注册器 - 管理和执行技能
 *
 * <p>功能：
 * <ul>
 *   <li>注册和管理技能</li>
 *   <li>根据名称查找技能</li>
 *   <li>执行技能并返回结果</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * SkillRegistry registry = new SkillRegistry();
 *
 * // 注册技能
 * registry.register(new CommitSkill());
 *
 * // 执行技能
 * String result = registry.execute("commit", context, "fix: bug");
 * }</pre>
 *
 * @author Abel
 */
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillRegistry() {
        registerDefaultSkills();
    }

    /**
     * 注册默认技能
     */
    private void registerDefaultSkills() {
        register(new HelpSkill());
        register(new CommitSkill());
        register(new ReviewSkill());
        register(new ExplainSkill());
        register(new RefactorSkill());
        register(new TestSkill());
        register(new TokensSkill());
        register(new ClearSkill());
        register(new SaveSkill());
        register(new LoadSkill());
        register(new NewSkill());
        register(new SessionsSkill());
        register(new ConfigSkill());

        log.debug("Registered {} default skills", skills.size());
    }

    /**
     * 注册技能
     *
     * @param skill 技能实例
     */
    public void register(Skill skill) {
        if (skill == null) {
            return;
        }
        String name = skill.name().toLowerCase();
        if (skills.containsKey(name)) {
            log.warn("Overwriting existing skill: {}", name);
        }
        skills.put(name, skill);
        log.debug("Registered skill: {}", name);
    }

    /**
     * 注销技能
     *
     * @param name 技能名称
     * @return 被移除的技能，如果不存在返回 null
     */
    public Skill unregister(String name) {
        return skills.remove(name.toLowerCase());
    }

    /**
     * 获取技能
     *
     * @param name 技能名称
     * @return 技能实例，不存在返回 null
     */
    public Skill get(String name) {
        return skills.get(name.toLowerCase());
    }

    /**
     * 检查技能是否存在
     *
     * @param name 技能名称
     * @return 是否存在
     */
    public boolean exists(String name) {
        return skills.containsKey(name.toLowerCase());
    }

    /**
     * 执行技能
     *
     * @param name    技能名称
     * @param context 执行上下文
     * @param args    参数
     * @return 执行结果
     * @throws SkillNotFoundException 技能不存在
     * @throws SkillExecutionException 执行失败
     */
    public String execute(String name, SkillContext context, String args) {
        Skill skill = get(name);
        if (skill == null) {
            throw new SkillNotFoundException("Unknown skill: " + name);
        }

        // 验证参数
        String validationError = skill.validateArgs(args);
        if (validationError != null) {
            return validationError;
        }

        try {
            log.debug("Executing skill: {} with args: {}", name, args);
            return skill.execute(context, args);
        } catch (Exception e) {
            log.error("Skill execution failed: {}", name, e);
            throw new SkillExecutionException("Skill execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * 列出所有技能
     *
     * @return 技能列表
     */
    public List<Skill> listAll() {
        return new ArrayList<>(skills.values());
    }

    /**
     * 获取技能名称列表
     *
     * @return 名称列表
     */
    public List<String> listNames() {
        return new ArrayList<>(skills.keySet());
    }

    /**
     * 获取技能数量
     */
    public int size() {
        return skills.size();
    }

    /**
     * 生成帮助文本
     */
    public String generateHelpText() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用技能:\n\n");

        for (Skill skill : skills.values()) {
            sb.append(String.format("  %-12s %s\n", skill.usage(), skill.description()));
        }

        return sb.toString();
    }

    /**
     * 技能未找到异常
     */
    public static class SkillNotFoundException extends RuntimeException {
        public SkillNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 技能执行异常
     */
    public static class SkillExecutionException extends RuntimeException {
        public SkillExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
