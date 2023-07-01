package cn.wolfcode.service;

import cn.wolfcode.domain.SeckillProductVo;

import java.util.List;


public interface ISeckillProductService {

    List<SeckillProductVo> selectTodayListByTime(Integer time);

    List<SeckillProductVo> selectTodayListByTimeFromRedis(Integer time);

    SeckillProductVo selectByIdAndTime(Long seckillId, Integer time);

    int decrStockCount(Integer time, Long seckillId);

    void incrStockCount(Integer time, Long seckillId);
}
