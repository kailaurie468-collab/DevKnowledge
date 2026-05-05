package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.Demo;
import org.apache.ibatis.annotations.Mapper;

/**
 * Demo Mapper
 */
@Mapper
public interface DemoMapper extends BaseMapper<Demo> {
}
