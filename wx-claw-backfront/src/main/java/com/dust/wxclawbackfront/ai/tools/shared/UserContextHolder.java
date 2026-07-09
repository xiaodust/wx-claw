package com.dust.wxclawbackfront.ai.tools.shared;

/**
 * 用户上下文持有器，用于在 AI Tool 调用过程中传递当前用户的 userId
 * 使用 ThreadLocal 确保线程安全
 */
public class UserContextHolder {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前用户ID
     */
    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前用户ID
     */
    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 清除当前用户ID
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }

    /**
     * 检查是否已设置用户ID
     */
    public static boolean hasUserId() {
        return USER_ID_HOLDER.get() != null;
    }
}
