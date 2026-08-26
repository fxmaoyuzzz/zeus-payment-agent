package com.moyu.zeuspaymentagent.knowledge.tool;

import com.moyu.zeuspaymentagent.audit.service.ToolCallAuditService;
import com.moyu.zeuspaymentagent.knowledge.model.KnowledgeSearchHit;
import com.moyu.zeuspaymentagent.knowledge.model.KnowledgeSearchResult;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 知识库检索 Tool，供 LLM 按需查询 Chroma 中的支付渠道文档和 SOP。
 */
@Service
public class KnowledgeSearchTool {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.2;

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ToolCallAuditService toolCallAuditService;

    public KnowledgeSearchTool(
            ObjectProvider<VectorStore> vectorStoreProvider,
            ToolCallAuditService toolCallAuditService) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.toolCallAuditService = toolCallAuditService;
    }

    /**
     * 检索流程：LLM 提炼查询语句 -> Chroma 相似度检索 -> 返回 TopK 文档片段。
     */
    @Tool(
            name = "search_knowledge_base",
            description = "检索支付知识库。用户询问支付渠道规则、错误码、失败处理 SOP、排查步骤、支付方式差异时优先调用。")
    public KnowledgeSearchResult searchKnowledgeBase(
            @ToolParam(description = "用于知识库相似度检索的查询语句，建议包含支付方式、渠道、错误码或问题现象")
                    String query,
            @ToolParam(required = false, description = "返回的知识片段数量，默认5，最大10")
                    Integer topK) {
        var startedAt = System.currentTimeMillis();
        var args = new Object[] {query, topK};
        try {
            var vectorStore = vectorStoreProvider.getIfAvailable();
            if (vectorStore == null) {
                var result = new KnowledgeSearchResult(query, normalizeTopK(topK), 0, List.of());
                toolCallAuditService.record("search_knowledge_base", getClass().getName(),
                        "searchKnowledgeBase", args, result, null, System.currentTimeMillis() - startedAt);
                return result;
            }

            var request = SearchRequest.builder()
                    .query(query)
                    .topK(normalizeTopK(topK))
                    .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                    .build();

            var hits = vectorStore.similaritySearch(request).stream()
                    .map(this::toHit)
                    .toList();

            var result = new KnowledgeSearchResult(query, request.getTopK(), hits.size(), hits);
            toolCallAuditService.record("search_knowledge_base", getClass().getName(),
                    "searchKnowledgeBase", args, result, null, System.currentTimeMillis() - startedAt);
            return result;
        }
        catch (RuntimeException ex) {
            toolCallAuditService.record("search_knowledge_base", getClass().getName(),
                    "searchKnowledgeBase", args, null, ex, System.currentTimeMillis() - startedAt);
            throw ex;
        }
    }

    private KnowledgeSearchHit toHit(Document document) {
        var metadata = document.getMetadata();
        return new KnowledgeSearchHit(
                stringMetadata(metadata.get("source")),
                stringMetadata(metadata.get("title")),
                integerMetadata(metadata.get("chunkIndex")),
                document.getScore(),
                document.getText());
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK < 1) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private String stringMetadata(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer integerMetadata(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value.toString());
    }
}
