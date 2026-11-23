package com.pullautumn.apphider;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 模块入口。
 *
 * 挂载时机很关键：
 *
 *   Application.attachBaseContext()   ← 最早，推荐
 *   Application.onCreate()            ← 较晚，WebView 可能已初始化
 *
 * 之前挂在 onCreate 上，导致 WebView provider 已经走过第一轮初始化，
 * 我的 hook 装上后返回的包列表变了，WebView 又走了一轮 → 重复注册：
 *   IllegalStateException: Already registered a list of actions in this process
 *
 * 现在改为 attachBaseContext + 立即执行，确保在任何业务代码
 * （包括 WebView 初始化）之前装好 hook，只装一次、结果恒定。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "[AppHider]";
    private static volatile boolean sInited = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) {
            return;
        }
        final String pkg = lpparam.packageName;

        if (pkg.equals("com.pullautumn.apphider") || pkg.equals("android")) {
            return;
        }

        XposedBridge.log(TAG + " 载入目标: " + pkg);

        // ① 最优先：attachBaseContext，早于 onCreate 和任何业务初始化
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "attachBaseContext",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            init(lpparam.classLoader);
                        }
                    });
            XposedBridge.log(TAG + " 已挂在 attachBaseContext");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " attachBaseContext hook 失败: " + t.getMessage());
        }

        // ② 兜底：onCreate（若 attachBaseContext 未触发）
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            init(lpparam.classLoader);
                        }
                    });
        } catch (Throwable ignored) {
        }

        // ③ 极端兜底：模块加载已晚，立即执行
        try {
            Object app = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                    "currentApplication");
            if (app instanceof Application) {
                init(lpparam.classLoader);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 同步 + 一次性。多路径触发只生效一次，保证 hook 集合恒定。
     */
    private static synchronized void init(ClassLoader cl) {
        if (sInited) {
            return;
        }
        sInited = true;

        try {
            ConfigLoader.Result cfg = ConfigLoader.load();
            XposedBridge.log(TAG + " 模式: " + (cfg.isWhitelist ? "白名单" : "黑名单")
                    + " | hidden=" + cfg.hidden.size());

            if (DebugFlags.DIAGNOSTIC_ONLY) {
                XposedBridge.log(TAG + " 诊断模式：不安装任何 hook");
                return;
            }

            if (DebugFlags.ENABLE_PM) {
                PmHook.install(cl, cfg);
                // Xposed 标记清除：与包名名单互补，抹掉 manifest 里的
                // xposedminversion 等键，防 ApplistDetector 那类检测
                MetaDataCleaner.install(cl);
            }
            if (DebugFlags.ENABLE_AM) {
                AmHook.install(cl, cfg);
            }
            if (DebugFlags.ENABLE_FS) {
                FsHook.install(cl, cfg);
            }

            XposedBridge.log(TAG + " 钩子安装完成");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " init 异常: " + t);
        }
    }
}
