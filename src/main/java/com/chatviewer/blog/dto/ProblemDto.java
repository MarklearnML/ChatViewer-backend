package com.chatviewer.blog.dto;

import com.chatviewer.blog.pojo.Problem;
import lombok.Data;

@Data
public class ProblemDto extends Problem {
    /**
     * 当前的用户是否提出问题的人
     */
    private Boolean isUser;
}
