
import com.alibaba.fastjson.JSON;
import com.chatviewer.blog.BlogApplication;
import com.chatviewer.blog.mapper.ArticleMapper;
import com.chatviewer.blog.pojo.Article;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.AnalyzeRequest;
import org.elasticsearch.client.indices.AnalyzeResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;


@SpringBootTest(classes = BlogApplication.class)
public class TestApplication {
    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    /**
     * 批量将数据库的数据导入进ES里
     * @throws IOException
     */
    @Test
    public void importDataToElasticsearch() throws IOException {
        List<Article> articles = articleMapper.selectList(null);
        BulkRequest bulkRequest = new BulkRequest();

        for (Article article : articles) {
            // 将title进行分词  放入suggest中 实现自动补全title的分词
            AnalyzeRequest analyzeRequest = AnalyzeRequest.withGlobalAnalyzer("ik_max_word", article.getArticleTitle());
            AnalyzeResponse analyzeResponse = restHighLevelClient.indices().analyze(analyzeRequest, RequestOptions.DEFAULT);
            List<String> tokens = analyzeResponse.getTokens().stream().map(AnalyzeResponse.AnalyzeToken::getTerm).collect(Collectors.toList());
            // 将分词放入suggest
            article.setSuggest(tokens.toArray(new String[0]));
            // 创建索引 并把doc逐条插入
            IndexRequest indexRequest = new IndexRequest("blog")
                    .id(article.getArticleId().toString())
                    .source(JSON.toJSONString(article), XContentType.JSON);

            bulkRequest.add(indexRequest);
        }
        // 整体插入
        BulkResponse bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
            System.out.println("Bulk operation had failures: " + bulkResponse.buildFailureMessage());
        }
    }

}
