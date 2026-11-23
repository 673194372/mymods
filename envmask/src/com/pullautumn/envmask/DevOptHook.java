package com.pullautumn.envmask;

import android.provider.Settings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 开发者选项 / USB 调试 / 无线调试 检测拦截。
 *
 * 这是本模块的重点，原版没有覆盖。
 *
 * 检测点：
 *   ① Settings.Global.ADB_ENABLED              USB 调试开关
 *   ② Settings.Global.DEVELOPMENT_SETTINGS_ENABLED  开发者选项
 *   ③ Settings.Global.ADB_WIFI_ENABLED         无线调试（Android 11+）
 *   ④ Settings.Global.ADB_TCP_PORT             无线调试端口
 *   ⑤ Settings.Secure.ADB_ENABLED              老版本位置
 *   ⑥ SystemProperties adb 相关属性
 *   ⑦ Settings.Secure / Global 的 getInt/getLong/getString 通用兜底
 *
 * 关键：hook 的是 Settings.Secure / Settings.Global 的静态方法，
 * 这些方法在目标 App 进程内被调用，FPA 能拦。
 */
final class DevOptHook {

    private static final String TAG = "[RunEnv/Dev]";

    /** 需要伪造为 0 的 Settings.Global 键 */
    private static final String[] GLOBAL_ZERO_KEYS = {
            "adb_enabled",
            "development_settings_enabled",
            "adb_wifi_enabled",
            "adb_tcp_port",              // 端口，也置 0
            "debug_view_attributes",
            "debug_app",
            "show_processes",
            "always_finish_activities",
            "wait_for_debugger",
            "verifier_verify_adb_installs",
            "package_verifier_enable",
            "stay_on_while_plugged_in",
            "wifi_display_on",
            "device_provisioned",
    };

    /** 需要伪造为 0 的 Settings.Secure 键 */
    private static final String[] SECURE_ZERO_KEYS = {
            "adb_enabled",
            "development_settings_enabled",
            "mock_location",
            "enabled_accessibility_services",
            "accessibility_enabled",
            "accessibility_display_magnification_enabled",
            "touch_exploration_enabled",
            "install_non_market_apps",
    };

    private DevOptHook() {
    }

    static void install(ClassLoader cl, Config cfg) {
        hookSettingsGlobal(cl);
        hookSettingsSecure(cl);
        hookSystemProperties(cl);
        XposedBridge.log(TAG + " 开发者选项/USB调试 钩子已安装");
    }

    // ==================== Settings.Global ====================

    private static void hookSettingsGlobal(ClassLoader cl) {
        try {
            Class<?> global = XposedHelpers.findClass("android.provider.Settings$Global", cl);

            // getInt(ContentResolver, String) / getInt(ContentResolver, String, int)
            hookSettingsGetter(global, "getInt", GLOBAL_ZERO_KEYS, 0);
            hookSettingsGetter(global, "getLong", GLOBAL_ZERO_KEYS, 0L);
            hookSettingsGetter(global, "getFloat", GLOBAL_ZERO_KEYS, 0f);
            hookSettingsString(global, GLOBAL_ZERO_KEYS, "0");

            // getStringForUser / getIntForUser（部分 App 用这个）
            hookSettingsForUser(global, "getIntForUser", GLOBAL_ZERO_KEYS, 0);
            hookSettingsForUser(global, "getLongForUser", GLOBAL_ZERO_KEYS, 0L);
            hookSettingsStringForUser(global, GLOBAL_ZERO_KEYS, "0");

        } catch (Throwable t) {
            XposedBridge.log(TAG + " Settings.Global hook 失败: " + t.getMessage());
        }
    }

    // ==================== Settings.Secure ====================

    private static void hookSettingsSecure(ClassLoader cl) {
        try {
            Class<?> secure = XposedHelpers.findClass("android.provider.Settings$Secure", cl);

            hookSettingsGetter(secure, "getInt", SECURE_ZERO_KEYS, 0);
            hookSettingsGetter(secure, "getLong", SECURE_ZERO_KEYS, 0L);
            hookSettingsGetter(secure, "getFloat", SECURE_ZERO_KEYS, 0f);
            hookSettingsString(secure, SECURE_ZERO_KEYS, "");

            hookSettingsForUser(secure, "getIntForUser", SECURE_ZERO_KEYS, 0);
            hookSettingsForUser(secure, "getLongForUser", SECURE_ZERO_KEYS, 0L);
            hookSettingsStringForUser(secure, SECURE_ZERO_KEYS, "");

        } catch (Throwable t) {
            XposedBridge.log(TAG + " Settings.Secure hook 失败: " + t.getMessage());
        }
    }

    // ==================== 通用 Settings getter ====================

    /**
     * hook getInt(ContentResolver, String) 与 getInt(ContentResolver, String, int)
     */
    private static void hookSettingsGetter(Class<?> settingsClass, String methodName,
                                           final String[] keys, final Object fakeValue) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = keyArg(param.args);
                if (key != null && matches(key, keys)) {
                    Object real = param.getResult();
                    // 只在我们该伪造的时候改（原值非 0 才改，减少无谓改动）
                    param.setResult(fakeValue);
                    XposedBridge.log(TAG + " " + key + ": " + real + " → " + fakeValue);
                }
            }
        };

        // getInt(ContentResolver, String)
        try {
            Class<?>[] p1 = {android.content.ContentResolver.class, String.class};
            XposedHelpers.findAndHookMethod(settingsClass, methodName, append(p1, hook));
        } catch (Throwable ignored) {
        }
        // getInt(ContentResolver, String, int)
        try {
            Class<?>[] p2 = {android.content.ContentResolver.class, String.class,
                    methodName.contains("Long") ? long.class : int.class};
            XposedHelpers.findAndHookMethod(settingsClass, methodName, append(p2, hook));
        } catch (Throwable ignored) {
        }
    }

    private static void hookSettingsString(Class<?> settingsClass,
                                           final String[] keys, final String fakeValue) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = keyArg(param.args);
                if (key != null && matches(key, keys)) {
                    param.setResult(fakeValue);
                    XposedBridge.log(TAG + " " + key + " (String) → " + fakeValue);
                }
            }
        };
        try {
            Class<?>[] p = {android.content.ContentResolver.class, String.class};
            XposedHelpers.findAndHookMethod(settingsClass, "getString", append(p, hook));
        } catch (Throwable ignored) {
        }
    }

    private static void hookSettingsForUser(Class<?> settingsClass, String methodName,
                                            final String[] keys, final Object fakeValue) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = keyArg(param.args);
                if (key != null && matches(key, keys)) {
                    param.setResult(fakeValue);
                    XposedBridge.log(TAG + " " + key + " (ForUser) → " + fakeValue);
                }
            }
        };
        try {
            Class<?>[] p = {android.content.ContentResolver.class, String.class,
                    methodName.contains("Long") ? long.class : int.class, int.class};
            XposedHelpers.findAndHookMethod(settingsClass, methodName, append(p, hook));
        } catch (Throwable ignored) {
        }
    }

    private static void hookSettingsStringForUser(Class<?> settingsClass,
                                                  final String[] keys, final String fakeValue) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String key = keyArg(param.args);
                if (key != null && matches(key, keys)) {
                    param.setResult(fakeValue);
                }
            }
        };
        try {
            Class<?>[] p = {android.content.ContentResolver.class, String.class, int.class};
            XposedHelpers.findAndHookMethod(settingsClass, "getStringForUser", append(p, hook));
        } catch (Throwable ignored) {
        }
    }

    // ==================== SystemProperties ====================

    /**
     * 拦截 android.os.SystemProperties.get() / getInt() 的 adb 属性。
     *
     * 有些检测直接读：
     *   getprop ro.debuggable
     *   getprop ro.adb.secure
     *   getprop service.adb.root
     */
    private static void hookSystemProperties(ClassLoader cl) {
        try {
            final Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", cl);

            XC_MethodHook getHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = keyArg(param.args);
                    if (key == null) {
                        return;
                    }
                    String fake = fakeProp(key);
                    if (fake != null) {
                        Object real = param.getResult();
                        param.setResult(fake);
                        XposedBridge.log(TAG + " prop " + key + ": " + real + " → " + fake);
                    }
                }
            };

            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, getHook);
            } catch (Throwable ignored) {
            }
            try {
                // get(String, String)
                XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class, getHook);
            } catch (Throwable ignored) {
            }

            XC_MethodHook getIntHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = keyArg(param.args);
                    if (key == null) {
                        return;
                    }
                    String fake = fakeProp(key);
                    if (fake != null) {
                        try {
                            param.setResult(Integer.parseInt(fake));
                        } catch (Throwable ignored) {
                        }
                    }
                }
            };
            try {
                XposedHelpers.findAndHookMethod(sp, "getInt", String.class, int.class, getIntHook);
            } catch (Throwable ignored) {
            }

            XposedBridge.log(TAG + " SystemProperties 钩子已安装");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " SystemProperties hook 失败: " + t.getMessage());
        }
    }

    /** 需要伪造的系统属性 */
    private static String fakeProp(String key) {
        if ("ro.debuggable".equals(key)) {
            return "0";
        }
        if ("ro.adb.secure".equals(key)) {
            return "1";
        }
        if ("service.adb.root".equals(key)) {
            return "0";
        }
        if ("persist.sys.usb.config".equals(key)) {
            return "mtp";
        }
        if ("sys.usb.config".equals(key)) {
            return "mtp";
        }
        if ("sys.usb.state".equals(key)) {
            return "mtp";
        }
        if ("init.svc.adbd".equals(key)) {
            return "stopped";
        }
        if ("ro.build.type".equals(key)) {
            return "user";
        }
        if ("ro.build.tags".equals(key)) {
            return "release-keys";
        }
        if ("ro.secure".equals(key)) {
            return "1";
        }
        return null;
    }

    // ==================== 工具 ====================

    private static String keyArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object a : args) {
            if (a instanceof String) {
                return (String) a;
            }
        }
        return null;
    }

    private static boolean matches(String key, String[] keys) {
        for (String k : keys) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** 把 callback 追加到参数类型数组末尾 */
    private static Object[] append(Class<?>[] types, Object callback) {
        Object[] out = new Object[types.length + 1];
        System.arraycopy(types, 0, out, 0, types.length);
        out[types.length] = callback;
        return out;
    }
}
