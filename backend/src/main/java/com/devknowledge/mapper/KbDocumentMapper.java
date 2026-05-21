package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.KbDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {
}
