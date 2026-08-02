
--1.参数列表
--1.1.优惠券Id
local voucherId=ARGV[1]
--1.2.用户id
local userId=ARGV[2]
--1.3.订单Id
local orderId=ARGV[3]

--2.数据key
--2.1.库存key
--拼接出当前优惠券库存的key
local stockKey='seckill:stock:'..voucherId
--2.2.订单key
local orderKey='seckill:order:'..voucherId

--3.脚本业务
--3.3.判断库存是否充足get stockKey
if(tonumber(redis.call('get',stockKey))<=0) then
    --3.2.库存不足，返回1
    return 1
end
--3.2.判断用户是否下单
if(redis.call('sismember',orderKey,userId)==1)then
    --3.3.存在，说明是重复下单，返回2
    return 2
end
--3.4.扣库存
redis.call('incrby',stockKey,-1)
--3.5.保存用户（一人一单标记）
redis.call('sadd',orderKey,userId)
--3.6.入队逻辑已由Java改为发送到 RabbitMQ，此处不再 XADD；发送失败通过 recoverStock.lua 补偿
return 0

