package com.pullautumn.apphider;

import java.util.LinkedHashSet;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

/**
 * 配置加载器。
 *
 * 优先级：
 *   1) 编译进 dex 的 WhitelistData（从 HMA JSON 自动生成）
 *   2) 内置默认黑名单 Config.DEFAULT_HIDDEN
 *
 * 为什么不用读文件：
 *   Android 11+ 分区存储下，目标 App 进程读不到
 *   模块自己 /sdcard/Android/data/ 下的文件（Permission denied）。
 *   所以配置必须编译进 dex。
 *
 * 要改配置：改 隐藏应用列表白名单配置.json → 重新跑 _gen_whitelist.py
 *          → 提交 → 重新构建。
 */
final class ConfigLoader {

    private static final String TAG = "[AppHider/Cfg]";

    private ConfigLoader() {
    }

    static final class Result {
        final Set<String> hidden = new LinkedHashSet<String>();
        final Set<String> visible = new LinkedHashSet<String>();
        boolean isWhitelist = false;
        String source = "内置默认";

        boolean isEmpty() {
            return hidden.isEmpty() && visible.isEmpty();
        }
    }

    static Result load() {
        Result r = loadFromCompiledData();
        if (r != null && !r.isEmpty()) {
            return r;
        }

        Result def = new Result();
        for (String p : Config.DEFAULT_HIDDEN) {
            def.hidden.add(p);
        }
        def.source = "内置默认黑名单";
        XposedBridge.log(TAG + " 使用内置默认列表，共 " + def.hidden.size() + " 项");
        return def;
    }

    /** 从编译进 dex 的数据加载 */
    private static Result loadFromCompiledData() {
        try {
            String[] pkgs = WhitelistData.PACKAGES;
            if (pkgs == null || pkgs.length == 0) {
                return null;
            }

            Result r = new Result();
            r.isWhitelist = WhitelistData.IS_WHITELIST;
            r.source = "编译内置 (HMA 白名单)";

            for (String p : pkgs) {
                if (p == null || p.length() == 0) {
                    continue;
                }
                if (r.isWhitelist) {
                    r.visible.add(p);
                } else {
                    r.hidden.add(p);
                }
            }

            XposedBridge.log(TAG + " 已加载编译内置配置: 模式="
                    + (r.isWhitelist ? "白名单" : "黑名单")
                    + ", 条目=" + pkgs.length);
            return r;
        } catch (Throwable t) {
            XposedBridge.log(TAG + " 读取编译数据失败: " + t.getMessage());
            return null;
        }
    }
}
