---
--- 秒杀补偿脚本：订单发送/处理失败时恢复 Redis 库存，并移除用户的一人一单标记。
local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 恢复库存
redis.call('incrby', stockKey, 1)
-- 移除下单标记
redis.call('srem', orderKey, userId)

return 1
