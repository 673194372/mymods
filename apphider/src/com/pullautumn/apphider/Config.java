package com.pullautumn.apphider;

/**
 * 内置默认黑名单（外部 JSON 读不到时使用）。
 *
 * 支持 "com.foo.*" 通配。
 */
final class Config {

    static final String PREF_FILE = "apphider_conf";
    static final String PREF_KEY = "hidden_packages";

    static final String[] DEFAULT_HIDDEN = {
            // 框架类
            "com.shizuposed.manager",
            "com.tsng.hidemyapplist",
            "com.xposed.hider",
            "de.robv.android.xposed.installer",
            "org.lsposed.manager",
            "org.lsposed.lspatch",
            "top.nkbe.npatch",
            "fun.fpa",

            // 工具类
            "bin.mt.plus",
            "bin.mt.plus.canary",
            "bin.mt.termex",
            "com.termux",
            "org.autojs.autojs6",

            // 检测器
            "icu.nullptr.applistdetector",
            "com.virb3.trustmealready",
            "com.junge.justtrustmepp",
    };

    private Config() {
    }
}
