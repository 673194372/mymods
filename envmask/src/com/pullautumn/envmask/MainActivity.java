package com.pullautumn.envmask;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 只读信息界面。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("运行环境适配");
        title.setTextSize(22);
        root.addView(title);

        TextView ver = new TextView(this);
        ver.setText("v1.0");
        ver.setTextSize(11);
        ver.setPadding(0, dp(2), 0, dp(18));
        root.addView(ver);

        TextView body = new TextView(this);
        body.setTextSize(13);
        body.setLineSpacing(0, 1.35f);
        body.setText(build());
        root.addView(body);

        scroll.addView(root);
        setContentView(scroll);
    }

    private String build() {
        Config c = Config.load();
        StringBuilder sb = new StringBuilder();

        sb.append("适配项\n\n");
        line(sb, c.hideDevOptions, "调试开关读取",
                "adb_enabled / development_settings_enabled / adb_wifi_enabled / adb_tcp_port");
        line(sb, c.hideDevOptions, "调试属性读取",
                "ro.debuggable / ro.adb.secure / init.svc.adbd / sys.usb.config");
        line(sb, c.hideRoot, "特权路径探测", "su 与 magisk 相关路径");
        line(sb, c.hideRoot, "特权命令调用", "Runtime.exec / ProcessBuilder");
        line(sb, c.hideRootApps, "管理类应用可见性", "相关包名从应用列表移除");
        line(sb, c.hideHook, "注入框架痕迹", "类名查找与堆栈帧");
        line(sb, c.hideFrida, "动态插桩痕迹", "类名与线程名");
        line(sb, c.hideEmulator, "虚拟硬件特征", "路径、Build 字段、系统属性");
        line(sb, c.hideAccessibility, "无障碍状态", "服务列表与开关");
        line(sb, c.hideProxy, "网络代理配置", "http.proxy* 属性");
        line(sb, c.hideBluetoothMac, "硬件地址", "蓝牙与网卡 MAC");

        sb.append("\n生效方式\n\n");
        sb.append("1  在 FPA 中 patch 目标应用\n");
        sb.append("2  patch 配置里勾选本模块\n");
        sb.append("3  安装 patched 版并打开\n\n");

        sb.append("说明\n\n");
        sb.append("本模块只改变目标应用进程内读到的返回值，\n");
        sb.append("不改动系统设置，也不影响其他应用。");

        return sb.toString();
    }

    private void line(StringBuilder sb, boolean on, String name, String detail) {
        sb.append(on ? "✓  " : "✗  ").append(name).append("\n");
        sb.append("      ").append(detail).append("\n");
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
