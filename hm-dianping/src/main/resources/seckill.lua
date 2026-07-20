--参数列表
--优惠券id
local voucherId = ARGV[1]
--用户id
local userId = ARGV[2]
--库存key
local stockKey = 'seckill:stock:' .. voucherId
--订单key
local orderKey = 'seckill:order:' .. voucherId
--查询库存
local stock = redis.call("get", stockKey)
--检查库存是否存在或不足
if not stock or tonumber(stock) <= 0 then
    --库存不存在或不足，返回1
    return 1
end
--判断用户是否下单
if(redis.call('sismember', orderKey, userId) == 1) then
    --存在，说明重复下单，返回2
    return 2
end
--扣减库存
redis.call('incrby', stockKey, -1)
--创建订单
redis.call('sadd', orderKey, userId)
return 0