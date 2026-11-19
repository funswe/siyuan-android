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

/**
 * 远程连接默认配置（二开定制，新增文件，零冲突）。
 *
 * <p>这里只放【默认值】：用户首次启动、尚未输入服务器地址时用它。
 * 用户输入后保存在 RemoteSettings（SharedPreferences），运行时以 RemoteSettings 为准。
 * 授权码完全不写在这里，由用户在登录框输入。</p>
 */
public final class RemoteConfig {
    /** 总开关：true=连接远程服务器；false=使用官方本地内核逻辑 */
    public static final boolean ENABLED = true;

    /** 默认服务器地址（用户未输入时使用；用户在登录框输入后会覆盖并持久化） */
    public static final String DEFAULT_ORIGIN = "https://siyuan.funswe.com:443";

    // 以下为兼容旧引用保留的默认值常量；运行时逻辑应优先使用 RemoteSettings 的动态方法。
    public static final String SCHEME = "https";
    public static final String HOST = "siyuan.funswe.com";
    public static final int PORT = 443;
    public static final String ORIGIN = DEFAULT_ORIGIN;
    public static final String MAIN_PAGE_URL = ORIGIN + "/";
    public static final String BOOT_PAGE_URL = ORIGIN + "/appearance/boot/index.html?v=";
    public static final String BOOT_PROGRESS_URL = ORIGIN + "/api/system/bootProgress";
    public static final String LOGIN_URL = ORIGIN + "/api/system/loginAuth";

    private RemoteConfig() {
    }
}
