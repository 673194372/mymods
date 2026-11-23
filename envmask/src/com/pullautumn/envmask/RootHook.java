package com.pullautumn.envmask;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Root 检测拦截。
 *
 * 检测点：
 *   ① 常见 root 文件路径 /data/adb/magisk、/system/bin/su 等
 *   ② Runtime.exec("su") / ProcessBuilder
 *   ③ Build.TAGS 含 "test-keys"
 *   ④ RootChecker 类库的一些静态方法
 *   ⑤ 包名检测（Magisk Manager、SuperSU 等）→ 由 PmHook 统一处理
 */
final class RootHook {

    private static final String TAG = "[RunEnv/Root]";

    /** 需要"看起来不存在"的 root 相关路径 */
    private static final String[] ROOT_PATHS = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/system/sbin/su",
            "/vendor/bin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/bin/failsafe/su",
            "/system/sd/xbin/su",
            "/system/bin/magisk",
            "/system/xbin/magisk",
            "/sbin/magisk",
            "/data/adb/magisk",
            "/data/adb/magisk.db",
            "/data/adb/magisk.img",
            "/data/adb/modules",
            "/cache/.disable_magisk",
            "/dev/.magisk.unblock",
            "/init.magisk.rc",
            "/sbin/.magisk",
            "/system/app/Superuser.apk",
            "/system/app/SuperSU",
            "/system/app/SuperSU.apk",
            "/system/app/Magisk",
            "/system/etc/init.d/99SuperSUDaemon",
            "/data/data/com.noshufou.android.su",
            "/data/data/com.thirdparty.superuser",
            "/data/data/eu.chainfire.supersu",
            "/data/data/com.koushikdutta.superuser",
            "/data/data/com.topjohnwu.magisk",
            "/data/user/0/com.topjohnwu.magisk",
    };

    private RootHook() {
    }

    static void install(ClassLoader cl, Config cfg) {
        if (!cfg.hideRoot) {
            XposedBridge.log(TAG + " hideRoot=false，跳过");
            return;
        }
        hookFileExists(cl);
        hookRuntimeExec(cl);
        XposedBridge.log(TAG + " Root 检测钩子已安装");
    }

    // ==================== 文件存在性 ====================

    private static void hookFileExists(ClassLoader cl) {
        XC_MethodHook guard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Reentry.enterFileCheck()) {
                    return;
                }
                try {
                    if (!(param.thisObject instanceof File)) {
                        return;
                    }
                    String path;
                    try {
                        path = ((File) param.thisObject).getAbsolutePath();
                    } catch (Throwable t) {
                        return;
                    }
                    if (isRootPath(path)) {
                        param.setResult(Boolean.FALSE);
                    }
                } finally {
                    Reentry.exitFileCheck();
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(File.class, "exists", guard);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " File.exists hook 失败: " + t.getMessage());
        }
        try {
            XposedHelpers.findAndHookMethod(File.class, "isFile", guard);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(File.class, "isDirectory", guard);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(File.class, "canRead", guard);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(File.class, "canExecute", guard);
        } catch (Throwable ignored) {
        }

        // listFiles 也拦，防止枚举 /system/xbin 找 su
        XC_MethodHook listGuard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof File)) {
                    return;
                }
                String path;
                try {
                    path = ((File) param.thisObject).getAbsolutePath();
                } catch (Throwable t) {
                    return;
                }
                if (isRootPath(path)) {
                    param.setResult(null);
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(File.class, "listFiles", listGuard);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isRootPath(String path) {
        if (path == null) {
            return false;
        }
        for (String p : ROOT_PATHS) {
            if (path.equals(p) || path.startsWith(p + "/")) {
                return true;
            }
        }
        return false;
    }

    // ==================== Runtime.exec("su") ====================

    private static void hookRuntimeExec(ClassLoader cl) {
        XC_MethodHook guard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String cmd = execCmd(param.args);
                if (cmd == null) {
                    return;
                }
                if (isSuCommand(cmd)) {
                    // 让 exec 抛异常，模拟"没有 su"
                    param.setThrowable(new java.io.IOException(
                            "Cannot run program \"" + cmd + "\": error=13, Permission denied"));
                    XposedBridge.log(TAG + " Runtime.exec(\"" + cmd + "\") → IOException");
                }
            }
        };

        try {
            Class<?> rt = XposedHelpers.findClass("java.lang.Runtime", cl);
            XposedHelpers.findAndHookMethod(rt, "exec", String.class, guard);
            XposedHelpers.findAndHookMethod(rt, "exec", String.class, String[].class, guard);
        } catch (Throwable ignored) {
        }

        // ProcessBuilder
        XC_MethodHook pbGuard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0) {
                    return;
                }
                Object a0 = param.args[0];
                if (a0 instanceof java.util.List) {
                    StringBuilder sb = new StringBuilder();
                    for (Object o : (java.util.List<?>) a0) {
                        sb.append(o).append(' ');
                    }
                    if (isSuCommand(sb.toString().trim())) {
                        param.setThrowable(new java.io.IOException("error=13, Permission denied"));
                    }
                } else if (a0 instanceof String[]) {
                    String[] arr = (String[]) a0;
                    if (arr.length > 0 && isSuCommand(arr[0])) {
                        param.setThrowable(new java.io.IOException("error=13, Permission denied"));
                    }
                }
            }
        };
        try {
            Class<?> pb = XposedHelpers.findClass("java.lang.ProcessBuilder", cl);
            XposedHelpers.findAndHookMethod(pb, "start", pbGuard);
        } catch (Throwable ignored) {
        }
    }

    private static String execCmd(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object a0 = args[0];
        if (a0 instanceof String) {
            return (String) a0;
        }
        if (a0 instanceof String[]) {
            String[] arr = (String[]) a0;
            return arr.length > 0 ? arr[0] : null;
        }
        return null;
    }

    private static boolean isSuCommand(String cmd) {
        if (cmd == null) {
            return false;
        }
        String c = cmd.trim().toLowerCase();
        // 只拦以 su 开头或包含 " su" 的（避免误伤 "sudo" 之类，反正也没 sudo）
        if (c.equals("su") || c.startsWith("su ") || c.startsWith("su\t")) {
            return true;
        }
        if (c.contains("which su") || c.contains("whereis su")) {
            return true;
        }
        if (c.contains("/system/bin/su") || c.contains("/system/xbin/su")) {
            return true;
        }
        return false;
    }

}
