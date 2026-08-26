package com.moyu.zeuspaymentagent.knowledge.model;

import java.util.List;

/**
 * 知识库预览或导入后的统计结果。
 */
public record KnowledgeIngestResult(
        int documentCount,
        int chunkCount,
        List<String> sources) {
}
