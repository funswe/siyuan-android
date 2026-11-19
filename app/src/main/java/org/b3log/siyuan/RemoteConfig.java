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
 * 远程内核服务器配置。
 *
 * <p>二开定制：让 App 连接自托管的思源服务而不是在手机上启动本地内核。
 * 修改服务器地址、端口和访问授权码只需改这里一处。
 * 以后合并官方 upstream 代码时，本文件是新增文件，不会被覆盖，
 * 其它改造点都用 git 记录，方便 rebase。</p>
 */
public final class RemoteConfig {
    /** 总开关：true=连接远程服务器；false=使用官方本地内核逻辑 */
    public static final boolean ENABLED = true;
    /** 远程服务器协议 */
    public static final String SCHEME = "https";
    /** 远程服务器主机（公网域名，经反代） */
    public static final String HOST = "siyuan.funswe.com";
    /** 远程服务器端口（HTTPS 默认 443，显式写出以匹配 URL 端口校验） */
    public static final int PORT = 443;
    // 授权码不再硬编码：由用户在登录框输入，保存在本机 SharedPreferences（见 RemoteAuth）。
    // 这样 APK 里没有任何凭据，别人拿到也无法直接登录。

    /** 服务根地址，例如 https://siyuan.funswe.com:443 */
    public static final String ORIGIN = SCHEME + "://" + HOST + ":" + PORT;
    /** 主页面地址 */
    public static final String MAIN_PAGE_URL = ORIGIN + "/";
    /** 启动引导页地址 */
    public static final String BOOT_PAGE_URL = ORIGIN + "/appearance/boot/index.html?v=";
    /** 启动进度接口 */
    public static final String BOOT_PROGRESS_URL = ORIGIN + "/api/system/bootProgress";
    /** 登录鉴权接口 */
    public static final String LOGIN_URL = ORIGIN + "/api/system/loginAuth";

    private RemoteConfig() {
    }
}
