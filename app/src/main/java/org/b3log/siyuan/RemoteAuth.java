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
import android.widget.TextView;
import android.widget.Toast;

/**
 * 远程服务器登录管理（二开定制，新增文件，零冲突）。
 *
 * <p>服务器地址和授权码都由用户输入并保存在本机 SharedPreferences，
 * APK 里不含任何地址或凭据，别人拿到也无法直接登录。</p>
 */
public final class RemoteAuth {

    private static final String PREF_NAME = "siyuan_remote_auth";
    private static final String KEY_AUTH_CODE = "auth_code";
    private static final String KEY_ORIGIN = "server_origin"; // 与 RemoteSettings 共用

    private RemoteAuth() {
    }

    public static String getAuthCode(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_AUTH_CODE, "");
    }

    public static void saveAuthCode(final Context context, final String authCode) {
        final SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_AUTH_CODE, authCode).apply();
    }

    public static void clearAuthCode(final Context context) {
        final SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_AUTH_CODE).apply();
    }

    public static boolean hasAuthCode(final Context context) {
        return !getAuthCode(context).isEmpty();
    }

    /** 登录提交回调：dismiss 用于校验成功后关闭本对话框 */
    public interface Callback {
        void onSubmit(String origin, String authCode, Runnable dismiss);
        void onCancel();
    }

    /**
     * 显示登录对话框：服务器地址 + 授权码两个输入框（纯代码构建，合并 upstream 零冲突）。
     *
     * @param message 提示信息，null 时用默认提示
     */
    public static void showLoginDialog(final Context context, final String message, final Callback callback) {
        final int pad = 24;

        // 服务器地址输入框
        final TextView urlLabel = new TextView(context);
        urlLabel.setText("服务器地址（可带端口，也可只填域名/IP）");
        urlLabel.setPadding(pad, 8, pad, 0);

        final EditText urlInput = new EditText(context);
        urlInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setHint("例：https://your-domain.com 或 192.168.1.10:6806");
        urlInput.setSingleLine(true);
        urlInput.setPadding(pad, 4, pad, 16);
        // 不预填默认地址：APK 里不携带任何默认服务器地址。
        // 仅当本机曾保存过地址（重新登录场景）时才回填，方便用户修改。
        final android.content.SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        final String savedOrigin = sp.getString(KEY_ORIGIN, "");
        if (!savedOrigin.isEmpty()) {
            urlInput.setText(savedOrigin);
        }

        // 授权码输入框
        final TextView codeLabel = new TextView(context);
        codeLabel.setText("访问授权码");
        codeLabel.setPadding(pad, 8, pad, 0);

        final EditText codeInput = new EditText(context);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        codeInput.setHint("服务器端设置的访问授权码");
        codeInput.setSingleLine(true);
        codeInput.setPadding(pad, 4, pad, 8);
        codeInput.setText(RemoteAuth.getAuthCode(context)); // 预填已保存的授权码

        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(urlLabel);
        container.addView(urlInput);
        container.addView(codeLabel);
        container.addView(codeInput);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("连接思源服务器")
                .setMessage(null == message || message.isEmpty()
                        ? "请输入服务器地址和授权码" : message)
                .setView(container)
                .setCancelable(false)
                .setPositiveButton("登录", null) // 手动设置，校验失败时保持弹窗
                .setNegativeButton("退出", (d, w) -> callback.onCancel())
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String origin = RemoteSettings.normalize(urlInput.getText().toString());
            if (origin == null) {
                Toast.makeText(context, "服务器地址格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }
            final String code = codeInput.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(context, "授权码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            // 传 dismiss 句柄：校验成功后由调用方关闭对话框
            callback.onSubmit(origin, code, () -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
        }));
        dialog.show();
    }
}
