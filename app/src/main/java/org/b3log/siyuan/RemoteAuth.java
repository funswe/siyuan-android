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

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * 远程服务器授权码管理（二开定制，新增文件，零冲突）。
 *
 * <p>授权码不再硬编码在 APK 里，而是由用户输入并保存在本机 SharedPreferences。
 * 这样别人拿到 APK 也不知授权码，必须先登录。</p>
 */
public final class RemoteAuth {

    private static final String PREF_NAME = "siyuan_remote_auth";
    private static final String KEY_AUTH_CODE = "auth_code";

    private RemoteAuth() {
    }

    /** 读取本机保存的授权码，未保存返回空串 */
    public static String getAuthCode(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_AUTH_CODE, "");
    }

    /** 保存授权码到本机 */
    public static void saveAuthCode(final Context context, final String authCode) {
        final SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_AUTH_CODE, authCode).apply();
    }

    /** 清除授权码（退出登录） */
    public static void clearAuthCode(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_AUTH_CODE).apply();
    }

    /** 是否已有授权码 */
    public static boolean hasAuthCode(final Context context) {
        return !getAuthCode(context).isEmpty();
    }

    /** 登录结果回调 */
    public interface Callback {
        void onSubmit(String authCode);
        void onCancel();
    }

    /**
     * 显示登录对话框（纯代码构建，不依赖官方 layout 资源，合并 upstream 零冲突）。
     */
    public static void showLoginDialog(final Context context, final String message, final Callback callback) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("访问授权码");
        input.setSingleLine(true);
        final int pad = 24;
        input.setPadding(pad, pad, pad, pad);

        final LinearLayout container = new LinearLayout(context);
        container.setPadding(pad, 8, pad, 8);
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("连接思源服务器")
                .setMessage(null == message || message.isEmpty()
                        ? "服务器：" + RemoteConfig.ORIGIN + "\n\n请输入服务器端设置的访问授权码"
                        : message)
                .setView(container)
                .setCancelable(false)
                .setPositiveButton("登录", null) // 下面手动设置，避免输错时对话框被关掉
                .setNegativeButton("退出", (d, w) -> callback.onCancel())
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String code = input.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(context, "授权码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            callback.onSubmit(code); // 由调用方决定是否 dismiss（校验成功后再关）
        }));
        dialog.show();
    }
}
