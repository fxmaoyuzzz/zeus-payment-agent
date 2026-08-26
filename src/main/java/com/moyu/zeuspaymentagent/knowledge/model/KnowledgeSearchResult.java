package com.moyu.zeuspaymentagent.knowledge.model;

import java.util.List;

/**
 * 知识库检索 Tool 返回给 LLM 的结构化结果。
 */
public record KnowledgeSearchResult(
        String query,
        int topK,
        int matchCount,
        List<KnowledgeSearchHit> hits) {
}
