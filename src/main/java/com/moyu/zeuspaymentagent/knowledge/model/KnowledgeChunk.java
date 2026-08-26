package com.moyu.zeuspaymentagent.knowledge.model;

/**
 * 知识库文档切分后的最小入库单元。
 */
public record KnowledgeChunk(
        String id,
        String source,
        int chunkIndex,
        String title,
        int charLength,
        String content) {
}
