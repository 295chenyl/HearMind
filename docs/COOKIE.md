# B 站 Cookie 配置指南

导入 B 站视频时，yt-dlp 需要登录态 Cookie。本项目的 **默认方式** 是：在 Web Link 导入页 **与 URL 一并上传 Cookie**（仅用于当次下载，用后删除）。

## 方式一：Web 导入页上传（推荐）

1. 浏览器登录 [bilibili.com](https://www.bilibili.com)
2. 安装插件 **Get cookies.txt LOCALLY**（Chrome / Edge 扩展商店）
3. 在 B 站页面点击插件 → 导出 Cookie → 得到 `cookies.txt`
4. 在本项目 **链接导入** 面板粘贴 URL，并上传该文件（或粘贴文件内容）
5. 点击「预检」确认可解析后，再「导入并处理」

## 方式二：服务器全局配置（可选 fallback）

复制 `config/bilibili.cookies.txt.example` 为 `config/bilibili.cookies.txt`，填入 Netscape 格式 Cookie。  
当用户未上传 Cookie 时，后端会尝试使用该文件。

## 格式要求

Netscape HTTP Cookie File，需包含 `SESSDATA`、`bili_jct` 等字段。示例：

```text
.bilibili.com	TRUE	/	FALSE	1893456000	SESSDATA	your_value
.bilibili.com	TRUE	/	FALSE	1893456000	bili_jct	your_value
```

## 安全提示

- Cookie 等同登录态，**不要分享给他人**
- 不要将 `bilibili.cookies.txt` 提交到 Git（已在 `.gitignore`）
- Cookie 会过期，导入失败时请重新导出

## 常见错误

| 现象 | 处理 |
|------|------|
| HTTP 412 | 重新登录 B 站并导出 Cookie |
| 预检失败 | 确认 URL 为完整 BV 链接，Cookie 为 Netscape 格式 |
