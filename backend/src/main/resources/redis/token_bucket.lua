-- Redis 令牌桶：返回 1 允许，0 拒绝
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(data[1])
local last_refill = tonumber(data[2])

if tokens == nil then
  tokens = capacity
  last_refill = now
end

local elapsed = math.max(0, now - last_refill)
tokens = math.min(capacity, tokens + elapsed * rate / 1000.0)

if tokens < requested then
  redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
  local ttl = math.ceil(capacity / math.max(rate, 0.001)) + 120
  redis.call('EXPIRE', key, ttl)
  return 0
end

tokens = tokens - requested
redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
local ttl = math.ceil(capacity / math.max(rate, 0.001)) + 120
redis.call('EXPIRE', key, ttl)
return 1
