# B 站 Cookie 配置指南

导入 B 站视频时，yt-dlp 需要登录态 Cookie。**仅由管理员在服务器配置**，用户无需也无法在 Web 页面上传。

## 获取 Cookie

1. 浏览器登录 [bilibili.com](https://www.bilibili.com)
2. 安装插件 **Get cookies.txt LOCALLY**（Chrome / Edge）
3. 打开 B 站任意页面 → 插件 → **Export**（格式选 Netscape）
4. 建议导出 **当前站点 Cookie**；若使用「Export All Cookies」，文件可包含多站，后端会自动提取 B 站相关条目

## 服务器配置

将导出内容写入项目根目录：

```text
config/bilibili.cookies.txt
```

也可在 ECS 上直接编辑：

```bash
vim /opt/dovidioai/src/config/bilibili.cookies.txt
sudo systemctl restart dovidioai
```

### 格式要求

Netscape HTTP Cookie File，**必须包含** `.bilibili.com` 域名的 `SESSDATA`：

```text
# Netscape HTTP Cookie File
.bilibili.com	TRUE	/	TRUE	1797148860	SESSDATA	你的值...
.bilibili.com	TRUE	/	TRUE	1797148860	bili_jct	你的值...
```

> 常见错误：把「全部 Cookie 导出」放进文件，但其中**没有任何 bilibili.com 条目**（例如只在 Google/GitHub 等站点登录过）。请确认文件内能搜到 `bilibili.com` 和 `SESSDATA`。

## 过期提醒

- 系统解析 `SESSDATA` 过期时间
- 剩余 **7 天**内：登录后顶部黄色横幅（可暂时关闭）
- **已过期 / 无效 / 未配置**：橙色或红色横幅
- 调整提醒阈值：`application.yml` → `app.bilibili-cookie-warn-days`

## 安全提示

- Cookie 等同登录态，不要分享或提交到 Git（`config/bilibili.cookies.txt` 已在 `.gitignore`）
- 定期在服务器更新 Cookie

## 常见错误

| 现象 | 处理 |
|------|------|
| HTTP 412 | 更新 `config/bilibili.cookies.txt` 中的 SESSDATA |
| 文件无效 | 确认含 `.bilibili.com` 的 SESSDATA，非纯示例或无关站点 Cookie |
| 预检失败 | URL 为完整 BV 链接，且 Cookie 为 Netscape 格式 |
