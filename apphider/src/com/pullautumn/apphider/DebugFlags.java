package com.pullautumn.apphider;

/**
 * 诊断开关。
 *
 * 用来二分定位崩溃来源。每次只开一项，找出是哪层 hook 导致的。
 *
 * 已知现象：
 *   全开 → WebView 报 Already registered a list of actions
 *   需要找出是哪一层触发的
 */
final class DebugFlags {

    /** 只打日志，不装任何 hook。设为 true 可验证"是不是模块的锅" */
    static final boolean DIAGNOSTIC_ONLY = false;

    /** PackageManager 过滤 —— 核心功能，通常是安全的 */
    static final boolean ENABLE_PM = true;

    /** ActivityManager 过滤 —— 关掉试试 */
    static final boolean ENABLE_AM = false;

    /** 文件系统过滤 —— 最可疑，hook File.exists 影响面极大，先关掉 */
    static final boolean ENABLE_FS = false;

    private DebugFlags() {
    }
}
