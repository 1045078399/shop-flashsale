package cn.wolfcode.data;

import cn.wolfcode.dto.OrderInfoDto;
import cn.wolfcode.redis.SeckillRedisKey;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

@Slf4j
@Component
@CanalTable("t_order_info")
public class OrderInfoHandler implements EntryHandler<OrderInfoDto> {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void insert(OrderInfoDto orderInfo) {
        String json = JSON.toJSONString(orderInfo);
        log.info("[订单表数据监听] 有新增的订单:{}, 详细数据为={}", orderInfo.getOrderNo(), json);
        redisTemplate.opsForValue().set(SeckillRedisKey.SECKILL_ORDER_CACHE.join(orderInfo.getOrderNo()), json);
    }

    @Override
    public void update(OrderInfoDto before, OrderInfoDto after) {
        String json = JSON.toJSONString(before);
        log.info("[订单表数据监听] 有订单更新:{}, 更新前的数据={}", before.getOrderNo(), json);
        String afterJson = JSON.toJSONString(after);
        log.info("[订单表数据监听] 有订单更新:{}, 更新后的数据={}", after.getOrderNo(), afterJson);
        redisTemplate.opsForValue().set(SeckillRedisKey.SECKILL_ORDER_CACHE.join(after.getOrderNo()), afterJson);
    }

    @Override
    public void delete(OrderInfoDto orderInfo) {
        log.info("[订单表数据监听] 订单被删除了: {}", orderInfo.getOrderNo());
        redisTemplate.delete(SeckillRedisKey.SECKILL_ORDER_CACHE.join(orderInfo.getOrderNo()));
    }
}
