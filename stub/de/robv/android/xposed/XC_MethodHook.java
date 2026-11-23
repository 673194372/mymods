package de.robv.android.xposed;

/**
 * Xposed API stub —— 编译期用，运行时不打包。
 *
 * 注意：不能是 final，模块代码需要继承它来写 before/afterHookedMethod。
 */
public class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    /** 方法调用参数容器 */
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;

        public Object getResult() {
            return null;
        }

        public void setResult(Object r) {
        }

        public Throwable getThrowable() {
            return null;
        }

        public void setThrowable(Throwable t) {
        }
    }

    /** findAndHookMethod 的返回值，用于取消 hook */
    public class Unhook {
        public void unhook() {
        }
    }
}
