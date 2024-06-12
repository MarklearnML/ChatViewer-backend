package com.chatviewer.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatviewer.blog.pojo.Problem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author ChatViewer
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    @Delete("DELETE FROM blog_problem WHERE problem_id = #{problemId} AND create_user = #{userId}")
    boolean deleteProblemByIdAndUserId(@Param("problemId") Long problemId, @Param("userId") Long userId);
}
