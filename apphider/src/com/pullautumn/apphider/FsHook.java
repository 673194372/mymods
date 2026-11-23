package com.pullautumn.apphider;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 文件系统层拦截。
 *
 * 让 /data/data/<隐藏包名> 看起来不存在。
 *
 * 只在黑名单模式下有效 —— 白名单模式无法反推"哪些路径该隐藏"
 * （因为白名单语义是"除了名单全藏"，而文件路径探测不知道对方要查什么包）。
 * 白名单模式下这里直接跳过，靠 PM/AM 层已经够用。
 */
final class FsHook {

    private static final String TAG = "[AppHider/FS]";

    private FsHook() {
    }

    static void install(ClassLoader cl, final ConfigLoader.Result cfg) {
        if (cfg == null || cfg.isEmpty()) {
            return;
        }
        if (cfg.isWhitelist) {
            XposedBridge.log(TAG + " 白名单模式，跳过文件层（语义不允许）");
            return;
        }

        XC_MethodHook guard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pkg = pkgOf(param.thisObject);
                if (pkg != null && PmHook.shouldHide(pkg, cfg)) {
                    param.setResult(Boolean.FALSE);
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(File.class, "exists", guard);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " exists 失败: " + t.getMessage());
        }
        try {
            XposedHelpers.findAndHookMethod(File.class, "isDirectory", guard);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(File.class, "isFile", guard);
        } catch (Throwable ignored) {
        }

        XC_MethodHook listGuard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pkg = pkgOf(param.thisObject);
                if (pkg != null && PmHook.shouldHide(pkg, cfg)) {
                    param.setResult(null);
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(File.class, "listFiles", listGuard);
        } catch (Throwable ignored) {
        }

        XposedBridge.log(TAG + " File 钩子已安装");
    }

    /** 从 File 对象解析出包名 */
    private static String pkgOf(Object self) {
        if (!(self instanceof File)) {
            return null;
        }
        String path;
        try {
            path = ((File) self).getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
        // /data/data/com.foo/...   /data/user/0/com.foo/...   /data/user_de/0/com.foo/...
        String[] prefixes = {"/data/data/", "/data/user/0/", "/data/user_de/0/"};
        for (String p : prefixes) {
            if (path.startsWith(p)) {
                String rest = path.substring(p.length());
                int slash = rest.indexOf('/');
                return slash > 0 ? rest.substring(0, slash) : rest;
            }
        }
        return null;
    }
}
