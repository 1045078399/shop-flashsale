package cn.wolfcode.job;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.feign.SeckillProductFeignApi;
import cn.wolfcode.redis.SeckillRedisKey;
import com.alibaba.fastjson.JSON;
import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 每天凌晨 00:00 执行
 * 将今天的秒杀商品数据, 全部缓存到 Redis 中
 */
@Component
@Setter
@Getter
@RefreshScope
@Slf4j
public class SeckillProductInitJob implements SimpleJob {

    @Value("${job.seckillProduct.cron}")
    private String cron;
    @Value("${job.seckillProduct.shardingCount}")
    private Integer shardingCount;
    @Value("${job.seckillProduct.shardingParameters}")
    private String shardingParameters;
    @Value("${job.seckillProduct.dataFlow}")
    private boolean dataFlow;

    @Autowired
    private SeckillProductFeignApi seckillProductFeignApi;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void execute(ShardingContext shardingContext) {
        // 分片参数利用场次进行分片
        String time = shardingContext.getShardingParameter();

        // 先清空之前的数据
        String key = SeckillRedisKey.SECKILL_PRODUCT_LIST.join(time);
        stringRedisTemplate.delete(key);

        // 调用秒杀服务的接口, 查询秒杀商品数据
        Result<List<SeckillProductVo>> result = seckillProductFeignApi.selectTodayListByTime(Integer.valueOf(time));
        if (result.hasError() || result.getData() == null) {
            log.warn("[秒杀商品数据预热] 查询秒杀商品数据失败, 远程服务异常. res={}", JSON.toJSONString(result));
            return;
        }

        List<SeckillProductVo> productVoList = result.getData();
        log.info("[秒杀商品数据预热] 准备开始预热秒杀商品数据, 当前场次:{}, 本次缓存的数据:{}", time, productVoList.size());

        // 将数据存入 Redis : List
        // key=TODAY:{time}:SECKILL:PRODUCTS
        // value=SeckillProductVo => {json}

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 30);

        long endTime = calendar.getTime().getTime();
        long remain = (endTime - new Date().getTime()) / 1000;

        for (SeckillProductVo vo : productVoList) {
            String json = JSON.toJSONString(vo);
            stringRedisTemplate.opsForList().rightPush(key, json);

            // 将库存额外存入 Redis
            String stockCountKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.join(vo.getTime() + "");
            stringRedisTemplate.opsForHash().put(stockCountKey, vo.getId() + "", vo.getStockCount() + "");
            // 设置过期时间为今天 23:30 失效
            stringRedisTemplate.expire(stockCountKey, remain, TimeUnit.SECONDS);
        }
        log.info("[秒杀商品数据预热] 数据预热完成...");
    }
}
