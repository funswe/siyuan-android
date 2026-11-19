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

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;

/**
 * 远程服务器地址设置（二开定制，新增文件，零冲突）。
 *
 * <p>保存用户在登录框输入的服务器地址，提供动态读取方法。
 * 未设置时回退到 RemoteConfig 里的默认值。地址规范化为 scheme://host:port，
 * 显式带端口保证 WebView 的 getPort() 校验正常。</p>
 */
public final class RemoteSettings {

    private static final String PREF_NAME = "siyuan_remote_auth";
    private static final String KEY_ORIGIN = "server_origin";

    private RemoteSettings() {
    }

    private static Context ctx() {
        return com.blankj.utilcode.util.Utils.getApp();
    }

    private static SharedPreferences sp() {
        return ctx().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 保存规范化后的 origin */
    public static void saveOrigin(final String origin) {
        sp().edit().putString(KEY_ORIGIN, origin).apply();
    }

    /** 读取当前 origin（未设置返回空串，不携带任何默认服务器地址） */
    public static String origin() {
        return sp().getString(KEY_ORIGIN, "");
    }

    public static String scheme() {
        return extract(origin(), 0);
    }

    public static String host() {
        return extract(origin(), 1);
    }

    public static int port() {
        try {
            return Integer.parseInt(extract(origin(), 2));
        } catch (final Exception e) {
            return "https".equalsIgnoreCase(scheme()) ? 443 : 6806;
        }
    }

    /** 从 origin 解析出 scheme/host/port，idx: 0=scheme 1=host 2=port */
    private static String extract(final String origin, final int idx) {
        try {
            final URI uri = new URI(origin);
            if (idx == 0) return uri.getScheme();
            if (idx == 1) return uri.getHost();
            return String.valueOf(uri.getPort());
        } catch (final Exception e) {
            // 简单字符串回退
            final String s = origin.replace("://", ":").replace("/", "");
            final String[] parts = s.split(":");
            if (parts.length >= 3) {
                if (idx == 0) return parts[0];
                if (idx == 1) return parts[1];
                return parts[2];
            }
            return "";
        }
    }

    public static String mainPageUrl() {
        return origin() + "/";
    }

    public static String bootPageUrl(final String version) {
        return origin() + "/appearance/boot/index.html?v=" + version;
    }

    public static String bootProgressUrl() {
        return origin() + "/api/system/bootProgress";
    }

    public static String loginUrl() {
        return origin() + "/api/system/loginAuth";
    }

    /**
     * 智能解析用户输入的地址，规范化为 scheme://host:port。
     * - 带协议则用之；不带则：IP/localhost 用 http，域名用 https。
     * - 带端口则用之；不带则：https→443，http→6806（思源默认）。
     *
     * @return 规范化后的 origin，解析失败返回 null
     */
    public static String normalize(final String input) {
        String s = (null == input ? "" : input.trim());
        // 去掉结尾斜杠和路径，只保留 origin 部分
        int slash = s.indexOf('/', s.indexOf("://") + 3);
        if (s.contains("://") && slash > 0) {
            s = s.substring(0, slash);
        }
        if (s.isEmpty()) {
            return null;
        }

        // 补协议
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = (isIpLiteral(s) || s.startsWith("localhost")) ? "http://" + s : "https://" + s;
        }

        try {
            final URI uri = new URI(s);
            final String scheme = uri.getScheme();
            final String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return null;
            }
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equalsIgnoreCase(scheme) ? 443 : 6806;
            }
            return scheme + "://" + host + ":" + port;
        } catch (final Exception e) {
            return null;
        }
    }

    /** 简单判断是否为 IPv4 字面量（含端口时先去掉端口） */
    private static boolean isIpLiteral(final String s) {
        final String host = s.split(":")[0];
        return host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
    }
}
