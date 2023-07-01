package cn.wolfcode.mapper;

import cn.wolfcode.domain.PayLog;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

@Mapper
@Component
public interface PayLogMapper {

    void insert(PayLog payLog);
}
