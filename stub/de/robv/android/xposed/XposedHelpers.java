package de.robv.android.xposed;

import java.lang.reflect.Member;

/** Xposed API stub —— 编译期用，运行时不打包 */
public final class XposedHelpers {

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            String className, ClassLoader classLoader,
            String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static Class<?> findClass(String name, ClassLoader cl) {
        return null;
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return null;
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return null;
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return null;
    }

    public static Class<?> findClassIfExists(String name, ClassLoader cl) {
        return null;
    }
}
