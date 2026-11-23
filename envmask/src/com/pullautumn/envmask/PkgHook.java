package com.pullautumn.envmask;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 包名隐藏：让 root 管理应用 / 框架类 App 不出现在应用列表里。
 *
 * 这是 root 检测的重要组成部分 ——
 * 检测方常通过 getInstalledPackages() 找 Magisk Manager / SuperSU 等。
 */
final class PkgHook {

    private static final String TAG = "[RunEnv/Pkg]";

    /** 需要隐藏的包名 */
    private static final String[] HIDDEN_PKGS = {
            // root 管理
            "com.topjohnwu.magisk",
            "com.topjohnwu.magisk.alpha",
            "com.topjohnwu.magisk.debug",
            "io.github.vvb2060.magisk",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot",
            "com.ramdroid.appquarantine",

            // 框架
            "org.lsposed.manager",
            "org.lsposed.lspatch",
            "com.shizuposed.manager",
            "com.tsng.hidemyapplist",
            "fun.fpa",
            "top.nkbe.npatch",

            // 工具
            "bin.mt.plus",
            "bin.mt.plus.canary",
            "bin.mt.termex",
            "com.termux",

            // 检测器自己
            "icu.nullptr.applistdetector",
            "com.virb3.trustmealready",
            "com.junge.justtrustmepp",
    };

    private PkgHook() {
    }

    static void install(ClassLoader cl, Config cfg) {
        if (!cfg.hideRootApps) {
            return;
        }

        XC_MethodHook pkgListFilter = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof List)) {
                    return;
                }
                List<PackageInfo> list = (List<PackageInfo>) r;
                Iterator<PackageInfo> it = list.iterator();
                while (it.hasNext()) {
                    PackageInfo pi = it.next();
                    if (pi != null && isHidden(pi.packageName)) {
                        it.remove();
                    }
                }
            }
        };

        XC_MethodHook appListFilter = new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof List)) {
                    return;
                }
                List<ApplicationInfo> list = (List<ApplicationInfo>) r;
                Iterator<ApplicationInfo> it = list.iterator();
                while (it.hasNext()) {
                    ApplicationInfo ai = it.next();
                    if (ai != null && isHidden(ai.packageName)) {
                        it.remove();
                    }
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
                    "getInstalledPackages", int.class, pkgListFilter);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
                    "getInstalledApplications", int.class, appListFilter);
        } catch (Throwable ignored) {
        }

        XposedBridge.log(TAG + " 包名隐藏钩子已安装，共 " + HIDDEN_PKGS.length + " 项");
    }

    private static boolean isHidden(String pkg) {
        if (pkg == null) {
            return false;
        }
        for (String h : HIDDEN_PKGS) {
            if (h.equals(pkg)) {
                return true;
            }
        }
        return false;
    }
}
