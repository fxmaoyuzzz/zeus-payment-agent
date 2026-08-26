package com.moyu.zeuspaymentagent.knowledge.model;

/**
 * 单条知识库命中结果，包含来源、标题、相似度和片段内容。
 */
public record KnowledgeSearchHit(
        String source,
        String title,
        Integer chunkIndex,
        Double score,
        String content) {
}
