package com.chatviewer.blog.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chatviewer.blog.base.Result;
import com.chatviewer.blog.dto.ArticleDto;
import com.chatviewer.blog.mapper.DraftMapper;
import com.chatviewer.blog.pojo.Article;
import com.chatviewer.blog.pojo.Draft;
import com.chatviewer.blog.service.ArticleService;
import com.chatviewer.blog.service.DraftService;
import com.chatviewer.blog.utils.ContextHolderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * @author ChatViewer
 */
@Slf4j
@CrossOrigin
@RestController
@RequestMapping("/article")
public class ArticleController {

    @Resource
    ArticleService articleService;

    @Resource
    DraftService draftService;

    /**
     * 根据articleId，
     * 返回文章信息、作者信息、Redis点赞数、如果用户已登录，同时返回文章的点赞状态
     * @param articleId 文章Id
     * @return ArticleDto
     */
    @GetMapping("")
    public Result<Object> getWithAuthor(Long articleId) throws ExecutionException, InterruptedException {
        return Result.success(articleService.getWithAuthor(articleId));
    }

    /**
     * 根据输入框输入进行自动补全功能
     * @param prefix
     * @return
     */
    @GetMapping("/search")
    public Result<Object> search(String prefix) {
        List<String> searchCompletion = articleService.searchCompletion(prefix);
        return Result.success(searchCompletion);
    }


    /**
     * 返回分页信息与作者信息
     * @param page 页面id
     * @param pageSize 每页item数
     * @param categoryId 是否需要查询分类
     * @return 带author信息的当前页面列表
     */
    @GetMapping("/page")
    public Result<Object> pageWithAuthor(int page, int pageSize, Long categoryId, String search) {
        if(search==null||"".equals(search)) {
            return Result.success(articleService.pageWithAuthor(page, pageSize, categoryId));
        }else {
            return Result.success(articleService.pageWithAuthor(page, pageSize, categoryId, search));
        }

    }


    /**
     * 上传文件，上传文章图像、录音音频均调用此接口
     * @param file File[]
     * @return 包含上传路径信息，详见ArticleServiceImpl
     */
    @PostMapping("/uploadFile")
    public Result<Object> uploadFile(MultipartFile file) {
        return articleService.uploadFile(file);
    }


    /**
     * 添加文章
     * @param map 属性基本与Article相同，但没有categoryId，而是articleCategoryList，形如["123", "456", "789"]，表示有层次的目录列表
     * @return 操作是否成功
     */
    @PostMapping("/add")
    public Result<Object> addArticle(@RequestBody Map<String, Object> map) {
        Article article = BeanUtil.mapToBean(map, Article.class, false, CopyOptions.create());
        // 从分类列表取得分类Id
        Object tmp = map.get("articleCategoryList");
        if (!(tmp instanceof List)) {
            return Result.fail("articleCategoryList不是字符串列表，参数格式错误");
        }
        // 分类id列表格式转换
        List<String> articleCategoryList = (List<String>) tmp;
        // String转换为Long
        article.setCategoryId(Long.parseLong(articleCategoryList.get(articleCategoryList.size() - 1)));
        articleService.addArticle(article);
        return Result.success();
    }


    /**
     * 保存草稿
     * @param map
     * @return
     */
    @PostMapping("/save")
    public Result<Object> saveArticle(@RequestBody Map<String, Object> map) {
        Article article = BeanUtil.mapToBean(map, Article.class, false, CopyOptions.create());
        // 从分类列表取得分类Id
        Object tmp = map.get("articleCategoryList");
        if (!(tmp instanceof List)) {
            return Result.fail("articleCategoryList不是字符串列表，参数格式错误");
        }
        // 分类id列表格式转换
        List<String> articleCategoryList = (List<String>) tmp;
        // String转换为Long
        article.setCategoryId(Long.parseLong(articleCategoryList.get(articleCategoryList.size() - 1)));
        Draft draft = new Draft();
        BeanUtil.copyProperties(article, draft);
        draftService.saveDraft(draft);
        return Result.success();
    }

    @GetMapping("/getDraft")
    public Result<Object> getDraft(Long draftId) {
        Draft draft = draftService.getById(draftId);
        return Result.success(draft);
    }

    @GetMapping("/draftList")
    public Result<Object> draftList(int page, int pageSize) {
        Long userId = ContextHolderUtil.getUserId();
        if(userId==null) return Result.fail("NOT_LOGIN");
        Page<ArticleDto> dtoPage = draftService.pageDraft(page, pageSize);
        return Result.success(dtoPage);
    }

}
