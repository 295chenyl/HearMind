-- Phase 1 数据在引入登录前均写入 user_id=1，与首个注册用户冲突。
-- 将无对应 app_user 的遗留数据标记为孤儿（user_id=0），新用户从空列表开始。
UPDATE video SET user_id = 0 WHERE user_id = 1;
UPDATE chat_session SET user_id = 0 WHERE user_id = 1;
