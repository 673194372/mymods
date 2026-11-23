package com.pullautumn.apphider;

import java.util.LinkedHashSet;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

/**
 * 配置加载器 —— 黑名单模式。
 *
 * 只隐藏 Config.HIDDEN 里明确列出的包名，其余全部放行。
 *
 * 为什么不用白名单：
 *   白名单是「除列表外全藏」，需要列表覆盖目标应用的全部系统依赖，
 *   否则一旦漏掉 WebView provider、Provider、输入法之类，
 *   目标应用会直接崩溃。实际维护成本极高。
 *
 *   黑名单只藏明确要藏的东西，不会误伤，稳定性优先。
 *
 * 配置为何编译进 dex：
 *   Android 11+ 分区存储下，目标应用进程读不到模块自己
 *   /sdcard/Android/data/ 下的文件，只能编译进 APK。
 */
final class ConfigLoader {

    private static final String TAG = "[AppHider/Cfg]";

    private ConfigLoader() {
    }

    static final class Result {
        final Set<String> hidden = new LinkedHashSet<String>();
        final Set<String> visible = new LinkedHashSet<String>();
        boolean isWhitelist = false;
        String source = "内置";

        boolean isEmpty() {
            return hidden.isEmpty() && visible.isEmpty();
        }
    }

    static Result load() {
        Result r = new Result();
        r.isWhitelist = false;
        r.source = "编译内置黑名单";

        for (String p : Config.HIDDEN) {
            if (p == null || p.length() == 0) {
                continue;
            }
            r.hidden.add(p);
        }

        XposedBridge.log(TAG + " 黑名单模式，共 " + r.hidden.size() + " 项");
        return r;
    }
}
