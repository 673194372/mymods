package de.robv.android.xposed.callbacks;

/** Xposed API stub —— 编译期用，运行时不打包 */
public class XC_LoadPackage {

    public static class LoadPackageParam {
        public String packageName;
        public ClassLoader classLoader;
        public Object appInfo;
        public boolean isFirstApplication;
        public String processName;
    }
}
