package com.chatviewer.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chatviewer.blog.base.Result;
import com.chatviewer.blog.dto.ArticleDto;
import com.chatviewer.blog.pojo.Draft;
import org.springframework.web.bind.annotation.RequestParam;

public interface DraftService extends IService<Draft> {

    /**
     * 保存草稿
     * @param draft
     */
    public void saveDraft(Draft draft);

    /**
     * 获取保存好的草稿
     * @param page
     * @param pageSize
     * @return
     */
    Page<ArticleDto> pageDraft(@RequestParam int page, @RequestParam int pageSize);


}
