package cn.abelib.minecode.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话接口 - 抽象会话操作
 *
 * @author Abel
 */
public interface Session {

    /**
     * 获取会话 ID
     */
    String getId();

    /**
     * 获取会话键
     */
    SessionKey getKey();

    /**
     * 获取会话元数据
     */
    SessionMetadata getMetadata();

    /**
     * 获取消息历史
     */
    List<JsonNode> getMessages();

    /**
     * 添加消息（增量追加）
     */
    void appendMessage(JsonNode message);

    /**
     * 获取模块状态
     */
    JsonNode getModuleState(String moduleName);

    /**
     * 设置模块状态
     */
    void setModuleState(String moduleName, JsonNode state);

    /**
     * 获取所有模块状态
     */
    Map<String, JsonNode> getAllModuleStates();

    /**
     * 保存会话
     */
    void save();

    /**
     * 重新加载会话
     */
    void reload();

    /**
     * 删除会话
     */
    boolean delete();

    /**
     * 获取消息数量
     */
    default int getMessageCount() {
        return getMessages().size();
    }
}
