package com.pullautumn.envmask;

/**
 * 模块配置。全部编译期固定，运行时不读外部文件
 * （Android 11+ 分区存储下目标进程读不到配置）。
 */
final class Config {

    boolean hideDevOptions = true;      // 开发者选项 / USB 调试 / 无线调试
    boolean hideRoot = true;            // Root 检测
    boolean hideRootApps = true;        // 隐藏 root 管理应用包名
    boolean hideHook = true;            // Hook / Xposed 痕迹
    boolean hideFrida = true;           // Frida 痕迹
    boolean hideEmulator = true;        // 模拟器检测
    boolean hideMultiApp = true;        // 多开检测（通过包名/路径）
    boolean hideAccessibility = true;   // 无障碍服务检测
    boolean hideProxy = true;           // 抓包代理检测
    boolean hideBluetoothMac = true;    // 蓝牙 / MAC 检测

    static Config load() {
        Config c = new Config();
        // 留一个总开关的入口：想关掉某一项就改成 false
        return c;
    }

    /** 日志用的摘要 */
    String summary() {
        return "dev=" + hideDevOptions
                + " root=" + hideRoot
                + " rootApps=" + hideRootApps
                + " hook=" + hideHook
                + " frida=" + hideFrida
                + " emu=" + hideEmulator
                + " a11y=" + hideAccessibility
                + " proxy=" + hideProxy
                + " bt=" + hideBluetoothMac;
    }
}
