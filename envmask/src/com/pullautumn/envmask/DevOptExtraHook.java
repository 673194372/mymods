package com.pullautumn.envmask;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 开发者选项检测的补强层。
 *
 * DevOptHook 只拦了 Settings.Global/Secure 的 getInt/getString 静态方法，
 * 但检测方还有这些绕过途径：
 *
 *   ① Settings.Global.isDeveloperOptionsEnabled()   Android 14+ 新 API
 *   ② contentResolver.query(Settings.URI, ...)      直接查 ContentProvider
 *   ③ contentResolver.call(...)                     走 call 接口
 *   ④ ContentResolver.query 返回的 Cursor 内容
 *   ⑤ Build.TYPE 字段（只拦了 ro.build.type 属性不够）
 *   ⑥ ActivityManager 的开发者相关服务
 *
 * 本类逐个补上。
 */
final class DevOptExtraHook {

    private static final String TAG = "[RunEnv/Dev+]";

    /** 需要从 Cursor 结果里剔除或改写的 Settings 键 */
    private static final String[] BLOCKED_KEYS = {
            "adb_enabled",
            "development_settings_enabled",
            "adb_wifi_enabled",
            "adb_tcp_port",
            "debug_app",
            "debug_view_attributes",
            "wait_for_debugger",
            "show_processes",
            "always_finish_activities",
            "enabled_accessibility_services",
            "accessibility_enabled",
            "mock_location",
            "install_non_market_apps",
            "stay_on_while_plugged_in",
    };

    private DevOptExtraHook() {
    }

    static void install(ClassLoader cl, Config cfg) {
        if (!cfg.hideDevOptions) {
            return;
        }
        hookIsDeveloperOptionsEnabled(cl);
        hookContentResolverQuery(cl);
        hookContentResolverCall(cl);
        hookBuildType(cl);
        hookGetStringDirect(cl);
        XposedBridge.log(TAG + " 开发者选项补强钩子已安装");
    }

    // ==================== ① isDeveloperOptionsEnabled ====================

    /**
     * Android 14 (API 34) 新增：
     *   Settings.Global.isDeveloperOptionsEnabled(Context)
     *
     * 部分检测直接用这个，比读 setting 更省事。
     */
    private static void hookIsDeveloperOptionsEnabled(ClassLoader cl) {
        try {
            Class<?> global = XposedHelpers.findClass("android.provider.Settings$Global", cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (Boolean.TRUE.equals(r)) {
                        param.setResult(Boolean.FALSE);
                        XposedBridge.log(TAG + " isDeveloperOptionsEnabled() → false");
                    }
                }
            };
            try {
                XposedHelpers.findAndHookMethod(global, "isDeveloperOptionsEnabled",
                        android.content.Context.class, hook);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(global, "isDeveloperOptionsEnabled", hook);
            } catch (Throwable ignored) {
            }
            // DeviceConfig 也有类似入口
            try {
                Class<?> deviceConfig = XposedHelpers.findClass(
                        "android.provider.DeviceConfig", cl);
                XposedHelpers.findAndHookMethod(deviceConfig, "getBoolean",
                        String.class, String.class, boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                String ns = (String) param.args[0];
                                String key = (String) param.args[1];
                                if (key != null && isBlockedKey(key)) {
                                    param.setResult(Boolean.FALSE);
                                }
                            }
                        });
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " isDeveloperOptionsEnabled hook 失败: " + t.getMessage());
        }
    }

    // ==================== ② ContentResolver.query ====================

    /**
     * 直接查 ContentProvider：
     *   cr.query(Settings.Global.CONTENT_URI, null, null, null, null)
     *   cr.query(Uri.parse("content://settings/global"), ...)
     *
     * 这种完全绕过 getInt/getString。做法：hook 后把 Cursor 里
     * 目标键的行过滤掉 / 值改掉。
     */
    private static void hookContentResolverQuery(ClassLoader cl) {
        XC_MethodHook filter = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object r = param.getResult();
                if (!(r instanceof Cursor)) {
                    return;
                }
                // 判断是否是 settings 相关 URI
                Uri uri = null;
                try {
                    if (param.args != null && param.args.length > 0
                            && param.args[0] instanceof Uri) {
                        uri = (Uri) param.args[0];
                    }
                } catch (Throwable ignored) {
                }
                if (uri == null) {
                    return;
                }
                String u = uri.toString();
                if (!u.contains("settings")) {
                    return;
                }

                Cursor c = (Cursor) r;
                try {
                    // 把 Cursor 转成可过滤的实现
                    param.setResult(wrapCursor(c));
                    XposedBridge.log(TAG + " ContentResolver.query(" + u + ") → 已过滤");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + " wrap cursor 失败: " + t.getMessage());
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(
                    ContentResolver.class, "query",
                    Uri.class, String[].class, String.class, String[].class, String.class,
                    filter);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(
                    ContentResolver.class, "query",
                    Uri.class, String[].class, String.class, String[].class, String.class,
                    android.os.CancellationSignal.class, filter);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 用一个包装 Cursor 过滤结果。
     *
     * 策略：读时如果发现是目标键，把值改成 0 / 空。
     * 因为 Cursor 接口方法太多，这里用 MatrixCursor 重建。
     */
    private static Cursor wrapCursor(Cursor src) {
        try {
            String[] cols = src.getColumnNames();
            if (cols == null || cols.length == 0) {
                return src;
            }
            int nameIdx = -1;
            int valueIdx = -1;
            for (int i = 0; i < cols.length; i++) {
                if ("name".equals(cols[i])) {
                    nameIdx = i;
                } else if ("value".equals(cols[i])) {
                    valueIdx = i;
                }
            }
            // 不是 settings 的 key/value 结构，原样返回
            if (nameIdx < 0 || valueIdx < 0) {
                return src;
            }

            android.database.MatrixCursor out = new android.database.MatrixCursor(cols);
            src.moveToFirst();
            while (!src.isAfterLast()) {
                String name = src.getString(nameIdx);
                if (name != null && isBlockedKey(name)) {
                    // 改写 value 为 "0" / "false" / ""
                    Object[] row = new Object[cols.length];
                    for (int i = 0; i < cols.length; i++) {
                        if (i == valueIdx) {
                            row[i] = "0";
                            continue;
                        }
                        switch (src.getType(i)) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                row[i] = src.getLong(i);
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                row[i] = src.getDouble(i);
                                break;
                            case Cursor.FIELD_TYPE_NULL:
                                row[i] = null;
                                break;
                            default:
                                row[i] = src.getString(i);
                        }
                    }
                    out.addRow(row);
                } else {
                    Object[] row = new Object[cols.length];
                    for (int i = 0; i < cols.length; i++) {
                        switch (src.getType(i)) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                row[i] = src.getLong(i);
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                row[i] = src.getDouble(i);
                                break;
                            default:
                                row[i] = src.getString(i);
                        }
                    }
                    out.addRow(row);
                }
                src.moveToNext();
            }
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    // ==================== ③ ContentResolver.call ====================

    private static void hookContentResolverCall(ClassLoader cl) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String method = null;
                Uri uri = null;
                if (param.args != null && param.args.length >= 2) {
                    if (param.args[0] instanceof Uri) {
                        uri = (Uri) param.args[0];
                    }
                    if (param.args[1] instanceof String) {
                        method = (String) param.args[1];
                    }
                }
                if (uri != null && method != null
                        && uri.toString().contains("settings")) {
                    // 直接返回 null / 空 Bundle，表示"没有这个设置"
                    param.setResult(new android.os.Bundle());
                    XposedBridge.log(TAG + " ContentResolver.call("
                            + uri + ", " + method + ") → 空 Bundle");
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(
                    ContentResolver.class, "call",
                    Uri.class, String.class, String.class, android.os.Bundle.class,
                    hook);
        } catch (Throwable ignored) {
        }
    }

    // ==================== ④ Build.TYPE ====================

    /**
     * 只拦 ro.build.type 属性不够，还需拦 Build.TYPE 字段本身。
     *
     * Build.TYPE 是 static final String，编译进类常量池，
     * 运行时改字段不一定有效（内联优化）。
     * 这里 hook Field.get 兜底。
     */
    private static void hookBuildType(ClassLoader cl) {
        try {
            XC_MethodHook fieldHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof java.lang.reflect.Field)) {
                        return;
                    }
                    java.lang.reflect.Field f = (java.lang.reflect.Field) param.thisObject;
                    if (!"android.os.Build".equals(f.getDeclaringClass().getName())) {
                        return;
                    }
                    String name = f.getName();
                    Object real = param.getResult();
                    if ("TYPE".equals(name) && "userdebug".equals(real)) {
                        param.setResult("user");
                    } else if ("TAGS".equals(name) && real instanceof String
                            && ((String) real).contains("test-keys")) {
                        param.setResult("release-keys");
                    } else if ("DEBUGGABLE".equals(name) && real instanceof Integer
                            && ((Integer) real) != 0) {
                        param.setResult(0);
                    }
                }
            };
            XposedHelpers.findAndHookMethod(java.lang.reflect.Field.class, "get", Object.class, fieldHook);
        } catch (Throwable ignored) {
        }

        // Build.TYPE 也可能通过 Build.getSerial 这类方式被读，但那不是重点
    }

    // ==================== ⑤ getString 兜底 ====================

    /**
     * 补一层：Settings.Global.getString(ContentResolver, String)
     * 之后如果读到的值非空但应该为 0，改成 "0"。
     */
    private static void hookGetStringDirect(ClassLoader cl) {
        try {
            Class<?> global = XposedHelpers.findClass("android.provider.Settings$Global", cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = null;
                    if (param.args != null) {
                        for (Object a : param.args) {
                            if (a instanceof String) {
                                key = (String) a;
                                break;
                            }
                        }
                    }
                    if (key != null && isBlockedKey(key)) {
                        Object real = param.getResult();
                        if (real != null && !"0".equals(String.valueOf(real))
                                && !"".equals(String.valueOf(real))) {
                            param.setResult("0");
                        }
                    }
                }
            };
            try {
                XposedHelpers.findAndHookMethod(global, "getString",
                        ContentResolver.class, String.class, hook);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    // ==================== 工具 ====================

    private static boolean isBlockedKey(String key) {
        if (key == null) {
            return false;
        }
        for (String k : BLOCKED_KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
