package com.pullautumn.apphider;

/**
 * 系统必需包豁免清单。
 *
 * 这些包无论白名单/黑名单模式如何配置，都必须对目标应用可见。
 * 原因：它们不是"用户应用"，而是目标应用赖以运行的系统组件。
 * 一旦隐藏，会导致目标应用直接崩溃。
 *
 * 典型崩溃案例：
 *   WebViewFactory$MissingWebViewPackageException:
 *   Failed to load WebView provider: com.google.android.webview
 *   → 任何使用 WebView / AgentWeb / H5 页面的应用都会启动失败
 */
final class CriticalPkgs {

    /**
     * 永远放行的包名。
     *
     * 分类说明：
     *   WebView 提供者   —— 不隐藏，否则网页组件加载失败
     *   系统框架         —— android 本身
     *   核心 Provider    —— settings / media / downloads 等
     *   Google 服务      —— 部分应用强依赖
     */
    private static final String[] ALWAYS_VISIBLE = {
            // ===== WebView 提供者（最关键）=====
            "com.google.android.webview",
            "com.android.webview",
            "com.google.android.trichromelibrary",
            "com.android.chrome",              // Trichrome 模式下 Chrome 即提供者
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",

            // ===== 系统框架 =====
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.shell",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",

            // ===== 核心 Provider =====
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.providers.media.module",
            "com.android.providers.downloads",
            "com.android.providers.downloads.ui",
            "com.android.providers.contacts",
            "com.android.providers.telephony",
            "com.android.providers.calendar",
            "com.android.providers.userdictionary",
            "com.android.externalstorage",
            "com.android.documentsui",
            "com.android.mtp",

            // ===== Google 基础服务 =====
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.gsf.login",
            "com.google.android.syncadapters.contacts",
            "com.google.android.syncadapters.calendar",
            "com.google.android.backuptransport",
            "com.google.android.ext.services",
            "com.google.android.ext.shared",

            // ===== 网络与安全 =====
            "com.android.networkstack",
            "com.android.networkstack.tethering",
            "com.android.captiveportallogin",
            "com.android.keychain",
            "com.android.certinstaller",
            "com.android.se",
            "com.android.ons",
            "com.android.phone",
            "com.android.bluetooth",

            // ===== 输入法（否则无法输入文字）=====
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.sohu.inputmethod.sogou",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.baidu.input",
            "com.baidu.input_mi",
            "com.iflytek.inputmethod",
            "com.iflytek.inputmethod.miui",
            "com.miui.inputmethod",

            // ===== 小米/HyperOS 系统组件 =====
            "com.miui.securitycenter",
            "com.miui.securitycore",
            "com.lbe.security.miui",
            "com.miui.contentcatcher",
            "com.xiaomi.xmsf",
            "com.miui.analytics",
            "com.xiaomi.metoknlp",
            "com.miui.powerkeeper",
            "com.miui.daemon",

            // ===== 分享/打开文件 =====
            "com.android.intentresolver",
            "com.android.mms",
            "com.android.bluetoothmidiservice",
            "com.android.providers.blockednumber",
    };

    private CriticalPkgs() {
    }

    /**
     * 是否属于系统必需包（永不隐藏）。
     */
    static boolean isAlwaysVisible(String pkg) {
        if (pkg == null) {
            return false;
        }
        for (String p : ALWAYS_VISIBLE) {
            if (p.equals(pkg)) {
                return true;
            }
        }
        // 兜底：任何 provider / webview 相关的包都放行
        String lower = pkg.toLowerCase();
        if (lower.contains("webview") || lower.contains("trichrome")) {
            return true;
        }
        return false;
    }
}
