-- 令牌桶限流：KEYS[1]=桶key, ARGV[1]=每秒令牌数, ARGV[2]=桶容量, ARGV[3]=当前毫秒, ARGV[4]=本次消耗
local key       = KEYS[1]
local rate      = tonumber(ARGV[1])
local capacity  = tonumber(ARGV[2])
local now       = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local ttl = math.floor(capacity / rate * 2)  -- 桶重新填满所需时间的 2 倍，够用即可，别让 key 常驻

local bucket = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(bucket[1])
local ts     = tonumber(bucket[2])

if tokens == nil then tokens = capacity end
if ts == nil then ts = now end

-- 按时间差补充令牌（毫秒 → 秒）
local delta = math.max(0, now - ts) / 1000
tokens = math.min(capacity, tokens + delta * rate)

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

-- 用 HSET（HMSET 在高版本 Redis 已废弃）；TTL 兜底防止冷用户 key 永久占用内存
redis.call('HSET', key, 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', key, ttl)

return allowed
