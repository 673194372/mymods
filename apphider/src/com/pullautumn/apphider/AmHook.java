package com.pullautumn.apphider;

import android.app.ActivityManager;

import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * ActivityManager 层拦截：运行中进程、服务、任务。
 *
 * 复用 PmHook.shouldHide()，白名单/黑名单都支持。
 */
final class AmHook {

    private static final String TAG = "[AppHider/AM]";

    private AmHook() {
    }

    static void install(ClassLoader cl, final ConfigLoader.Result cfg) {
        if (cfg == null || cfg.isEmpty()) {
            return;
        }

        // getRunningAppProcesses
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ActivityManager", cl,
                    "getRunningAppProcesses",
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object r = param.getResult();
                            if (!(r instanceof List)) {
                                return;
                            }
                            List<ActivityManager.RunningAppProcessInfo> list =
                                    (List<ActivityManager.RunningAppProcessInfo>) r;
                            int before = list.size();
                            Iterator<ActivityManager.RunningAppProcessInfo> it = list.iterator();
                            while (it.hasNext()) {
                                ActivityManager.RunningAppProcessInfo pi = it.next();
                                if (pi == null || pi.processName == null) {
                                    continue;
                                }
                                String base = pi.processName.split(":")[0];
                                if (PmHook.shouldHide(base, cfg)) {
                                    it.remove();
                                }
                            }
                            if (before != list.size()) {
                                XposedBridge.log(TAG + " 进程列表: " + before + " → " + list.size());
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " getRunningAppProcesses 失败: " + t.getMessage());
        }

        // getRunningServices
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ActivityManager", cl,
                    "getRunningServices", int.class,
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object r = param.getResult();
                            if (!(r instanceof List)) {
                                return;
                            }
                            List<ActivityManager.RunningServiceInfo> list =
                                    (List<ActivityManager.RunningServiceInfo>) r;
                            Iterator<ActivityManager.RunningServiceInfo> it = list.iterator();
                            while (it.hasNext()) {
                                ActivityManager.RunningServiceInfo si = it.next();
                                if (si == null || si.service == null) {
                                    continue;
                                }
                                if (PmHook.shouldHide(si.service.getPackageName(), cfg)) {
                                    it.remove();
                                }
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }

        // getRunningTasks
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ActivityManager", cl,
                    "getRunningTasks", int.class,
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object r = param.getResult();
                            if (!(r instanceof List)) {
                                return;
                            }
                            List<ActivityManager.RunningTaskInfo> list =
                                    (List<ActivityManager.RunningTaskInfo>) r;
                            Iterator<ActivityManager.RunningTaskInfo> it = list.iterator();
                            while (it.hasNext()) {
                                ActivityManager.RunningTaskInfo ti = it.next();
                                if (ti == null || ti.baseActivity == null) {
                                    continue;
                                }
                                if (PmHook.shouldHide(ti.baseActivity.getPackageName(), cfg)) {
                                    it.remove();
                                }
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }

        XposedBridge.log(TAG + " ActivityManager 钩子已安装");
    }
}
