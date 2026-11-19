# 思源 Android 二开定制说明（连接自托管服务器）

本仓库 fork 自 `siyuan-note/siyuan-android`，定制目标：**让 App 连接自己的思源服务器（飞牛 NAS），而不是在手机上启动本地内核**。

## 设计原则（为方便定期合并 upstream）

改动分为两类：

1. **新增文件（零冲突）**
   - `app/src/main/java/org/b3log/siyuan/RemoteConfig.java` —— 所有配置集中在这里（地址、端口、授权码、总开关）
   - `app/src/main/java/org/b3log/siyuan/RemoteKernel.java` —— 所有远程逻辑（登录鉴权、等待服务就绪）
   - 这两个文件是新增的，官方 upstream 完全没有它们，合并时不会产生冲突。

2. **对官方文件的修改（带标记，便于识别）**
   - `MainActivity.java`：地址常量指向 RemoteConfig；`bootKernel()` 和 `waitFotKernelHttpServing()` 开头各有一个 `SYNUIAN-CUSTOM-BEGIN/END` 包裹的开关块，官方原逻辑完整保留在后面。
   - `JSAndroid.java`、`Utils.java`：6 处 `127.0.0.1:6806` 字符串换成 `RemoteConfig.ORIGIN`（单行替换，极少冲突）。

**总开关**：`RemoteConfig.ENABLED`
- `true`（当前）= 连接远程服务器
- `false` = 回退到官方本地内核逻辑（用于对比测试 / 排查问题）

## 定期合并官方最新代码的流程

```powershell
# 1. 确认 upstream  remote 存在（首次）
git remote -v   # 应看到 upstream = https://github.com/siyuan-note/siyuan-android.git

# 2. 拉取官方最新代码
git fetch upstream

# 3. 变基到官方最新（把我们的定制 commit 重新应用到最新代码之上）
git rebase upstream/main

# 4. 如果 rebase 报冲突：
#    - 冲突几乎只会发生在 MainActivity.java 的 SYNUIAN-CUSTOM 标记块附近
#    - 解决原则：保留 SYNUIAN-CUSTOM-BEGIN/END 之间的开关块，其余采用官方(incoming)代码
#    - 然后 git add <文件> && git rebase --continue

# 5. 校验所有定制点都还在（见下方校验脚本）
.\scripts\verify-custom.ps1

# 6. 重新构建（见下）
.\scripts\rebuild.ps1

# 7. 推送到自己的 fork
git push origin main
```

## 重新构建 APK

构建产物（`kernel.aar`、`app.zip`、签名文件）不入库，升级版本后需要按版本重新生成：

1. **`app.zip`（前端资源）**：从对应版本的官方 APK 提取，或从运行中的服务器 Docker 容器复制：
   ```
   docker cp siyuan:/opt/siyuan/appearance  <临时目录>/appearance
   docker cp siyuan:/opt/siyuan/stage       <临时目录>/stage
   docker cp siyuan:/opt/siyuan/guide       <临时目录>/guide
   docker cp siyuan:/opt/siyuan/changelogs  <临时目录>/changelogs
   # 打包这几个目录为 app.zip，放到 app/src/main/assets/app.zip
   ```

2. **`kernel.aar`（Go 内核）**：从 siyuan 主仓库对应 tag 用 gomobile 编译。版本必须与前端资源一致：
   ```powershell
   # 克隆对应版本主仓库（如 v3.8.11）
   git clone --depth 1 --branch v3.8.11 https://github.com/siyuan-note/siyuan.git siyuan-kernel-src
   cd siyuan-kernel-src/kernel
   $env:ANDROID_HOME="..."; $env:ANDROID_NDK_HOME="..."; $env:JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
   $env:CGO_LDFLAGS="-Wl,-z,max-page-size=16384"   # 关键：16KB 页对齐，消除系统警告
   gomobile bind -tags "fts5,sqlcipher" -ldflags "-s -w" -o <本仓库>/app/libs/kernel.aar -target android/arm64 -androidapi=26 ./mobile/
   ```
   注意：
   - Windows 上必须设 `JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"`，否则 gomobile 生成的中文 Javadoc 会让 javac 报 GBK 编码错误。
   - 必须设 `CGO_LDFLAGS="-Wl,-z,max-page-size=16384"`，否则 libgojni.so 是 4KB 对齐，会触发 Android 16KB 警告。

3. **编译 APK**：
   ```powershell
   .\gradlew assembleOfficialDebug --no-daemon
   # 产物：app/build/outputs/apk/official/debug/
   ```
   首次构建需 `signings.gradle`（从 `signings.templates.gradle` 复制）、`local.properties`（指向 Android SDK）、SDK platform 36 + NDK + build-tools 36。

## 登录与服务器地址（安全设计）

**APK 里不含任何地址或凭据**——服务器地址和授权码都由用户在登录框输入，通用版可连任意思源服务器：

- 首次启动 / 未保存设置时，弹出登录框（两个输入框都为空，APK 里不含任何默认地址或授权码）：
  - **服务器地址**：可填 `https://your-domain.com`、`192.168.1.10:6806`、`http://host:6806` 等。
    - 端口可省略：`https://` 自动按 443，`http://` 和纯 IP 自动按 6806（思源默认）。
    - 解析逻辑在 `RemoteSettings.normalize()`，规范化为 `scheme://host:port` 并持久化。
    - 仅在重新登录时会回填上次保存的地址；首次启动留空。
  - **访问授权码**：服务器端「设置 → 关于 → 访问授权码」。
- 地址和授权码保存在本机 `SharedPreferences`（`RemoteAuth.java` + `RemoteSettings.java`），输一次后免重复输入。
- 每次连接先用授权码调用 `loginAuth` 校验，失败提示重新输入；本地授权码失效时自动清除并要求重新登录。
- 登录成功后 `MainActivity.refreshServerAddress()` 用输入的地址刷新所有 WebView URL 和校验常量——因此地址是运行时动态的，不再是编译期写死。
- 相关文件（均为新增，零冲突）：`RemoteAuth.java`（登录框+授权码存储）、`RemoteSettings.java`（地址解析+存储+动态读取）、`RemoteKernel.java`（校验+连接流程）。`RemoteConfig.DEFAULT_ORIGIN` 只是首次登录框的预填默认值。

**退出登录**：进入主界面后，点击底部工具栏最右侧的「三个点」（更多）→「设置与帮助」分组，最下面有「退出登录」项，点击后清除本机保存的地址和授权码并重启 App 回到登录框。

实现（关键：**改前端代码，不是 JS 注入**）：
- App 连远程时，WebView 加载的是**服务器端**的前端页面（`https://<host>/stage/build/mobile/`），不是 APK 里的 app.zip。所以退出登录按钮必须打在**服务器容器**的前端文件里。
- 修改服务器上 `/opt/siyuan/stage/build/mobile/index.html`，在 `</body>` 前插入 `scripts/frontend-logout-patch.html` 里的脚本：往 `#menu` 的 `data-menu-group="settings"` 分组追加「退出登录」项，点击调用 App 注入的 JS bridge `RemoteLogoutBridge.logout()`。
- **⚠️ 关键坑**：必须基于【服务器容器自己的】index.html 来改，**绝不能用本地 app.zip 里的 index.html 覆盖**。因为两端思源版本可能不同，index.html 里引用的 `main.<hash>.js` 文件名带版本哈希；若用本地版本覆盖，哈希和服务器实际 JS 文件不匹配，会导致前端加载失败、App 卡在"思源启动失败"黑屏。
  正确步骤：
  ```
  docker cp siyuan:/opt/siyuan/stage/build/mobile/index.html /tmp/orig.html   # 取出服务器当前版本
  # 在本地给 /tmp/orig.html 的 </body> 前插入补丁脚本
  docker cp /tmp/patched.html siyuan:/opt/siyuan/stage/build/mobile/index.html
  docker restart siyuan   # 重启让内核重新加载前端（有内存缓存）
  ```
  手机端也要清一次 WebView 缓存（卸载重装或删 app_webview/cache），否则会用到旧的错误 index.html 缓存。
- App 侧 `RemoteLogout.java`（新增文件）只负责注册 bridge 和执行退出（清凭据+重启），不含任何注入逻辑。
- 容器重建会丢失该文件改动，恢复命令：
  ```
  docker cp /vol1/backup/siyuan_mobile_index_patched.html siyuan:/opt/siyuan/stage/build/mobile/index.html
  docker restart siyuan
  ```
  （服务器上已存了打过补丁的备份；升级思源版本后需重新打补丁。）

**清除登录 / 换服务器**（备用）：卸载 App 重装，或 adb 执行：
```
adb shell run-as org.b3log.siyuan.debug rm -rf shared_prefs
```
（重新打开会弹出登录框，可输入新的地址和授权码。）

**关于登录方式**：自托管思源 Docker 版本身没有用户名/密码登录，只有「访问授权码」这一种鉴权。新版本支持 OIDC（对接 Google/Microsoft/GitHub 等第三方），但不是思源自带的账密。官方云端服务用 b3log 账号密码，与自托管不同。

## 关于默认地址

没有默认地址。`RemoteConfig` 只保留一个总开关 `ENABLED`，不含任何服务器地址或授权码。所有地址/凭据都来自用户在登录框的输入，保存在本机。

`RemoteSettings.origin()` 在未保存时返回空串，`RemoteAuth` 登录框首次启动地址栏为空。

## 已解决的问题（供参考）

1. **小米 16KB 对齐警告弹窗（已从根源修复）**：
   - `libgojni.so`：gomobile 编译内核时通过环境变量 `CGO_LDFLAGS="-Wl,-z,max-page-size=16384"` 让 ELF LOAD 段按 16KB 对齐（0x4000）。重新编译命令见下方"重新构建"第 2 步，**务必带上这个环境变量**。
   - `libonnxruntime.so` / `libonnxruntime4j_jni.so`：来自 `onnxruntime-android` 依赖且未对齐。已在 `app/build.gradle` 注释掉该依赖（连远程时本地 OCR 用不到，OCR 在服务端处理）。
   - 修复后恢复官方默认 `debuggable=true`，不再需要绕过。
   - 校验方法：`llvm-readelf -l libgojni.so | grep LOAD`，对齐应为 `0x4000`。
2. **启动黑屏 ANR**：`showBootIndex()` 运行在主线程，早期版本在其中做远程网络等待导致主线程阻塞。修复：`waitFotKernelHttpServing()` 的远程分支直接返回（服务已在 bootKernel 的 RemoteBoot 后台线程确认就绪）。

## 重要提醒

- `AUTH_CODE`（访问授权码）明文写在 APK 里，仅限个人使用。
- debug 版包名是 `org.b3log.siyuan.debug`，可与官方版共存。
- 服务器升级内核版本后，建议同步更新 app.zip 和 kernel.aar 到相同版本，避免前后端 API 不匹配。
