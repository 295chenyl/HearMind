# 实现回溯：Plan 模式 vs 直接编码

> 日期：2026-06-11  
> Git：`5b41d5b`（MVP）→ `00171a5`（P0–P5 一次性实现）

## 1. 发生了什么

| 预期（Plan 模式） | 实际 |
|------------------|------|
| 先产出需求文档，用户确认后再按 Phase 迭代 | Plan 确认后 **单次 commit 实现 P0–P5** |
| 每 Phase 独立验收、独立 tag | 6 个 tag 均指向同一 commit `00171a5` |
| 可选 `.github/ISSUE_TEMPLATE` | 未创建 |

**结论**：功能面覆盖计划条目，但 **缺少分阶段验收**，问题会在「一次性合并」后集中暴露。

---

## 2. 计划 vs 实现对照

| 需求 | 计划验收 | 实现状态 | 偏差/风险 |
|------|----------|----------|-----------|
| P1 时间戳跳转 | 点击误差 ≤2s | ⚠️ 有 bug | `TranscriptSegmentParser` 误将 ms 乘 1000 |
| P2 Cookie 导入 | 用户上传可导 B 站 | ✅ 基本满足 | 失败后「重新处理」不会保留 Cookie，需重传 |
| P2 preview 再 import | 先 preview | ⚠️ 弱约束 | 前端可跳过 preview 直接导入 |
| P3 导出 MD | 含摘要+带时间戳转写 | ⚠️ 部分 | 未含「可选问答历史」 |
| P3/P6 内容类型 | 详情页可改类型 regenerate | ⚠️ 部分 | 仅 Upload 可选，详情页 regenerate 用入库类型 |
| P4 RAG | 长视频后半段可答 + citations | ⚠️ 有 bug | 索引 **异步** 于 READY，刚完成时问答可能无 chunk |
| P4 citations 持久化 | 刷新后仍可见 | ❌ | Redis 只存文本，citations 刷新丢失 |
| P5 部署 | ECS 公网验收 | 📄 仅有文档 | 未在本环境完成公网部署 |

---

## 3. 已确认的根因（代码级）

### 3.1 时间戳解析错误（P1）

Paraformer 的 `begin_time` / `end_time` **单位已是毫秒**。  
原逻辑 `num > 100_000 ? num : num * 1000` 会把 100ms、5000ms 等放大 1000 倍，导致：

- 转写列表时间显示错误
- 点击跳转位置严重偏移
- RAG chunk 的 `startSec`/`endSec` 错误

### 3.2 RAG 索引竞态（P4）

`VideoProcessingService` 先 `markStatus(READY)`，再 `buildIndexAsync()`。  
用户视频刚变 READY 就提问时，检索常为空 → 无 citation、回答质量差。

### 3.3 问答无检索兜底（P4）

索引失败或为空时，`ChatService` 不再使用 `fullText`，仅依赖空 chunks + 摘要。

### 3.4 Citations 不持久化

`RedisChatMemoryStore` 仅存 `role/content`；刷新页面后 citation 按钮消失。

---

## 4. 本次修复（见 commit fix/retrospective）

1. 修正 `TranscriptSegmentParser` 毫秒解析  
2. READY 前 **同步** 构建 RAG 索引（失败仅 warn，不阻断 READY）  
3. 检索为空时 **关键词 fallback** 匹配转写 chunk  
4. 详情页增加 **内容类型** 选择并持久化  
5. Markdown 导出增加 **问答历史**（可选）  
6. 新增 `POST /api/videos/{id}/index/rebuild` 便于旧数据重建索引  
7. 更新前端问答提示文案（RAG 而非「全文」）

---

## 5. 建议的后续流程

1. **按 Phase 验收**：每完成一 Phase 再 tag，避免多 Phase 混在同一 commit  
2. **旧视频**：对 READY 但无 chunk 的视频调用 index rebuild 或「重新处理」  
3. **GitHub Release**：在网页为 `v1.0.0` 补 Release Notes（本机无 `gh` CLI）  
4. **公网验收**：按 `docs/DEPLOY.md` 在 ECS 跑通完整链路

---

## 6. Git 参考

```bash
git log --oneline
# 00171a5 feat: implement P0-P5 roadmap ...
# 5b41d5b Initial commit: dovideoAi pro MVP

git show 00171a5 --stat   # 一次性 56 文件变更
git diff 5b41d5b..00171a5 -- backend/   # MVP → v1 后端 diff
```
