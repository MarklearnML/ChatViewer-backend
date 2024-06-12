package com.chatviewer.blog.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chatviewer.blog.base.Result;
import com.chatviewer.blog.dto.ArticleDto;
import com.chatviewer.blog.mapper.ArticleMapper;
import com.chatviewer.blog.mapper.DraftMapper;
import com.chatviewer.blog.mapper.UserMapper;
import com.chatviewer.blog.pojo.Article;
import com.chatviewer.blog.pojo.Category;
import com.chatviewer.blog.pojo.Draft;
import com.chatviewer.blog.pojo.User;
import com.chatviewer.blog.service.ArticleService;
import com.chatviewer.blog.service.CategoryService;
import com.chatviewer.blog.service.LikeService;
import com.chatviewer.blog.utils.AliyunOssUtil;
import com.chatviewer.blog.utils.ContextHolderUtil;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.replication.ReplicationRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.MatchedQueriesPhase;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.suggest.SuggestBuilder;
import org.elasticsearch.search.suggest.SuggestBuilders;
import org.elasticsearch.search.suggest.completion.CompletionSuggestionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static com.chatviewer.blog.utils.ThreadPoolUtil.pool;

/**
 * @author ChatViewer
 */
@Slf4j
@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Resource
    ArticleMapper articleMapper;

    @Resource
    DraftMapper draftMapper;

    @Resource
    UserMapper userMapper;

    @Resource
    LikeService likeService;

    @Resource
    CategoryService categoryService;

    @Resource
    AliyunOssUtil aliyunOssUtil;

    @Autowired
    private RestHighLevelClient restHighLevelClient;


    @Override
    public ArticleDto getWithAuthor(Long articleId) throws ExecutionException, InterruptedException {
        ArticleDto articleDto = new ArticleDto();

        // UserId从Context中获取，不能放至线程任务里，否则得到的会是null
        Long userId = ContextHolderUtil.getUserId();

        // 任务一: MySQL进行连接，查询带作者的文章信息，复制属性至articleDto
        CompletableFuture<Void> taskQueryArticle = CompletableFuture.runAsync(() ->
                        BeanUtil.copyProperties(
                                articleMapper.getWithAuthor(articleId),
                                articleDto,
                                // 不拷贝like相关属性，避免覆盖
                                "likeCounts", "isLike"), pool);

        // 任务二：Redis查询文章点赞数
        CompletableFuture<Void> taskLikeCounts = CompletableFuture.runAsync(
                () -> articleDto.setLikeCounts(likeService.queryEntityLikeCounts(0, articleId, true)), pool);


        // 任务三：如果用户登录，需要查询文章的点赞状态
        CompletableFuture<Void> taskLikeState = CompletableFuture.runAsync(() -> {
            if (userId != null) {
                articleDto.setIsLike(likeService.queryUserLikeEntity(userId, 0, articleId));
            }
            else {
                articleDto.setIsLike(false);
            }
        }, pool);

        // 使用allOf，使得三个任务执行完，才能继续
        CompletableFuture<Void> combinedTask = CompletableFuture.allOf(taskQueryArticle, taskLikeCounts, taskLikeState);
        combinedTask.get();

        return articleDto;
    }


    @Override
    public Page<ArticleDto> pageWithAuthor(int page, int pageSize, Long categoryId) {
        // 设置好分页所用Page
        Page<Article> pageInfo = new Page<>(page, pageSize);

        // 构造条件构造器, 添加过滤条件和排序条件
        LambdaQueryWrapper<Article> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(Article::getUpdateTime);

        // 得到子分类列表，判断是否在子分类中
        if (categoryId != null) {
            List<Long> ids = categoryService.childrenIdOf(categoryId);
            queryWrapper.in(Article::getCategoryId, ids);
        }

        // 查询，转换为输出格式
        this.page(pageInfo, queryWrapper);

        // 拷贝旧Page到新Page，忽略records属性
        Page<ArticleDto> pageInfoPlus = new Page<>();
        BeanUtil.copyProperties(pageInfo, pageInfoPlus, "records");

        // 扩展author信息到article
        List<Article> articles = pageInfo.getRecords();
        List<ArticleDto> articleDtos = new ArrayList<>();
        for (Article article: articles) {
            // 查询用户信息
            Long userId = article.getUserId();
            User user = userMapper.selectById(userId);

            ArticleDto articleDto = new ArticleDto();
            BeanUtil.copyProperties(article, articleDto);

            // 设置user信息
            articleDto.setUserAvatar(user.getUserAvatar());
            articleDto.setUserName(user.getUserName());
            articleDtos.add(articleDto);
        }

        pageInfoPlus.setRecords(articleDtos);
        return pageInfoPlus;
    }

    /**
     * 根据search去es返回的分页信息与作者信息  此处用了方法的重载
     *
     * @param page
     * @param pageSize
     * @param categoryId
     * @param search
     * @return
     */
    @Override
    public Page<ArticleDto> pageWithAuthor(int page, int pageSize, Long categoryId, String search) {

        // 设置好分页所用Page
        Page<Article> pageInfo = new Page<>(page, pageSize);

        // 创建SearchRequest
        SearchRequest searchRequest = new SearchRequest("blog");
        // 构建查询源
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 构建查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("all", search);
        boolQuery.must(matchQuery);

        // 当categoryId不为null时
        if (categoryId != null) {
            boolQuery.filter(QueryBuilders.termQuery("categoryId", categoryId));
        }

        sourceBuilder.query(boolQuery);
        // 设置高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field(new HighlightBuilder.Field("articleTitle").requireFieldMatch(false));
        highlightBuilder.field(new HighlightBuilder.Field("articleAbstract").requireFieldMatch(false));
        highlightBuilder.field(new HighlightBuilder.Field("articleContent").requireFieldMatch(false).preTags("<em>").postTags("</em>"));
        sourceBuilder.highlighter(highlightBuilder);

        // 将sourceBuilder放入searchRequest中
        searchRequest.source(sourceBuilder);

        List<ArticleDto> articleDtos = new ArrayList<>();
        long totalHits = 0;

        try {
            // 执行搜索请求
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 处理搜索响应
            SearchHits hits = searchResponse.getHits();
            totalHits = hits.getTotalHits().value;

            for (SearchHit hit : hits) {
                Map<String, Object> sourceAsMap = hit.getSourceAsMap();

                ArticleDto articleDto = new ArticleDto();
                articleDto.setArticleId((Long) sourceAsMap.get("articleId"));
                articleDto.setArticleTitle((String) sourceAsMap.get("articleTitle"));
                articleDto.setArticleAbstract((String) sourceAsMap.get("articleAbstract"));
                articleDto.setArticleContent((String) sourceAsMap.get("articleContent"));
                articleDto.setCategoryId((Long) sourceAsMap.get("categoryId"));
                articleDto.setUserId((Long) sourceAsMap.get("userId"));
                articleDto.setArticlePic((String) sourceAsMap.get("articlePic"));

                // 设置高亮字段（如果有）
                Map<String, HighlightField> highlightFields = hit.getHighlightFields();
                if (highlightFields.containsKey("articleTitle")) {
                    articleDto.setArticleTitle(highlightFields.get("articleTitle").fragments()[0].string());
                }
                if (highlightFields.containsKey("articleAbstract")) {
                    articleDto.setArticleAbstract(highlightFields.get("articleAbstract").fragments()[0].string());
                }
                if (highlightFields.containsKey("articleContent")) {
                    articleDto.setArticleContent(highlightFields.get("articleContent").fragments()[0].string());
                }

                // 查询用户信息
                Long userId = articleDto.getUserId();
                User user = userMapper.selectById(userId);
                articleDto.setUserAvatar(user.getUserAvatar());
                articleDto.setUserName(user.getUserName());

                articleDtos.add(articleDto);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 创建Page对象并设置分页信息
        Page<ArticleDto> pageInfoPlus = new Page<>(page, pageSize, totalHits);
        pageInfoPlus.setRecords(articleDtos);

        return pageInfoPlus;

    }


    @Override
    public Result<Object> uploadFile(@RequestBody MultipartFile file) {
        // 从原始文件名得到扩展名
        String originFileName = file.getOriginalFilename();
        assert originFileName != null;
        String extension = originFileName.substring(originFileName.lastIndexOf('.'));
        // 重命名
        String objectName = "article_picture/chat_viewer_" + UUID.randomUUID() + extension;
        try {
            // 调用阿里云OSS工具类，上传文件
            String filePath = aliyunOssUtil.uploadFile(file.getBytes(), objectName);

            // 按Vditor文档要求组装返回结果
            // { "msg": "", "code": 0,
            // "data": { "errFiles": ['filename', 'filename2'], "succMap": { "filename3": "filepath3"}}}
            Map<String, Object> uploadDto = new HashMap<>(2);
            Map<String, Object> successFiles = new HashMap<>(4);
            successFiles.put(originFileName, filePath);
            uploadDto.put("succMap", successFiles);
            uploadDto.put("errFiles", new ArrayList<>());
            Result<Object> res = Result.success(uploadDto);
            res.setCode(0);
            res.setMsg("");
            return res;
        }
        catch (Exception e) {
            log.info("file upload failed { }", e);
        }
        return Result.fail();
    }


    @Transactional
    @Override
    public void addArticle(Article article) {
        Long articleId = article.getArticleId();
        // 如果是草稿，则需要删除草稿
        if(articleId!=null) {
            draftMapper.deleteById(articleId);
        }

        // 存在数据库中
        article.setUserId(ContextHolderUtil.getUserId());
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        article.setLikeCounts(0);
        article.setCommentCounts(0);
        save(article);

        // 存在ES中
        IndexRequest indexRequest = new IndexRequest("blog").id(article.getArticleId().toString())
                .source(JSON.toJSONString(article), XContentType.JSON);

        try {
            IndexResponse response = restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }




    @Override
    public void addCommentCounts(Long articleId) {
        Article article = this.getById(articleId);
        article.setCommentCounts(article.getCommentCounts() + 1);
        this.updateById(article);
    }

    /**
     * 实现搜索框的自动补全功能
     *
     * @param prefix
     * @return
     */
    @Override
    public List<String> searchCompletion(String prefix) {
        List<String> suggestions = new ArrayList<>();

        SearchRequest searchRequest = new SearchRequest("blog");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        CompletionSuggestionBuilder completionSuggestionBuilder = SuggestBuilders.completionSuggestion("suggest")
                .prefix(prefix)
                .skipDuplicates(true)
                .size(10);

        SuggestBuilder suggestBuilder = new SuggestBuilder();
        suggestBuilder.addSuggestion("suggestion", completionSuggestionBuilder);

        searchSourceBuilder.suggest(suggestBuilder);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = null;
        try {
            searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        searchResponse.getSuggest().getSuggestion("suggestion").getEntries().forEach(entry ->
                entry.getOptions().forEach(option -> suggestions.add(option.getText().string()))
        );

        return suggestions;
    }

}
