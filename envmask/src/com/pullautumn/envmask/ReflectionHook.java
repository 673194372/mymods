package com.pullautumn.envmask;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 反射层统一 hook。
 *
 * 全模块只在这里 hook 一次 Field.get / Field.get(Object)，
 * 其它类不再重复 hook —— 重复 hook 会形成递归链：
 *
 *   Hook① 进入 → 内部读字段 → 触发 Hook②
 *              → Hook② 内部读字段 → 触发 Hook③ ...
 *
 * 实测后果：EventBus 用反射探测 Android 环境时被干扰，
 * 误判"组件缺失"并抛出：
 *   RuntimeException: It looks like you are using EventBus on Android
 *
 * 本类同时处理：
 *   · Build.TAGS / Build.TYPE / Build.DEBUGGABLE 伪造
 *   · 模拟器特征字段伪造
 */
final class ReflectionHook {

    private static final String TAG = "[RunEnv/Reflect]";

    private ReflectionHook() {
    }

    static void install(ClassLoader cl, Config cfg) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // 重入保护：已在处理中就直通
                if (!Reentry.enterFieldGet()) {
                    return;
                }
                try {
                    handleFieldGet(param, cfg);
                } catch (Throwable ignored) {
                } finally {
                    Reentry.exitFieldGet();
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(
                    Field.class, "get", Object.class, hook);
            XposedHelpers.findAndHookMethod(
                    Field.class, "get", hook);
            XposedBridge.log(TAG + " Field.get 已 hook（含重入保护）");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " 安装失败: " + t.getMessage());
        }
    }

    private static void handleFieldGet(XC_MethodHook.MethodHookParam param,
                                       Config cfg) {
        Object self = param.thisObject;
        if (!(self instanceof Field)) {
            return;
        }
        Field f = (Field) self;
        Class<?> declaring = f.getDeclaringClass();
        String className = declaring == null ? "" : declaring.getName();
        String fieldName = f.getName();

        // 先挡住递归：不要再通过 getObjectField 之类触发本 hook
        // 直接使用 f.get() 的原始行为靠 param 结果判断，
        // 这里用 afterHookedMethod 更安全，但 before 阶段拿不到值。
        // 折中：只做无条件伪造，不读取原值。
        // （读取原值会再次进入本方法，虽然被重入保护挡住，
        //   但多一层无谓调用，能免则免）

        if ("android.os.Build".equals(className)) {
            // Build.TAGS: test-keys → release-keys
            if ("TAGS".equals(fieldName) && cfg.hideRoot) {
                param.setResult("release-keys");
                return;
            }
            // Build.TYPE: userdebug → user
            if ("TYPE".equals(fieldName) && cfg.hideRoot) {
                param.setResult("user");
                return;
            }
            // Build.DEBUGGABLE: 非 0 → 0
            if ("DEBUGGABLE".equals(fieldName) && cfg.hideRoot) {
                param.setResult(0);
                return;
            }
            // 模拟器特征字段
            if (cfg.hideEmulator) {
                String fake = fakeEmulatorField(fieldName);
                if (fake != null) {
                    param.setResult(fake);
                }
            }
        }
    }

    /** 模拟器相关的 Build 字段伪造值 */
    private static String fakeEmulatorField(String name) {
        if ("FINGERPRINT".equals(name)) {
            return "Xiaomi/houji/houji:15/AQ3A.240812.001/OS2.0:user/release-keys";
        }
        if ("MODEL".equals(name)) {
            return "23127PN0CC";
        }
        if ("MANUFACTURER".equals(name) || "BRAND".equals(name)) {
            return "Xiaomi";
        }
        if ("DEVICE".equals(name)) {
            return "houji";
        }
        if ("PRODUCT".equals(name)) {
            return "houji";
        }
        if ("HARDWARE".equals(name)) {
            return "qcom";
        }
        if ("BOARD".equals(name)) {
            return "kalama";
        }
        if ("HOST".equals(name)) {
            return "xiaomi-host";
        }
        return null;
    }
}
