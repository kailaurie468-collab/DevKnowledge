package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.UserFeedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {
}
