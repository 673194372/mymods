package com.pullautumn.apphider;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * PackageManager 层拦截。
 *
 * 支持两种模式（由 HMA 配置的 isWhitelist 决定）：
 *
 *   黑名单模式 (isWhitelist=false)
 *     hidden 集合里的包名 → 从返回结果中删除
 *     "隐藏这几个"
 *
 *   白名单模式 (isWhitelist=true)
 *     visible 集合里的包名 → 保留
 *     其他 → 全部从返回结果中删除
 *     "只允许这几个被看见"
 *
 * 白名单模式完全可行，因为我们 hook 的是「返回列表」这个动作本身 ——
 * 列表就在手里，按规则过滤即可，不需要预先知道设备装了什么。
 */
final class PmHook {

    private static final String TAG = "[AppHider/PM]";

    private PmHook() {
    }

    static void install(ClassLoader cl, ConfigLoader.Result cfg) {
        if (cfg == null || cfg.isEmpty()) {
            XposedBridge.log(TAG + " 配置为空，跳过");
            return;
        }
        XposedBridge.log(TAG + " 安装中 | 模式=" + (cfg.isWhitelist ? "白名单" : "黑名单")
                + " | hidden=" + cfg.hidden.size()
                + " | visible=" + cfg.visible.size());

        hookGetInstalledPackages(cl, cfg);
        hookGetInstalledApplications(cl, cfg);
        hookGetPackageInfo(cl, cfg);
        hookGetApplicationInfo(cl, cfg);
        hookQueryIntent(cl, cfg);
        hookGetPackagesForUid(cl, cfg);
        hookGetInstalledModules(cl, cfg);
    }

    // ---------- getInstalledPackages ----------
    private static void hookGetInstalledPackages(ClassLoader cl, final ConfigLoader.Result cfg) {
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof List)) {
                    return;
                }
                List<PackageInfo> list = (List<PackageInfo>) r;
                int before = list.size();
                Iterator<PackageInfo> it = list.iterator();
                while (it.hasNext()) {
                    PackageInfo pi = it.next();
                    if (pi != null && shouldHide(pi.packageName, cfg)) {
                        it.remove();
                    }
                }
                if (before != list.size()) {
                    XposedBridge.log(TAG + " getInstalledPackages: " + before + " → " + list.size());
                }
            }
        };

        for (String sig : new String[]{"int", "int,int"}) {
            try {
                if ("int".equals(sig)) {
                    XposedHelpers.findAndHookMethod(
                            "android.app.ApplicationPackageManager", cl,
                            "getInstalledPackages", int.class, filter);
                } else {
                    XposedHelpers.findAndHookMethod(
                            "android.app.ApplicationPackageManager", cl,
                            "getInstalledPackages", int.class, int.class, filter);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------- getInstalledApplications ----------
    private static void hookGetInstalledApplications(ClassLoader cl, final ConfigLoader.Result cfg) {
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof List)) {
                    return;
                }
                List<ApplicationInfo> list = (List<ApplicationInfo>) r;
                int before = list.size();
                Iterator<ApplicationInfo> it = list.iterator();
                while (it.hasNext()) {
                    ApplicationInfo ai = it.next();
                    if (ai != null && shouldHide(ai.packageName, cfg)) {
                        it.remove();
                    }
                }
                if (before != list.size()) {
                    XposedBridge.log(TAG + " getInstalledApplications: " + before + " → " + list.size());
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getInstalledApplications", int.class, filter);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getInstalledApplications", int.class, int.class, filter);
        } catch (Throwable ignored) {
        }
    }

    // ---------- getPackageInfo / getApplicationInfo ----------
    private static void hookGetPackageInfo(ClassLoader cl, final ConfigLoader.Result cfg) {
        XC_MethodHook guard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pkg = pkgArg(param.args);
                if (pkg != null && shouldHide(pkg, cfg)) {
                    param.setThrowable(new PackageManager.NameNotFoundException(pkg));
                }
            }
        };
        for (String name : new String[]{"getPackageInfo", "getPackageInfoAsUser"}) {
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager", cl,
                        name, String.class, int.class, guard);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager", cl,
                        name, String.class, int.class, int.class, guard);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void hookGetApplicationInfo(ClassLoader cl, final ConfigLoader.Result cfg) {
        XC_MethodHook guard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pkg = pkgArg(param.args);
                if (pkg != null && shouldHide(pkg, cfg)) {
                    param.setThrowable(new PackageManager.NameNotFoundException(pkg));
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getApplicationInfo", String.class, int.class, guard);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getApplicationInfo", String.class, int.class, int.class, guard);
        } catch (Throwable ignored) {
        }
    }

    // ---------- queryIntent* ----------
    private static void hookQueryIntent(ClassLoader cl, final ConfigLoader.Result cfg) {
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof List)) {
                    return;
                }
                List<ResolveInfo> list = (List<ResolveInfo>) r;
                Iterator<ResolveInfo> it = list.iterator();
                while (it.hasNext()) {
                    ResolveInfo ri = it.next();
                    if (ri == null) {
                        continue;
                    }
                    String pkg = null;
                    if (ri.activityInfo != null) {
                        pkg = ri.activityInfo.packageName;
                    } else if (ri.serviceInfo != null) {
                        pkg = ri.serviceInfo.packageName;
                    } else if (ri.providerInfo != null) {
                        pkg = ri.providerInfo.packageName;
                    }
                    if (pkg != null && shouldHide(pkg, cfg)) {
                        it.remove();
                    }
                }
            }
        };

        for (String m : new String[]{
                "queryIntentActivities", "queryIntentActivityOptions",
                "queryBroadcastReceivers", "queryIntentServices",
                "queryIntentContentProviders"}) {
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager", cl,
                        m, android.content.Intent.class, int.class, filter);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager", cl,
                        m, android.content.Intent.class, int.class, int.class, filter);
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------- getPackagesForUid ----------
    private static void hookGetPackagesForUid(ClassLoader cl, final ConfigLoader.Result cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getPackagesForUid", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object r = param.getResult();
                            if (!(r instanceof String[])) {
                                return;
                            }
                            String[] arr = (String[]) r;
                            List<String> keep = new ArrayList<>();
                            for (String s : arr) {
                                if (!shouldHide(s, cfg)) {
                                    keep.add(s);
                                }
                            }
                            if (keep.size() != arr.length) {
                                param.setResult(keep.toArray(new String[0]));
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    // ---------- Android 12+ 模块 ----------
    private static void hookGetInstalledModules(ClassLoader cl, final ConfigLoader.Result cfg) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.content.pm.PackageManager", cl,
                    "getInstalledModules", int.class,
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object r = param.getResult();
                            if (!(r instanceof List)) {
                                return;
                            }
                            List<Object> list = (List<Object>) r;
                            Iterator<Object> it = list.iterator();
                            while (it.hasNext()) {
                                Object mi = it.next();
                                try {
                                    String pkg = (String) XposedHelpers.getObjectField(mi, "packageName");
                                    if (shouldHide(pkg, cfg)) {
                                        it.remove();
                                    }
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    // ==================== 核心判定 ====================

    /**
     * 统一的"是否应该隐藏"判定。两种模式的区别只在这里。
     *
     * 黑名单：在 hidden 里 → 隐藏
     * 白名单：不在 visible 里 → 隐藏
     */
    static boolean shouldHide(String pkg, ConfigLoader.Result cfg) {
        if (pkg == null || cfg == null) {
            return false;
        }

        if (cfg.isWhitelist) {
            // 白名单模式：只保留 visible 里的
            return !matchAny(pkg, cfg.visible);
        } else {
            // 黑名单模式：命中 hidden 的才隐藏
            return matchAny(pkg, cfg.hidden);
        }
    }

    /** 支持精确匹配 + "com.foo.*" 通配 */
    private static boolean matchAny(String pkg, Set<String> set) {
        if (set == null || set.isEmpty()) {
            return false;
        }
        if (set.contains(pkg)) {
            return true;
        }
        for (String h : set) {
            if (h.endsWith(".*")) {
                if (pkg.startsWith(h.substring(0, h.length() - 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String pkgArg(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object a = args[0];
        return a instanceof String ? (String) a : null;
    }
}
