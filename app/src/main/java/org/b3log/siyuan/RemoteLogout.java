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
import android.webkit.WebView;

/**
 * 退出登录功能（二开定制，新增文件，零冲突）。
 *
 * <p>往思源前端底部工具栏"三个点"(#barMore)弹出的菜单里注入一个"退出登录"项，
 * 点击后调用本 bridge 的 logout()：清除本机保存的地址和授权码并重启 App 回到登录框。
 * 不改 app.zip 前端源码、不改官方 activity 启动流程。</p>
 */
public final class RemoteLogout {

    public static final String BRIDGE_NAME = "RemoteLogoutBridge";

    private final Activity activity;

    public RemoteLogout(final Activity activity) {
        this.activity = activity;
    }

    /** 前端"退出登录"菜单项点击时调用（运行在 WebView 的 JS 线程） */
    @JavascriptInterface
    public void logout() {
        new Handler(Looper.getMainLooper()).post(() -> {
            RemoteAuth.clearAuthCode(activity);
            RemoteSettings.saveOrigin(""); // 清空地址，回到首次登录状态
            // 清除 WebView cookie，避免残留鉴权
            try {
                android.webkit.CookieManager.getInstance().removeAllCookies(null);
            } catch (final Exception ignored) {
            }
            // 重启 App 主 Activity，触发重新登录
            final Intent intent = activity.getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(activity.getPackageName());
            if (null != intent) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
            activity.finish();
        });
    }

    /**
     * 注入 JS：hook #barMore，在"更多"菜单弹出时追加一个"退出登录"项。
     * 只注入一次（用 window 标志防重复）。
     */
    public static void inject(final WebView webView) {
        if (null == webView) {
            return;
        }
        webView.evaluateJavascript(JS, null);
    }

    private static final String JS =
            "(function(){"
            + "if(window.__remoteLogoutInjected)return;"
            + "window.__remoteLogoutInjected=true;"
            + "function ensureItem(){"
            + "  try{"
            + "    var menus=document.querySelectorAll('.b3-menu');"
            + "    for(var i=0;i<menus.length;i++){"
            + "      var menu=menus[i];"
            + "      var r=menu.getBoundingClientRect();"
            + "      if(r.width<=0||r.height<=0)continue;" // 不可见跳过
            + "      // 识别底部'更多'菜单：含'文档'和'大纲'标签项"
            + "      var labels=menu.querySelectorAll('.b3-menu__label');"
            + "      var isMoreMenu=false;"
            + "      for(var j=0;j<labels.length;j++){"
            + "        if(labels[j].textContent==='文档'){isMoreMenu=true;break;}"
            + "      }"
            + "      if(!isMoreMenu)continue;"
            + "      if(menu.querySelector('[data-siyuan-logout]'))continue;" // 已加过
            + "      var item=document.createElement('div');"
            + "      item.className='b3-menu__item';"
            + "      item.setAttribute('data-siyuan-logout','1');"
            + "      item.innerHTML='<svg class=\"b3-menu__icon\"><use xlink:href=\"#iconQuit\"></use></svg>'"
            + "        +'<span class=\"b3-menu__label\">退出登录</span>';"
            + "      item.addEventListener('click',function(ev){"
            + "        ev.stopPropagation();ev.preventDefault();"
            + "        try{" + BRIDGE_NAME + ".logout();}catch(e){}"
            + "      });"
            + "      menu.appendChild(item);"
            + "      return;"
            + "    }"
            + "  }catch(e){}"
            + "}"
            // 点 toolbarMore 后立即补项（延时等菜单构建完成）
            + "document.addEventListener('click',function(e){"
            + "  var t=e.target;"
            + "  if(t&&t.closest&&t.closest('#toolbarMore')){"
            + "    setTimeout(ensureItem,80);setTimeout(ensureItem,300);"
            + "  }"
            + "},true);"
            // MutationObserver + 定时轮询兜底
            + "try{new MutationObserver(ensureItem).observe(document.body,"
            + "  {childList:true,subtree:true});}catch(e){}"
            + "setInterval(ensureItem,500);"
            + "})();";
}
