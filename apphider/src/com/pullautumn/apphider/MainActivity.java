package com.pullautumn.apphider;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 只读信息界面。配置已编译进 dex，运行时不改。
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
        ver.setText("v1.0");
        ver.setTextSize(11);
        ver.setPadding(0, dp(2), 0, dp(18));
        root.addView(ver);

        TextView info = new TextView(this);
        info.setTextSize(13);
        info.setLineSpacing(0, 1.35f);

        StringBuilder sb = new StringBuilder();
        sb.append("当前规则\n\n");
        sb.append("模式    ").append(WhitelistData.IS_WHITELIST ? "白名单" : "黑名单").append("\n");
        sb.append("条目    ").append(WhitelistData.PACKAGES.length).append(" 个\n\n");

        if (WhitelistData.IS_WHITELIST) {
            sb.append("白名单表示：只有列表内应用会被目标应用看到，\n");
            sb.append("其余一律不出现。\n\n");
        } else {
            sb.append("黑名单表示：列表内应用不会被目标应用看到。\n\n");
        }

        sb.append("生效方式\n\n");
        sb.append("1  在 FPA 中 patch 目标应用\n");
        sb.append("2  patch 配置里勾选本模块\n");
        sb.append("3  安装 patched 版并打开\n\n");

        sb.append("规则改动\n\n");
        sb.append("规则随 APK 编译固定。修改步骤：\n");
        sb.append("编辑配置 → 重新生成数据 → 重新构建 → 重新 patch\n\n");

        sb.append("列表预览\n\n");
        int n = Math.min(40, WhitelistData.PACKAGES.length);
        for (int i = 0; i < n; i++) {
            sb.append(i + 1).append(". ").append(WhitelistData.PACKAGES[i]).append("\n");
        }
        if (WhitelistData.PACKAGES.length > n) {
            sb.append("… 其余 ").append(WhitelistData.PACKAGES.length - n).append(" 项\n");
        }

        info.setText(sb.toString());
        root.addView(info);

        scroll.addView(root);
        setContentView(scroll);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
