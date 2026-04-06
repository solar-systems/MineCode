package cn.abelib.minecode.session;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 状态模块接口 - 支持多组件状态保存
 *
 * <p>实现此接口的组件可以将其状态保存到会话中。
 * 例如：消息历史、计划笔记本、工具状态等。
 *
 * @author Abel
 */
public interface StateModule {

    /**
     * 获取模块名称（用于在会话中标识）
     *
     * @return 模块名称
     */
    String getModuleName();

    /**
     * 保存当前状态
     *
     * @return 状态 JSON
     */
    JsonNode saveState();

    /**
     * 加载状态
     *
     * @param state 状态 JSON
     */
    void loadState(JsonNode state);

    /**
     * 重置状态
     */
    default void resetState() {
    }
}
