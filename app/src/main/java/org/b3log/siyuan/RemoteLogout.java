/*
 * SiYuan - From thought to insight, with agents
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.b3log.siyuan;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

/**
 * 退出登录 bridge（二开定制，新增文件，零冲突）。
 *
 * <p>前端页面（app.zip 里改过的 mobile/index.html）的"设置与帮助"分组里有
 * "退出登录"按钮，点击调用本 bridge 的 logout()：清除本机保存的地址和授权码
 * 并重启 App 回到登录框。</p>
 */
public final class RemoteLogout {

    public static final String BRIDGE_NAME = "RemoteLogoutBridge";

    private final Activity activity;

    public RemoteLogout(final Activity activity) {
        this.activity = activity;
    }

    /** 前端"退出登录"按钮点击时调用（运行在 WebView 的 JS 线程） */
    @JavascriptInterface
    public void logout() {
        new Handler(Looper.getMainLooper()).post(() -> {
            RemoteAuth.clearAuthCode(activity);
            RemoteSettings.saveOrigin(""); // 清空地址，回到首次登录状态
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null);
            } catch (final Exception ignored) {
            }
            // 干净重启：先结束当前 Activity，再延迟拉起启动页，避免 finish 牵连新 Activity
            activity.finish();
            activity.getWindow().getDecorView().postDelayed(() -> {
                final Intent intent = activity.getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage(activity.getPackageName());
                if (null != intent) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                }
                // 结束旧进程，确保下次是全新启动
                android.os.Process.killProcess(android.os.Process.myPid());
            }, 300);
        });
    }
}
