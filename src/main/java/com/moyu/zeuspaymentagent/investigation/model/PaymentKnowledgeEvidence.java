package com.moyu.zeuspaymentagent.investigation.model;

/**
 * 调查过程中从知识库召回的处理建议。
 */
public record PaymentKnowledgeEvidence(
        String source,
        String title,
        Integer chunkIndex,
        Double score,
        String content) {
}
