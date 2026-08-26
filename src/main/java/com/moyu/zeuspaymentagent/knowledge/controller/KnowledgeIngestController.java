package com.moyu.zeuspaymentagent.knowledge.controller;

import com.moyu.zeuspaymentagent.knowledge.model.KnowledgeChunk;
import com.moyu.zeuspaymentagent.knowledge.model.KnowledgeIngestResult;
import com.moyu.zeuspaymentagent.knowledge.service.KnowledgeIngestService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeIngestController {

    private final KnowledgeIngestService knowledgeIngestService;

    public KnowledgeIngestController(KnowledgeIngestService knowledgeIngestService) {
        this.knowledgeIngestService = knowledgeIngestService;
    }

    /**
     * 预览导入摘要，不写入向量库。
     */
    @GetMapping("/preview")
    public KnowledgeIngestResult preview() {
        return knowledgeIngestService.preview();
    }

    /**
     * 预览实际切分后的 Chunk，便于人工确认切分质量。
     */
    @GetMapping("/chunks/preview")
    public List<KnowledgeChunk> previewChunks() {
        return knowledgeIngestService.previewChunks();
    }

    /**
     * 手动导入流程入口：读取 doc 文档 -> 切分 Chunk -> 写入 Chroma。
     */
    @PostMapping("/ingest")
    public KnowledgeIngestResult ingest() {
        return knowledgeIngestService.ingest();
    }
}
