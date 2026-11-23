package com.pullautumn.apphider;

/**
 * 黑名单：明确要隐藏的包名。
 *
 * 改这里 = 改隐藏范围。
 *
 * 支持通配：以 ".*" 结尾表示前缀匹配，例如 "com.foo.*"。
 * 不加通配则精确匹配。
 *
 * 原则：只列确定要藏的工具，绝不列任何系统组件。
 *       系统组件一旦被藏，目标应用会崩溃（WebView / Provider / 输入法等）。
 */
final class Config {

    static final String[] HIDDEN = {
            // ===== 免 root 框架 =====
            "com.shizuposed.manager",
            "fun.fpa",
            "top.nkbe.npatch",
            "org.lsposed.manager",
            "org.lsposed.lspatch",
            "de.robv.android.xposed.installer",

            // ===== 隐藏类模块 =====
            "com.tsng.hidemyapplist",
            "com.xposed.hider",

            // ===== 文件与终端工具 =====
            "bin.mt.plus",
            "bin.mt.plus.canary",
            "bin.mt.termex",
            "com.termux",
            "com.termux.api",
            "com.termux.boot",

            // ===== 自动化 =====
            "org.autojs.autojs",
            "org.autojs.autojs6",

            // ===== 检测工具（防止反被识别）=====
            "icu.nullptr.applistdetector",
            "com.virb3.trustmealready",
            "com.junge.justtrustmepp",
            "com.xposed.detector",

            // ===== 抓包与调试 =====
            "com.reqable.android",
            "com.guoshi.httpcanary",
            "com.guoshi.httpcanary.premium",
            "burp.burp",
            "com.minhui.networkcapture",

            // ===== 虚拟空间与多开 =====
            "com.oasisfeng.island",
            "com.lbe.parallel.intl",
            "com.excelliance.dualaid",
            "com.parallel.space.lite",

            // ===== 本模块自身（防止被检测到）=====
            "com.pullautumn.apphider",
            "com.pullautumn.envmask",
    };

    private Config() {
    }
}
