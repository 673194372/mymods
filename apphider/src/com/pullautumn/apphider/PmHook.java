package com.pullautumn.apphider;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * PackageManager 层拦截。
 *
 * 两条铁律（违反会导致目标应用崩溃）：
 *
 *   一、绝不原地修改 PackageManager 返回的 List。
 *       框架内部可能缓存这些 List 实例。原地 remove 会让调用方
 *       两次拿到的内容不一致，进而触发 WebView 之类的重复初始化：
 *         IllegalStateException: Already registered a list of actions
 *       正确做法：构造新 List，用 setResult 换掉返回值。
 *
 *   二、系统应用永不隐藏。
 *       白名单的语义是「我另外装了什么」不该被发现，
 *       系统应用本来就该存在，隐藏它们只会破坏依赖关系。
 *       判定依据：ApplicationInfo.FLAG_SYSTEM / FLAG_UPDATED_SYSTEM_APP。
 */
final class PmHook {

    private static final String TAG = "[AppHider/PM]";

    /** 系统应用标志 */
    private static final int SYSTEM_FLAGS =
            ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

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

    // ==================== getInstalledPackages ====================

    private static void hookGetInstalledPackages(ClassLoader cl, final ConfigLoader.Result cfg) {
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof List)) {
                    return;
                }
                List<PackageInfo> src = (List<PackageInfo>) r;
                // 构造新 List，绝对不动 src
                List<PackageInfo> out = new ArrayList<PackageInfo>(src.size());
                int removed = 0;
                for (PackageInfo pi : src) {
                    if (pi == null) {
                        continue;
                    }
                    if (shouldHidePackage(pi, cfg)) {
                        removed++;
                        continue;
                    }
                    out.add(pi);
                }
                if (removed > 0) {
                    param.setResult(out);
                    XposedBridge.log(TAG + " getInstalledPackages: "
                            + src.size() + " → " + out.size());
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getInstalledPackages", int.class, filter);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", cl,
                    "getInstalledPackages", int.class, int.class, filter);
        } catch (Throwable ignored) {
        }
    }

    // ==================== getInstalledApplications ====================

    private static void hookGetInstalledApplications(ClassLoader cl, final ConfigLoader.Result cfg) {
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof List)) {
                    return;
                }
                List<ApplicationInfo> src = (List<ApplicationInfo>) r;
                List<ApplicationInfo> out = new ArrayList<ApplicationInfo>(src.size());
                int removed = 0;
                for (ApplicationInfo ai : src) {
                    if (ai == null) {
                        continue;
                    }
                    if (shouldHideAppInfo(ai, cfg)) {
                        removed++;
                        continue;
                    }
                    out.add(ai);
                }
                if (removed > 0) {
                    param.setResult(out);
                    XposedBridge.log(TAG + " getInstalledApplications: "
                            + src.size() + " → " + out.size());
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

    // ==================== getPackageInfo / getApplicationInfo ====================

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

    // ==================== queryIntent* ====================

    private static void hookQueryIntent(ClassLoader cl, final ConfigLoader.Result cfg) {
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof List)) {
                    return;
                }
                List<ResolveInfo> src = (List<ResolveInfo>) r;
                List<ResolveInfo> out = new ArrayList<ResolveInfo>(src.size());
                int removed = 0;
                for (ResolveInfo ri : src) {
                    if (ri == null) {
                        continue;
                    }
                    String pkg = resolvePkg(ri);
                    if (pkg != null && shouldHideResolve(ri, cfg)) {
                        removed++;
                        continue;
                    }
                    out.add(ri);
                }
                if (removed > 0) {
                    param.setResult(out);
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

    private static String resolvePkg(ResolveInfo ri) {
        if (ri.activityInfo != null) {
            return ri.activityInfo.packageName;
        }
        if (ri.serviceInfo != null) {
            return ri.serviceInfo.packageName;
        }
        if (ri.providerInfo != null) {
            return ri.providerInfo.packageName;
        }
        return null;
    }

    private static boolean shouldHideResolve(ResolveInfo ri, ConfigLoader.Result cfg) {
        String pkg = resolvePkg(ri);
        if (pkg == null) {
            return false;
        }
        // 系统应用放行
        ApplicationInfo ai = null;
        if (ri.activityInfo != null) {
            ai = ri.activityInfo.applicationInfo;
        } else if (ri.serviceInfo != null) {
            ai = ri.serviceInfo.applicationInfo;
        } else if (ri.providerInfo != null) {
            ai = ri.providerInfo.applicationInfo;
        }
        if (ai != null && isSystemApp(ai)) {
            return false;
        }
        return shouldHide(pkg, cfg);
    }

    // ==================== getPackagesForUid ====================

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
                            List<String> keep = new ArrayList<String>(arr.length);
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

    // ==================== Android 12+ 模块 ====================

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
                            List<Object> src = (List<Object>) r;
                            List<Object> out = new ArrayList<Object>(src.size());
                            for (Object mi : src) {
                                boolean hide = false;
                                try {
                                    String pkg = (String) XposedHelpers.getObjectField(
                                            mi, "packageName");
                                    hide = shouldHide(pkg, cfg);
                                } catch (Throwable ignored) {
                                }
                                if (!hide) {
                                    out.add(mi);
                                }
                            }
                            if (out.size() != src.size()) {
                                param.setResult(out);
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    // ==================== 核心判定 ====================

    /** 系统应用判定 */
    private static boolean isSystemApp(ApplicationInfo ai) {
        if (ai == null) {
            return false;
        }
        return (ai.flags & SYSTEM_FLAGS) != 0;
    }

    /** 针对 PackageInfo 的判定（含系统应用放行） */
    private static boolean shouldHidePackage(PackageInfo pi, ConfigLoader.Result cfg) {
        if (pi == null) {
            return false;
        }
        if (isSystemApp(pi.applicationInfo)) {
            return false;
        }
        return shouldHide(pi.packageName, cfg);
    }

    /** 针对 ApplicationInfo 的判定 */
    private static boolean shouldHideAppInfo(ApplicationInfo ai, ConfigLoader.Result cfg) {
        if (ai == null) {
            return false;
        }
        if (isSystemApp(ai)) {
            return false;
        }
        return shouldHide(ai.packageName, cfg);
    }

    /**
     * 包名级判定。
     *
     * 前置保护：
     *   1. 系统必需包（WebView 提供者、核心 Provider、输入法等）永不隐藏
     *   2. 白名单模式只过滤非系统应用（调用方需先判 FLAG_SYSTEM）
     */
    static boolean shouldHide(String pkg, ConfigLoader.Result cfg) {
        if (pkg == null || cfg == null) {
            return false;
        }
        if (CriticalPkgs.isAlwaysVisible(pkg)) {
            return false;
        }
        if (cfg.isWhitelist) {
            return !matchAny(pkg, cfg.visible);
        }
        return matchAny(pkg, cfg.hidden);
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
