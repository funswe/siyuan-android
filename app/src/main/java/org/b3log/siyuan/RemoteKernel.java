/*
 * SiYuan - From thought to insight, with agents
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 远程内核连接逻辑（二开定制，新增文件，零冲突）。
 *
 * <p>承载"连接远程服务器"的完整流程：读取本机授权码 → 无则弹登录框 →
 * 校验授权码 → 注入 cookie → 等待服务就绪 → 回调加载页面。</p>
 */
public final class RemoteKernel {

    private RemoteKernel() {
    }

    /**
     * 启动远程连接完整流程。在 MainActivity 的后台线程中调用。
     *
     * @param activity App 主 Activity（用于弹登录框 / 回主线程）
     * @param onReady  连接就绪后在主线程执行的回调（通常是 bootIndex）
     */
    public static void start(final Activity activity, final Runnable onReady) {
        final Handler main = new Handler(Looper.getMainLooper());
        final String saved = RemoteAuth.getAuthCode(activity);

        if (!saved.isEmpty()) {
            // 本机已有授权码：后台校验并连接
            connect(activity, main, saved, onReady, true);
            return;
        }

        // 无授权码：主线程弹登录框
        main.post(() -> RemoteAuth.showLoginDialog(activity, null, new RemoteAuth.Callback() {
            @Override
            public void onSubmit(final String authCode) {
                // 后台校验
                new Thread(() -> {
                    if (verify(authCode)) {
                        RemoteAuth.saveAuthCode(activity, authCode);
                        main.post(() -> {
                            // 关闭登录框由 dialog 自行处理较复杂，这里用重新进入连接流程
                            Toast.makeText(activity, "登录成功", Toast.LENGTH_SHORT).show();
                        });
                        connect(activity, main, authCode, onReady, false);
                    } else {
                        main.post(() -> RemoteAuth.showLoginDialog(activity,
                                "授权码错误，请重新输入（服务器：" + RemoteConfig.ORIGIN + "）", this));
                    }
                }, "RemoteAuth").start();
            }

            @Override
            public void onCancel() {
                activity.finish();
            }
        }));
    }

    /**
     * 用给定授权码连接：先确保 cookie 注入成功，再等待服务就绪，最后回调。
     *
     * @param reAuthOnFail 校验失败时是否清除本机授权码并重试登录框
     */
    private static void connect(final Activity activity, final Handler main, final String authCode,
                                final Runnable onReady, final boolean reAuthOnFail) {
        new Thread(() -> {
            // 1) 校验 + 注入 cookie
            if (!verify(authCode)) {
                Log.w("remote", "auth failed with saved code");
                if (reAuthOnFail) {
                    RemoteAuth.clearAuthCode(activity);
                    main.post(() -> RemoteAuth.showLoginDialog(activity,
                            "登录已失效，请重新输入授权码（服务器：" + RemoteConfig.ORIGIN + "）",
                            new RemoteAuth.Callback() {
                                @Override
                                public void onSubmit(final String code) {
                                    new Thread(() -> {
                                        if (verify(code)) {
                                            RemoteAuth.saveAuthCode(activity, code);
                                            connect(activity, main, code, onReady, false);
                                        } else {
                                            main.post(() -> RemoteAuth.showLoginDialog(activity,
                                                    "授权码错误，请重新输入（服务器：" + RemoteConfig.ORIGIN + "）", this));
                                        }
                                    }, "RemoteAuth").start();
                                }

                                @Override
                                public void onCancel() {
                                    activity.finish();
                                }
                            }));
                }
                return;
            }
            // 2) 等待服务就绪
            waitForServer();
            // 3) 主线程回调加载页面
            main.post(onReady);
        }, "RemoteBoot").start();
    }

    /**
     * 向远程服务器校验授权码，成功则把 siyuan cookie 注入 WebView CookieManager。
     *
     * @return true=授权码正确且 cookie 已注入
     */
    public static boolean verify(final String authCode) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(RemoteConfig.LOGIN_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json");
            // 转义授权码中的特殊字符，防止 JSON 注入
            final String safe = authCode.replace("\\", "\\\\").replace("\"", "\\\"");
            final String body = "{\"authCode\":\"" + safe + "\"}";
            final OutputStream os = conn.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            final int code = conn.getResponseCode();
            final String setCookie = conn.getHeaderField("Set-Cookie");
            Log.i("remote-auth", "login code=" + code + " cookie=" + (null == setCookie ? "null" : setCookie.split(";")[0]));
            if (code == 200 && null != setCookie && !setCookie.isEmpty()) {
                final String cookie = setCookie.split(";")[0]; // siyuan=xxxxx
                final android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                cm.setCookie(RemoteConfig.ORIGIN, cookie);
                cm.flush();
                Log.i("remote-auth", "auth cookie injected");
                return true;
            }
            return false;
        } catch (final Exception e) {
            Utils.logError("remote-auth", "authenticate remote server failed", e);
            return false;
        } finally {
            if (null != conn) {
                conn.disconnect();
            }
        }
    }

    /**
     * 等待远程内核 HTTP 服务就绪（探测 bootProgress 接口）。
     */
    public static void waitForServer() {
        HttpURLConnection conn = null;
        for (int i = 0; i < 300; i++) { // 最多约 30 秒
            try {
                conn = (HttpURLConnection) new URL(RemoteConfig.BOOT_PROGRESS_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                final int code = conn.getResponseCode();
                if (code == 200) {
                    Log.i("remote", "remote server is ready");
                    conn.disconnect();
                    return;
                }
            } catch (final Exception ignored) {
                // 服务器还没就绪，继续重试
            } finally {
                if (null != conn) {
                    conn.disconnect();
                    conn = null;
                }
            }
            try {
                Thread.sleep(100);
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Log.i("remote", "remote server not ready after timeout, continue anyway");
    }
}
