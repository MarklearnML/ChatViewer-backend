package com.chatviewer.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatviewer.blog.pojo.Draft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;

@Mapper
public interface DraftMapper extends BaseMapper<Draft> {

}
