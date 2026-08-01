# BlogWriter — Android 博客编写客户端

基于 Kotlin + Jetpack Compose（Material Design 3）的 Android 客户端，通过 GitHub API 浏览、预览、编辑存放在 GitHub 仓库中的 Markdown 博客文章。

## 功能流程

1. **首页授权**：点击按钮跳转 GitHub 授权页 `https://github.com/login/oauth/authorize?client_id=...&scope=repo`。
2. **回调返回 APP**：授权完成后，浏览器跳转 `blog://oauth/callback?access_token=xxx&scope=xxx`，系统会弹出选择器返回本 APP（已在 `AndroidManifest.xml` 里注册了对应的 `intent-filter` 深链接，`MainActivity` 通过 `onNewIntent` 接收）。
3. **选择仓库**：拉取 `GET /user/repos`，以列表形式展示，点击进入下一步。
4. **选择博客文件夹**：从仓库根目录开始逐级浏览（`GET /repos/{owner}/{repo}/contents/{path}`），确认某个目录为“博客文件夹”。
5. **扫描文章**：遍历该文件夹下的所有一级子文件夹，查找每个子文件夹里的 `index.md`，解析 front-matter 中的 `title` 或首个 `# 标题` 作为文章标题，摘要取正文前 80 字，最终以列表形式展示在主页。
6. **预览/编辑**：点击某篇文章进入编辑器，右上角图标可在“编辑（纯文本）”与“预览（compose-markdown 渲染 Markdown）”之间切换；点击保存按钮会调用 `PUT /repos/{owner}/{repo}/contents/{path}` 提交修改（自动带上文件的 `sha`，避免冲突）。

## ⚠️ 关于 GitHub OAuth 的重要说明

GitHub 标准 OAuth Web Flow 是：

```
1. APP 打开 /authorize?client_id=xxx&scope=repo&redirect_uri=blog://oauth/callback
2. 用户授权后，GitHub 重定向到 redirect_uri，并带上 ?code=xxx （不是 access_token）
3. 用 code + client_id + client_secret 向 https://github.com/login/oauth/access_token 换取真正的 access_token
```

**第 3 步必须在服务端完成**，因为 `client_secret` 一旦打包进 APK 就等于公开泄露，任何人反编译都能拿到，从而冒充你的 OAuth App。

所以本项目的深链接接收逻辑是按照“你已经有一个后端/云函数，在收到 GitHub 的 `code` 回调后，用 `client_secret` 完成换取，再重定向到 `blog://oauth/callback?access_token=xxx&scope=xxx`”来实现的（`MainActivity` 只负责接收这个最终态的 `access_token`）。

如果你还没有这个中转服务，推荐两种方案：

- **方案 A（推荐，最简单）**：写一个几行代码的 Serverless Function（Cloudflare Workers / Vercel Edge Function 都可以），路由为 `GET /oauth/callback?code=xxx`，在里面用 `client_secret` 换取 token，再 302 重定向到 `blog://oauth/callback?access_token=xxx&scope=xxx`。这样 `redirect_uri` 配置成这个 Function 的 HTTPS 地址即可（GitHub OAuth App 的 redirect_uri 必须是 https，不能直接填 `blog://...`）。
- **方案 B**：改用 GitHub 的 [Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow)，完全不需要 `client_secret`，适合原生 APP，但用户体验是“输入一个 8 位代码”而不是网页跳转授权，如果你想换成这种方式我可以再补一版。

我可以帮你把方案 A 的 Cloudflare Worker 代码也写好，需要的话告诉我。

## 目录结构

```
app/src/main/java/com/blog/writer/
├── MainActivity.kt              # 入口 + 深链接接收
├── AppViewModel.kt              # 全局状态：登录态/仓库/文件夹/文章列表
├── data/
│   ├── TokenStore.kt            # DataStore 保存 access_token
│   ├── GitHubRepository.kt      # 业务逻辑：拉仓库/扫描 index.md/读写文件
│   ├── api/
│   │   ├── GitHubApi.kt         # Retrofit 接口定义
│   │   └── ApiClient.kt         # Retrofit/OkHttp 单例
│   └── model/GitHubModels.kt    # 数据模型
├── nav/
│   ├── Routes.kt
│   └── AppNavHost.kt            # Navigation-Compose 导航图
└── ui/
    ├── theme/                   # Material3 主题（支持动态取色 Dynamic Color）
    └── screens/
        ├── auth/AuthScreen.kt
        ├── repo/RepoSelectScreen.kt
        ├── folder/FolderSelectScreen.kt
        ├── list/PostListScreen.kt
        └── editor/EditorScreen.kt
```

## 构建

用 Android Studio（Koala 或更新版本）直接打开项目根目录即可，Gradle 会自动同步依赖（min SDK 26 / target SDK 34, 需要网络访问 Google/Maven 仓库）。命令行构建：

```bash
./gradlew assembleDebug
```

> 注：本次交付未包含 `gradle-wrapper.jar` 二进制文件，请用 Android Studio 打开一次即可自动生成 wrapper，或自行执行 `gradle wrapper`。

## 已知可继续完善的点

- 目前 access_token 明文存放于 DataStore，生产环境建议改为 `EncryptedSharedPreferences` / Android Keystore 加密存储。
- 文件夹扫描目前只做一级子目录（子文件夹本身放 `index.md`），如果博客目录结构更深，可以在 `GitHubRepository.scanBlogPosts` 里改成递归。
- 编辑器目前是纯文本框 + compose-markdown 预览，如需要工具栏（加粗/插入图片/表格）可以在 `EditorScreen` 基础上继续加。
- 保存冲突处理：如果远端文件在你编辑期间被别人修改，`sha` 会不匹配导致 409，目前只是提示“保存失败”，可以再加冲突后自动拉取最新内容合并的逻辑。
