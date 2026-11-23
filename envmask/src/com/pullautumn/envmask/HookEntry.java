package com.pullautumn.envmask;

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
 * 只实现 IXposedHookLoadPackage（不实现 ZygoteInit），
 * 因为 FPA 的 StartupParam.startupModulesPath 为空。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "[RunEnv]";
    private static boolean sInited = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) {
            return;
        }
        final String pkg = lpparam.packageName;

        // 不处理自己
        if (pkg.equals("com.pullautumn.envmask") || pkg.equals("android")) {
            return;
        }

        XposedBridge.log(TAG + " 载入目标: " + pkg);

        try {
            XposedHelpers.findAndHookMethod(
                    Application.class, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            init((Application) param.thisObject, lpparam.classLoader);
                        }
                    });

            // 兜底：模块加载晚于 onCreate 时立即执行
            try {
                Object app = XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass("android.app.ActivityThread",
                                lpparam.classLoader),
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
            Config cfg = Config.load();
            XposedBridge.log(TAG + " 配置: " + cfg.summary());

            // 顺序有讲究：
            // 1. DevOpt 最先（很多检测第一步查这个）
            DevOptHook.install(cl, cfg);
            // 1b. DevOpt 补强（isDeveloperOptionsEnabled / ContentResolver 直查等）
            DevOptExtraHook.install(cl, cfg);
            // 1c. 反射层统一 hook（Field.get 只在这里装一次）
            ReflectionHook.install(cl, cfg);
            // 2. Root
            RootHook.install(cl, cfg);
            // 3. 包名隐藏
            PkgHook.install(cl, cfg);
            // 4. Hook 痕迹
            HookTraceHook.install(cl, cfg);
            // 5. 其他环境
            EnvHook.install(cl, cfg);

            XposedBridge.log(TAG + " 全部钩子安装完成 ✅");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " init 异常: " + t);
        }
    }
}
