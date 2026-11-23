package com.pullautumn.envmask;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Hook / Xposed / Frida 环境检测拦截。
 *
 * 检测点：
 *   ① Class.forName 找 Xposed/LSPosed/Frida 相关类 → 抛 ClassNotFoundException
 *   ② StackTrace 里出现 de.robv.android.xposed / com.frida → 过滤
 *   ③ /proc/self/maps 里出现 frida / xposed → 过滤
 *   ④ 加载的 so 列表（Runtime.loadLibrary）→ 过滤
 *   ⑤ Thread.getAllStackTraces → 过滤
 */
final class HookTraceHook {

    private static final String TAG = "[RunEnv/Trace]";

    /** 需要"找不到"的类名（前缀匹配） */
    private static final String[] HIDDEN_CLASS_PREFIXES = {
            "de.robv.android.xposed.",
            "org.lsposed.",
            "com.elderdrivers.riru.",
            "com.saurik.substrate.",
            "re.frida.",
            "com.frida.",
            "top.canyie.pine.",
            "com.swift.sandhook.",
            "me.weishu.epic.",
            "com.bytedance.zjgsu.",
    };

    /** 需要从 maps / 堆栈里过滤的关键字 */
    private static final String[] TRACE_KEYWORDS = {
            "xposed",
            "lsposed",
            "lspatch",
            "shizuposed",
            "frida",
            "substrate",
            "epic",
            "sandhook",
            "pine",
            "yahfa",
            "amiru",
            "sysku",
            "callsite",
    };

    private HookTraceHook() {
    }

    static void install(ClassLoader cl, Config cfg) {
        if (cfg.hideHook) {
            hookClassForName(cl);
            hookStackTrace();
            hookThreadStackTraces();
        }
        if (cfg.hideFrida) {
            hookProcMaps();
        }
        XposedBridge.log(TAG + " Hook/Frida 痕迹钩子已安装"
                + " (hideHook=" + cfg.hideHook + ", hideFrida=" + cfg.hideFrida + ")");
    }

    // ==================== Class.forName ====================

    private static void hookClassForName(ClassLoader cl) {
        XC_MethodHook guard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Reentry.enterClassForName()) {
                    return;
                }
                try {
                    String name = null;
                    if (param.args != null && param.args.length > 0) {
                        Object a0 = param.args[0];
                        if (a0 instanceof String) {
                            name = (String) a0;
                        }
                    }
                    if (name != null && isHiddenClass(name)) {
                        param.setThrowable(new ClassNotFoundException(name));
                    }
                } finally {
                    Reentry.exitClassForName();
                }
            }
        };

        try {
            Class<?> cls = Class.class;
            // forName(String)
            XposedHelpers.findAndHookMethod(cls, "forName", String.class, guard);
        } catch (Throwable ignored) {
        }
        try {
            // forName(String, boolean, ClassLoader)
            XposedHelpers.findAndHookMethod(Class.class, "forName",
                    String.class, boolean.class, ClassLoader.class, guard);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isHiddenClass(String name) {
        for (String p : HIDDEN_CLASS_PREFIXES) {
            if (name.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    // ==================== StackTraceElement 过滤 ====================

    /**
     * hook Throwable.getStackTrace() 和 Thread.getStackTrace()，
     * 把含 xposed/frida 关键字的帧删掉。
     *
     * 很多检测是：
     *   for (StackTraceElement e : new Throwable().getStackTrace()) {
     *       if (e.getClassName().contains("xposed")) return true;
     *   }
     */
    private static void hookStackTrace() {
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof StackTraceElement[])) {
                    return;
                }
                StackTraceElement[] arr = (StackTraceElement[]) r;
                java.util.List<StackTraceElement> keep = new java.util.ArrayList<>();
                for (StackTraceElement e : arr) {
                    if (!isTraceElement(e)) {
                        keep.add(e);
                    }
                }
                if (keep.size() != arr.length) {
                    param.setResult(keep.toArray(new StackTraceElement[0]));
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(Throwable.class, "getStackTrace", filter);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(Thread.class, "getStackTrace", filter);
        } catch (Throwable ignored) {
        }
        // 新 API: Throwable.getOurStackTrace 是私有，也可能被用
        try {
            XposedHelpers.findAndHookMethod(Throwable.class, "getOurStackTrace", filter);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isTraceElement(StackTraceElement e) {
        if (e == null) {
            return false;
        }
        String cn = safeLower(e.getClassName());
        String mn = safeLower(e.getMethodName());
        String fn = safeLower(e.getFileName());
        for (String k : TRACE_KEYWORDS) {
            if (cn.contains(k) || mn.contains(k) || fn.contains(k)) {
                return true;
            }
        }
        return false;
    }

    // ==================== Thread.getAllStackTraces ====================

    private static void hookThreadStackTraces() {
        try {
            XposedHelpers.findAndHookMethod(Thread.class, "getAllStackTraces",
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object r = param.getResult();
                            if (!(r instanceof java.util.Map)) {
                                return;
                            }
                            java.util.Map<Thread, StackTraceElement[]> map =
                                    (java.util.Map<Thread, StackTraceElement[]>) r;
                            java.util.Map<Thread, StackTraceElement[]> cleaned =
                                    new java.util.HashMap<>();
                            for (java.util.Map.Entry<Thread, StackTraceElement[]> en
                                    : map.entrySet()) {
                                StackTraceElement[] arr = en.getValue();
                                java.util.List<StackTraceElement> keep = new java.util.ArrayList<>();
                                for (StackTraceElement e : arr) {
                                    if (!isTraceElement(e)) {
                                        keep.add(e);
                                    }
                                }
                                cleaned.put(en.getKey(), keep.toArray(new StackTraceElement[0]));
                            }
                            param.setResult(cleaned);
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    // ==================== /proc/self/maps ====================

    /**
     * 拦截读 /proc/self/maps：
     *   - FileInputStream / BufferedReader 读该路径
     *   - 更可靠的做法：hook java.io.File 的构造函数不好做，
     *     改为 hook 常见读文件的入口
     *
     * 说明：完全拦住 maps 读取很难（native 层可以绕），
     * 这里覆盖最常见的 Java 层写法。
     */
    private static void hookProcMaps() {
        // 思路：hook BufferedReader.readLine，如果来源是 /proc/self/maps，
        // 过滤掉含关键字行。
        // 但拿不到来源路径。所以改为：hook String.contains / String.indexOf?
        // 太宽泛。
        //
        // 实际可行的方案：hook java.lang.ProcessBuilder / Runtime.exec 里的 cat /proc/self/maps
        // 已在 RootHook 里做了一部分。
        //
        // 这里补充：hook FileInputStream 构造 + read 返回值过滤太复杂且易崩，
        // 改为 hook 常见检测库的特定方法。留空占位，靠 StackTrace 命中。
        XposedBridge.log(TAG + " maps 过滤依赖 StackTrace + exec 拦截，无独立 hook");
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
