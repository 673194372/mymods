package com.pullautumn.envmask;

/**
 * 重入保护。
 *
 * 反射类 hook（Field.get / Class.forName / File.exists 等）会被极大量调用，
 * 如果 hook 内部又触发同一个方法，就会无限递归或造成数据错乱：
 *
 *   实测案例：
 *     EventBus.register() 用反射探测 Android 环境
 *     → 触发我 hook 的 Field.get()
 *     → hook 内部读 metaData 又调 Field.get()
 *     → 递归 → 反射数据异常
 *     → EventBus 误判"Android 组件缺失"，抛 RuntimeException
 *
 * 做法：每个 hook 进入前检查标记，已在处理中就直通，不再拦截。
 */
final class Reentry {

    private static final ThreadLocal<Boolean> IN_FIELD_GET = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> IN_FILE_CHECK = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> IN_CLASS_FOR_NAME = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> IN_STACK_TRACE = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> IN_SETTINGS = new ThreadLocal<Boolean>();

    private Reentry() {
    }

    static boolean enterFieldGet() {
        return enter(IN_FIELD_GET);
    }

    static void exitFieldGet() {
        exit(IN_FIELD_GET);
    }

    static boolean enterFileCheck() {
        return enter(IN_FILE_CHECK);
    }

    static void exitFileCheck() {
        exit(IN_FILE_CHECK);
    }

    static boolean enterClassForName() {
        return enter(IN_CLASS_FOR_NAME);
    }

    static void exitClassForName() {
        exit(IN_CLASS_FOR_NAME);
    }

    static boolean enterStackTrace() {
        return enter(IN_STACK_TRACE);
    }

    static void exitStackTrace() {
        exit(IN_STACK_TRACE);
    }

    static boolean enterSettings() {
        return enter(IN_SETTINGS);
    }

    static void exitSettings() {
        exit(IN_SETTINGS);
    }

    /** 返回 true 表示可以进入（首次），false 表示已在处理中 */
    private static boolean enter(ThreadLocal<Boolean> tl) {
        if (Boolean.TRUE.equals(tl.get())) {
            return false;
        }
        tl.set(Boolean.TRUE);
        return true;
    }

    private static void exit(ThreadLocal<Boolean> tl) {
        tl.set(Boolean.FALSE);
    }
}
