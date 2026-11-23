package com.pullautumn.apphider;

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
        title.setText("应用列表隐藏");
        title.setTextSize(22);
        root.addView(title);

        TextView ver = new TextView(this);
        ver.setText("v1.1 · 黑名单模式");
        ver.setTextSize(11);
        ver.setPadding(0, dp(2), 0, dp(18));
        root.addView(ver);

        TextView info = new TextView(this);
        info.setTextSize(13);
        info.setLineSpacing(0, 1.35f);
        info.setText(build());
        root.addView(info);

        scroll.addView(root);
        setContentView(scroll);
    }

    private String build() {
        StringBuilder sb = new StringBuilder();

        sb.append("规则\n\n");
        sb.append("模式    ").append("黑名单\n");
        sb.append("条目    ").append(Config.HIDDEN.length).append(" 个\n\n");
        sb.append("只隐藏下表中的包，其余一概放行。\n");
        sb.append("系统组件不受影响，不会误伤目标应用。\n\n");

        sb.append("生效方式\n\n");
        sb.append("1  在 FPA 中 patch 目标应用\n");
        sb.append("2  patch 配置里勾选本模块\n");
        sb.append("3  安装 patched 版并打开\n\n");

        sb.append("改动规则\n\n");
        sb.append("规则随 APK 编译固定。\n");
        sb.append("编辑 Config.java 后重新构建即可。\n\n");

        sb.append("隐藏清单\n\n");
        for (int i = 0; i < Config.HIDDEN.length; i++) {
            sb.append(i + 1).append(". ").append(Config.HIDDEN[i]).append("\n");
        }

        return sb.toString();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
