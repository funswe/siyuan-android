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
 * 远程连接总开关（二开定制，新增文件，零冲突）。
 *
 * <p>不携带任何服务器地址或授权码：地址与授权码都由用户在登录框输入，
 * 保存在本机 RemoteSettings / RemoteAuth（SharedPreferences）。
 * APK 里没有任何默认服务器信息，别人拿到也无法预知要连哪里。</p>
 */
public final class RemoteConfig {
    /** true=连接远程服务器（地址/授权码由用户输入）；false=使用官方本地内核逻辑 */
    public static final boolean ENABLED = true;

    private RemoteConfig() {
    }
}
