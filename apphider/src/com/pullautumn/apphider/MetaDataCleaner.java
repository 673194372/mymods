package com.pullautumn.apphider;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Xposed 模块标记清除。
 *
 * 检测方的常见做法（ApplistDetector 实测源码）：
 *
 *   for (ResolveInfo ri : pm.queryIntentActivities(intent, GET_META_DATA)) {
 *       if (ri.activityInfo.applicationInfo.metaData
 *               ?.get("xposedminversion") != null) {
 *           return FOUND;
 *       }
 *   }
 *
 * 注意它读的是 metaData 里的键，不是包名。
 * 所以仅靠包名黑名单拦不住 —— 只要 manifest 里留着
 * xposedmodule / xposedminversion / xposeddescription，
 * 任何 Xposed 模块都会被认出来。
 *
 * 本类在 PackageManager 返回结果里把这些键直接抹掉，
 * 与包名名单互补，双重保险。
 */
final class MetaDataCleaner {

    private static final String TAG = "[AppHider/Meta]";

    /** Xposed 模块声明用的 meta-data 键 */
    private static final String[] XPOSE_KEYS = {
            "xposedmodule",          // 标记这是模块
            "xposedminversion",      // ApplistDetector 查的就是这个
            "xposeddescription",     // 模块描述
            "xposedscope",           // 作用域声明
            "xposedsharedprefs",     // 跨进程配置
            "xposednewdao",          // 部分框架用
    };

    private MetaDataCleaner() {
    }

    static void install(ClassLoader cl) {
        try {
            // getInstalledPackages 返回的 PackageInfo.applicationInfo.metaData
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getInstalledPackages", int.class,
                    new CleanHook());

            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getInstalledApplications", int.class,
                    new CleanHook());

            // getPackageInfo 单个查询
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getPackageInfo", String.class, int.class,
                    new SingleCleanHook());

            XposedBridge.log(TAG + " meta-data 清除钩子已安装");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " 安装失败: " + t.getMessage());
        }
    }

    /** 处理 List 返回 */
    private static final class CleanHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Object r = param.getResult();
            if (!(r instanceof List)) {
                return;
            }
            int cleaned = 0;
            for (Object o : (List<?>) r) {
                if (o instanceof PackageInfo) {
                    if (clean(((PackageInfo) o).applicationInfo)) {
                        cleaned++;
                    }
                } else if (o instanceof ApplicationInfo) {
                    if (clean((ApplicationInfo) o)) {
                        cleaned++;
                    }
                }
            }
            if (cleaned > 0) {
                XposedBridge.log(TAG + " 清除 " + cleaned + " 个应用的 Xposed 标记");
            }
        }
    }

    /** 处理单个 PackageInfo */
    private static final class SingleCleanHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Object r = param.getResult();
            if (r instanceof PackageInfo) {
                clean(((PackageInfo) r).applicationInfo);
            }
        }
    }

    /**
     * 清除一个 ApplicationInfo 上的 Xposed 标记。
     *
     * 对包名黑名单之外的调用方也开放（queryIntent 路径需要）。
     * 返回是否真的改了东西。
     */
    static boolean cleanApplicationInfo(ApplicationInfo ai) {
        return clean(ai);
    }

    /**
     * 清除一个 ApplicationInfo 上的 Xposed 标记。
     *
     * 返回是否真的改了东西。
     */
    private static boolean clean(ApplicationInfo ai) {
        if (ai == null) {
            return false;
        }
        // 1) 把 flags 里的 Xposed 相关位清掉（部分框架会置位）
        //    这里不动 flags，避免误伤

        // 2) 清 metaData
        android.os.Bundle md = ai.metaData;
        if (md == null) {
            return false;
        }
        boolean changed = false;
        for (String key : XPOSE_KEYS) {
            try {
                if (md.containsKey(key)) {
                    md.remove(key);
                    changed = true;
                }
            } catch (Throwable ignored) {
            }
        }
        return changed;
    }
}
