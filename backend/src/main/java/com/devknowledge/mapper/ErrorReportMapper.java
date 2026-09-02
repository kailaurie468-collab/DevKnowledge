package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.ErrorReport;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErrorReportMapper extends BaseMapper<ErrorReport> {
}
