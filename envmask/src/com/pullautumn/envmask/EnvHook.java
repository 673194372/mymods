package com.pullautumn.envmask;

import android.os.Build;

import java.io.File;
import java.net.NetworkInterface;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 模拟器 / 多开 / 无障碍 / 代理 / 蓝牙 检测拦截。
 */
final class EnvHook {

    private static final String TAG = "[RunEnv/Env]";

    /** 模拟器特征文件 */
    private static final String[] EMU_PATHS = {
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props",
            "/dev/goldfish_pipe",
            "/sys/class/misc/qemud",
            "/system/lib64/libgoldfish.so",
            "/vendor/lib64/hw/goldfish",
    };

    /** 模拟器特征属性 */
    private static final String[] EMU_PROPS = {
            "ro.kernel.qemu",
            "ro.hardware",
            "ro.boot.qemu",
            "ro.kernel.qemu.gles",
            "ro.build.host",
    };

    private EnvHook() {
    }

    static void install(ClassLoader cl, Config cfg) {
        if (cfg.hideEmulator) {
            hookEmulator(cl);
        }
        if (cfg.hideAccessibility) {
            hookAccessibility(cl);
        }
        if (cfg.hideProxy) {
            hookProxy();
        }
        if (cfg.hideBluetoothMac) {
            hookBluetoothMac(cl);
        }
        XposedBridge.log(TAG + " 环境钩子已安装"
                + " (emu=" + cfg.hideEmulator
                + ", a11y=" + cfg.hideAccessibility
                + ", proxy=" + cfg.hideProxy
                + ", bt=" + cfg.hideBluetoothMac + ")");
    }

    // ==================== 模拟器 ====================

    private static void hookEmulator(ClassLoader cl) {
        // ① 模拟器特征文件
        XC_MethodHook fileGuard = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof File)) {
                    return;
                }
                String path;
                try {
                    path = ((File) param.thisObject).getAbsolutePath();
                } catch (Throwable t) {
                    return;
                }
                for (String p : EMU_PATHS) {
                    if (path.equals(p) || path.startsWith(p)) {
                        param.setResult(Boolean.FALSE);
                        return;
                    }
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(File.class, "exists", fileGuard);
        } catch (Throwable ignored) {
        }

        // ② Build 字段伪造（FINGERPRINT / MODEL / MANUFACTURER / HARDWARE 等）
        // Build 的静态 final 字段在类初始化后无法直接改，
        // 用 Field.get 拦截（RootHook 也 hook 了 Field.get，这里补充模拟器相关）
        try {
            XC_MethodHook fieldGuard = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof java.lang.reflect.Field)) {
                        return;
                    }
                    java.lang.reflect.Field f = (java.lang.reflect.Field) param.thisObject;
                    if (!f.getDeclaringClass().getName().equals("android.os.Build")) {
                        return;
                    }
                    String name = f.getName();
                    Object real = param.getResult();
                    if (!(real instanceof String)) {
                        return;
                    }
                    String v = (String) real;
                    if (fakeBuild(name, v) != null) {
                        param.setResult(fakeBuild(name, v));
                    }
                }
            };
            XposedHelpers.findAndHookMethod(java.lang.reflect.Field.class, "get", Object.class, fieldGuard);
        } catch (Throwable ignored) {
        }

        // ③ SystemProperties 的 qemu / hardware 属性
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", cl);
            XC_MethodHook propGuard = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = firstString(param.args);
                    if (key == null) {
                        return;
                    }
                    if ("ro.kernel.qemu".equals(key)) {
                        param.setResult("0");
                    } else if ("ro.hardware".equals(key)) {
                        param.setResult("qcom");
                    } else if ("ro.boot.qemu".equals(key)) {
                        param.setResult("0");
                    }
                }
            };
            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, propGuard);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class, propGuard);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    /** 把模拟器特征值替换成真机值 */
    private static String fakeBuild(String field, String real) {
        String lower = real == null ? "" : real.toLowerCase();
        boolean suspicious = lower.contains("generic") || lower.contains("sdk")
                || lower.contains("emulator") || lower.contains("goldfish")
                || lower.contains("ranchu") || lower.contains("vbox")
                || lower.contains("genymotion") || lower.contains("unknown")
                || lower.contains("google_sdk");

        if ("FINGERPRINT".equals(field) && suspicious) {
            return "Xiaomi/houji/houji:14/UKQ1.231003.002/OS2.0:user/release-keys";
        }
        if ("MODEL".equals(field) && suspicious) {
            return "23127PN0CC";
        }
        if ("MANUFACTURER".equals(field) && suspicious) {
            return "Xiaomi";
        }
        if ("BRAND".equals(field) && suspicious) {
            return "Xiaomi";
        }
        if ("DEVICE".equals(field) && suspicious) {
            return "houji";
        }
        if ("PRODUCT".equals(field) && suspicious) {
            return "houji";
        }
        if ("HARDWARE".equals(field) && suspicious) {
            return "qcom";
        }
        if ("BOARD".equals(field) && suspicious) {
            return "kalama";
        }
        if ("HOST".equals(field) && (lower.contains("build") || suspicious)) {
            return "xiaomi-host";
        }
        return null;
    }

    // ==================== 无障碍 ====================

    private static void hookAccessibility(ClassLoader cl) {
        try {
            XC_MethodHook guard = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = firstString(param.args);
                    if (key == null) {
                        return;
                    }
                    if ("enabled_accessibility_services".equals(key)) {
                        Object real = param.getResult();
                        if (real != null && String.valueOf(real).length() > 0) {
                            param.setResult("");
                            XposedBridge.log(TAG + " 无障碍服务列表 → 空");
                        }
                    } else if ("accessibility_enabled".equals(key)) {
                        Object real = param.getResult();
                        if (real != null && !"0".equals(String.valueOf(real))) {
                            param.setResult(0);
                        }
                    }
                }
            };
            Class<?> secure = XposedHelpers.findClass("android.provider.Settings$Secure", cl);
            try {
                XposedHelpers.findAndHookMethod(secure, "getString",
                        android.content.ContentResolver.class, String.class, guard);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(secure, "getInt",
                        android.content.ContentResolver.class, String.class, guard);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    // ==================== 代理 ====================

    /**
     * 拦截 System.getProperty("http.proxyHost") 等。
     */
    private static void hookProxy() {
        try {
            XC_MethodHook guard = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String key = firstString(param.args);
                    if (key == null) {
                        return;
                    }
                    if (key.startsWith("http.proxy") || key.startsWith("https.proxy")
                            || key.startsWith("socksProxy")) {
                        Object real = param.getResult();
                        if (real != null && String.valueOf(real).length() > 0) {
                            param.setResult(null);
                            XposedBridge.log(TAG + " 代理属性 " + key + " → null");
                        }
                    }
                }
            };
            XposedHelpers.findAndHookMethod(System.class, "getProperty", String.class, guard);
            XposedHelpers.findAndHookMethod(System.class, "getProperty",
                    String.class, String.class, guard);
        } catch (Throwable ignored) {
        }
    }

    // ==================== 蓝牙 MAC ====================

    private static void hookBluetoothMac(ClassLoader cl) {
        try {
            Class<?> bt = XposedHelpers.findClass("android.bluetooth.BluetoothAdapter", cl);
            XC_MethodHook guard = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (r instanceof String) {
                        String mac = (String) r;
                        // 02:00:00:00:00:00 是 Android 6+ 的"无法获取"占位值
                        // 真机常见是随机化的，这里给一个固定的
                        if ("02:00:00:00:00:00".equals(mac)) {
                            param.setResult("3C:5A:B4:11:22:33");
                        }
                    }
                }
            };
            try {
                XposedHelpers.findAndHookMethod(bt, "getAddress", guard);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }

        // 网络接口 MAC
        try {
            XC_MethodHook niGuard = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (!(r instanceof byte[])) {
                        return;
                    }
                    byte[] mac = (byte[]) r;
                    if (mac.length != 6) {
                        return;
                    }
                    // 全 0 或 02:00:00 这类占位值替换
                    boolean placeholder = true;
                    for (byte b : mac) {
                        if (b != 0 && b != 2) {
                            placeholder = false;
                            break;
                        }
                    }
                    if (placeholder) {
                        param.setResult(new byte[]{(byte) 0x3C, (byte) 0x5A,
                                (byte) 0xB4, 0x11, 0x22, 0x33});
                    }
                }
            };
            XposedHelpers.findAndHookMethod(NetworkInterface.class, "getHardwareAddress", niGuard);
        } catch (Throwable ignored) {
        }
    }

    // ==================== 工具 ====================

    private static String firstString(Object[] args) {
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
}
