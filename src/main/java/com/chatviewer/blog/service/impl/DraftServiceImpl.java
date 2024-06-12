package com.chatviewer.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatviewer.blog.dto.ArticleDto;
import com.chatviewer.blog.mapper.DraftMapper;
import com.chatviewer.blog.mapper.LikeMapper;
import com.chatviewer.blog.mapper.UserMapper;
import com.chatviewer.blog.pojo.Article;
import com.chatviewer.blog.pojo.Draft;
import com.chatviewer.blog.pojo.Like;
import com.chatviewer.blog.pojo.User;
import com.chatviewer.blog.service.DraftService;
import com.chatviewer.blog.utils.ContextHolderUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
public class DraftServiceImpl extends ServiceImpl<DraftMapper, Draft> implements DraftService {

    @Resource
    UserMapper userMapper;

    /**
     * 保存草稿
     *
     * @param draft
     */
    @Override
    public void saveDraft(Draft draft) {
        // 存在数据库中
        draft.setUserId(ContextHolderUtil.getUserId());
        draft.setCreateTime(LocalDateTime.now());
        draft.setUpdateTime(LocalDateTime.now());
        if(draft.getArticleId()!=null) {
            // 当之前就是草稿时，覆盖之前的草稿
            updateById(draft);
        }else{
            // 当首次作为草稿，保存
            save(draft);
        }

    }

    /**
     * 获取保存好的草稿
     *
     * @param page
     * @param pageSize
     * @return
     */
    @Override
    public Page<ArticleDto> pageDraft(int page, int pageSize) {

        Page<Draft> pageInfo = new Page<>(page, pageSize);

        Long userId = ContextHolderUtil.getUserId();
        LambdaQueryWrapper<Draft> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Draft::getUserId, userId);
        Page<Draft> page1 = page(pageInfo, wrapper);


        Page<ArticleDto> pageInfoPlus = new Page<>();
        BeanUtil.copyProperties(page1, pageInfoPlus, "records");

        // 扩展author信息到article
        List<Draft> drafts = pageInfo.getRecords();
        List<ArticleDto> articleDtos = new ArrayList<>();
        for (Draft draft: drafts) {
            // 查询用户信息
            User user = userMapper.selectById(userId);

            ArticleDto articleDto = new ArticleDto();
            BeanUtil.copyProperties(draft, articleDto);

            // 设置user信息
            articleDto.setUserAvatar(user.getUserAvatar());
            articleDto.setUserName(user.getUserName());
            articleDtos.add(articleDto);
        }

        pageInfoPlus.setRecords(articleDtos);
        return pageInfoPlus;
    }


}
