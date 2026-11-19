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
 * <p>完整流程：读取本机设置 → 无则弹登录框（地址+授权码）→ 校验 →
 * 保存设置 → 注入 cookie → 等待就绪 → 回调加载页面。</p>
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
        final String savedCode = RemoteAuth.getAuthCode(activity);

        if (!savedCode.isEmpty()) {
            // 本机已有授权码：用当前保存的地址直接校验并连接
            connect(activity, main, RemoteSettings.origin(), savedCode, onReady, true);
            return;
        }

        // 无授权码：主线程弹登录框
        main.post(() -> RemoteAuth.showLoginDialog(activity, null, new RemoteAuth.Callback() {
            @Override
            public void onSubmit(final String origin, final String authCode) {
                new Thread(() -> {
                    if (verify(origin, authCode)) {
                        RemoteSettings.saveOrigin(origin);
                        RemoteAuth.saveAuthCode(activity, authCode);
                        main.post(() -> Toast.makeText(activity, "登录成功", Toast.LENGTH_SHORT).show());
                        connect(activity, main, origin, authCode, onReady, false);
                    } else {
                        main.post(() -> showLogin(activity, main, onReady,
                                "授权码错误，请重新输入（服务器：" + origin + "）"));
                    }
                }, "RemoteAuth").start();
            }

            @Override
            public void onCancel() {
                activity.finish();
            }
        }));
    }

    /** 弹出登录框（带错误提示） */
    private static void showLogin(final Activity activity, final Handler main, final Runnable onReady,
                                  final String message) {
        main.post(() -> RemoteAuth.showLoginDialog(activity, message, new RemoteAuth.Callback() {
            @Override
            public void onSubmit(final String origin, final String authCode) {
                new Thread(() -> {
                    if (verify(origin, authCode)) {
                        RemoteSettings.saveOrigin(origin);
                        RemoteAuth.saveAuthCode(activity, authCode);
                        connect(activity, main, origin, authCode, onReady, false);
                    } else {
                        showLogin(activity, main, onReady,
                                "授权码错误，请重新输入（服务器：" + origin + "）");
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
     * 用给定地址和授权码连接：校验 cookie → 等待就绪 → 回调。
     *
     * @param reAuthOnFail 校验失败时是否清除授权码并要求重新登录
     */
    private static void connect(final Activity activity, final Handler main, final String origin,
                                final String authCode, final Runnable onReady, final boolean reAuthOnFail) {
        new Thread(() -> {
            if (!verify(origin, authCode)) {
                Log.w("remote", "auth failed with saved code, origin=" + origin);
                if (reAuthOnFail) {
                    RemoteAuth.clearAuthCode(activity);
                    showLogin(activity, main, onReady,
                            "登录已失效或服务器地址已变更，请重新输入（当前：" + origin + "）");
                }
                return;
            }
            waitForServer(origin);
            main.post(onReady); // onReady 内部会刷新地址字段并加载页面
        }, "RemoteBoot").start();
    }

    /**
     * 向指定服务器校验授权码，成功则把 siyuan cookie 注入 WebView CookieManager。
     */
    public static boolean verify(final String origin, final String authCode) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(origin + "/api/system/loginAuth").openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json");
            final String safe = authCode.replace("\\", "\\\\").replace("\"", "\\\"");
            final String body = "{\"authCode\":\"" + safe + "\"}";
            final OutputStream os = conn.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            final int code = conn.getResponseCode();
            final String setCookie = conn.getHeaderField("Set-Cookie");
            Log.i("remote-auth", "origin=" + origin + " login code=" + code
                    + " cookie=" + (null == setCookie ? "null" : setCookie.split(";")[0]));
            if (code == 200 && null != setCookie && !setCookie.isEmpty()) {
                final String cookie = setCookie.split(";")[0]; // siyuan=xxxxx
                final android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
                cm.setCookie(origin, cookie);
                cm.flush();
                Log.i("remote-auth", "auth cookie injected for " + origin);
                return true;
            }
            return false;
        } catch (final Exception e) {
            Utils.logError("remote-auth", "authenticate failed for " + origin, e);
            return false;
        } finally {
            if (null != conn) {
                conn.disconnect();
            }
        }
    }

    /** 保留旧签名（兼容可能的其它调用），用当前保存的地址 */
    public static boolean verify(final String authCode) {
        return verify(RemoteSettings.origin(), authCode);
    }

    /** 等待指定服务器的内核 HTTP 服务就绪 */
    public static void waitForServer(final String origin) {
        HttpURLConnection conn = null;
        for (int i = 0; i < 300; i++) { // 最多约 30 秒
            try {
                conn = (HttpURLConnection) new URL(origin + "/api/system/bootProgress").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                final int code = conn.getResponseCode();
                if (code == 200) {
                    Log.i("remote", "remote server is ready: " + origin);
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

    /** 保留旧签名（兼容） */
    public static void waitForServer() {
        waitForServer(RemoteSettings.origin());
    }
}
