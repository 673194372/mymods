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
 * FPA / LSPatch / LSPosed 通用。
 *
 * 只实现 IXposedHookLoadPackage（不实现 ZygoteInit），
 * 因为 FPA 的 StartupParam.startupModulesPath 为空，
 * 依赖它的模块会初始化失败。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "[AppHider]";
    private static boolean sInited = false;

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

        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            init((Application) param.thisObject, lpparam.classLoader);
                        }
                    });

            // 兜底：模块加载晚于 onCreate 时立即执行
            try {
                Object app = XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                        "currentApplication");
                if (app instanceof Application) {
                    init((Application) app, lpparam.classLoader);
                }
            } catch (Throwable ignored) {
            }

        } catch (Throwable t) {
            XposedBridge.log(TAG + " 初始化失败: " + t);
        }
    }

    private static synchronized void init(Context ctx, ClassLoader cl) {
        if (sInited) {
            return;
        }
        sInited = true;

        try {
            ConfigLoader.Result cfg = ConfigLoader.load();
            XposedBridge.log(TAG + " 配置来源: " + cfg.source);
            XposedBridge.log(TAG + " 模式: " + (cfg.isWhitelist ? "白名单" : "黑名单")
                    + " | hidden=" + cfg.hidden.size()
                    + " | visible=" + cfg.visible.size());

            PmHook.install(cl, cfg);
            AmHook.install(cl, cfg);
            FsHook.install(cl, cfg);

            XposedBridge.log(TAG + " 全部钩子安装完成");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " init 异常: " + t);
        }
    }
}
