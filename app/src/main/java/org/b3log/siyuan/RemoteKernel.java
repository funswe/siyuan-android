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

import android.util.Log;
import android.webkit.CookieManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 远程内核连接逻辑（二开定制）。
 *
 * <p>这个类承载所有"连接远程服务器"相关的逻辑：登录鉴权、等待服务就绪。
 * 它是【新增文件】，官方 upstream 合并时不会产生冲突。
 * MainActivity 里只保留最小的调用挂钩点。</p>
 */
public final class RemoteKernel {

    private RemoteKernel() {
    }

    /**
     * 向远程思源服务器登录，拿到 siyuan 鉴权 cookie 并注入 WebView CookieManager。
     * 3.8.x 服务端在未登录时访问主页返回 401，必须先登录拿 cookie。
     */
    public static void authenticate() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(RemoteConfig.LOGIN_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json");
            final String body = "{\"authCode\":\"" + RemoteConfig.AUTH_CODE + "\"}";
            final OutputStream os = conn.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            final int code = conn.getResponseCode();
            final String setCookie = conn.getHeaderField("Set-Cookie");
            Log.i("remote-auth", "login code=" + code + " cookie=" + (null == setCookie ? "null" : setCookie.split(";")[0]));
            if (code == 200 && null != setCookie && !setCookie.isEmpty()) {
                final String cookie = setCookie.split(";")[0]; // siyuan=xxxxx
                final CookieManager cm = CookieManager.getInstance();
                cm.setCookie(RemoteConfig.ORIGIN, cookie);
                cm.flush();
                Log.i("remote-auth", "auth cookie injected");
            }
        } catch (final Exception e) {
            Utils.logError("remote-auth", "authenticate remote server failed", e);
        } finally {
            if (null != conn) {
                conn.disconnect();
            }
        }
    }

    /**
     * 等待远程内核 HTTP 服务就绪（探测 bootProgress 接口）。
     * 供后台线程调用的静态方法，不依赖 Activity 实例。
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
