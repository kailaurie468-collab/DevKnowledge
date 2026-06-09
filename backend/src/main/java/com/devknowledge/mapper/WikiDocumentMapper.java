package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.WikiDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiDocumentMapper extends BaseMapper<WikiDocument> {
}
