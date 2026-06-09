package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.WikiEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiEntityMapper extends BaseMapper<WikiEntity> {
}
