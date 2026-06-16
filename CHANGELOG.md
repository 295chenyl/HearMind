# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- Paraformer 时间戳毫秒解析错误导致转写跳转/RAG 引用偏移
- RAG 索引改为 READY 前同步构建；检索失败时关键词/片段降级
- 详情页可切换内容类型；Markdown 导出含问答历史
- 问答 citation 前端 localStorage 缓存（刷新后仍可点击跳转）

## [1.0.0] - 2026-06-11

### Added

- ECS deployment guide (`docs/DEPLOY.md`, `deploy/`)
- Production-oriented systemd and Nginx templates

## [0.6.0] - 2026-06-11

### Added

- RAG Q&A with transcript chunk indexing and citation timestamps
- DashScope text embedding for semantic retrieval
- `docs/eval/qa_cases.json` evaluation template

### Changed

- Chat uses retrieved chunks + user-edited summary instead of full transcript prompt

## [0.5.0] - 2026-06-11

### Added

- Editable summary, regenerate by content type, Markdown export API + UI
- Content types: GENERAL, CLASS, MEETING, INTERVIEW

## [0.4.0] - 2026-06-11

### Added

- Web Link preview API with per-import user Cookie (file or text)
- `GET /api/system/import-health`
- Bilibili Cookie user guide (`docs/COOKIE.md`)

### Fixed

- Direct HTTP download preserves file extension instead of always `source.mp4`

## [0.3.0] - 2026-06-11

### Added

- Transcript segments API and clickable timestamp list with player seek

## [0.2.0] - 2026-06-11

### Added

- Open-source documentation: PRODUCT_ROADMAP, CHANGELOG, MIT LICENSE
- README links to docs

## [0.1.0] - 2026-06-13

### Added

- Initial MVP: auth, upload, Web Link, ASR, summary, chat
- Redis rate limit, resumable upload, deduplication, exponential backoff
