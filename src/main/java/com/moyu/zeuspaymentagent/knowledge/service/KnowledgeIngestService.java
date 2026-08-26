package com.moyu.zeuspaymentagent.knowledge.service;

import com.moyu.zeuspaymentagent.knowledge.model.KnowledgeChunk;
import com.moyu.zeuspaymentagent.knowledge.model.KnowledgeIngestResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeIngestService {

    private static final int MAX_CHUNK_CHARS = 1200;
    private static final int OVERLAP_CHARS = 160;
    private static final int EMBEDDING_BATCH_SIZE = 10;

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final Path docDirectory;

    public KnowledgeIngestService(
            ObjectProvider<VectorStore> vectorStoreProvider,
            @Value("${zeus.knowledge.doc-dir:doc}") String docDirectory) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.docDirectory = Path.of(docDirectory);
    }

    /**
     * 入库主流程：加载并切分 Markdown -> 转为 Spring AI Document -> 覆盖写入 Chroma。
     */
    public KnowledgeIngestResult ingest() {
        var vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException("VectorStore is not available. Check Chroma and embedding configuration.");
        }

        var chunks = loadChunks();
        if (chunks.isEmpty()) {
            return new KnowledgeIngestResult(0, 0, List.of());
        }

        var documents = chunks.stream()
                .map(this::toDocument)
                .toList();

        vectorStore.delete(documents.stream().map(Document::getId).toList());
        addDocumentsInBatches(vectorStore, documents);

        return new KnowledgeIngestResult(
                countSources(chunks),
                chunks.size(),
                chunks.stream().map(KnowledgeChunk::source).distinct().sorted().toList());
    }

    /**
     * 只统计将要导入的文档和 Chunk 数，不访问向量库。
     */
    public KnowledgeIngestResult preview() {
        var chunks = loadChunks();
        return new KnowledgeIngestResult(
                countSources(chunks),
                chunks.size(),
                chunks.stream().map(KnowledgeChunk::source).distinct().sorted().toList());
    }

    public List<KnowledgeChunk> previewChunks() {
        return loadChunks();
    }

    /**
     * 从 doc 目录读取全部 Markdown 文件，并保持文件名排序，保证 Chunk ID 稳定。
     */
    private List<KnowledgeChunk> loadChunks() {
        if (!Files.isDirectory(docDirectory)) {
            throw new IllegalStateException("Knowledge doc directory does not exist: " + docDirectory.toAbsolutePath());
        }

        try (var paths = Files.list(docDirectory)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .flatMap(path -> splitFile(path).stream())
                    .toList();
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to read knowledge doc directory: " + docDirectory.toAbsolutePath(), ex);
        }
    }

    /**
     * 单文件切分：先按标题分段，再按段落长度切成可向量化的 Chunk。
     */
    private List<KnowledgeChunk> splitFile(Path path) {
        var source = path.getFileName().toString();
        var content = readFile(path);
        var sections = splitByHeading(content);
        var chunks = new ArrayList<KnowledgeChunk>();

        for (var section : sections) {
            chunks.addAll(splitSection(source, section.title(), section.content(), chunks.size()));
        }
        return chunks;
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to read knowledge file: " + path.toAbsolutePath(), ex);
        }
    }

    private List<Section> splitByHeading(String content) {
        var sections = new ArrayList<Section>();
        var currentTitle = "Untitled";
        var current = new StringBuilder();

        for (var line : content.split("\\R")) {
            if (line.startsWith("#")) {
                if (!current.isEmpty()) {
                    sections.add(new Section(currentTitle, current.toString().trim()));
                    current.setLength(0);
                }
                currentTitle = line.replaceFirst("^#+\\s*", "").trim();
            }
            current.append(line).append('\n');
        }

        if (!current.isEmpty()) {
            sections.add(new Section(currentTitle, current.toString().trim()));
        }
        return sections;
    }

    private List<KnowledgeChunk> splitSection(String source, String title, String content, int startIndex) {
        var chunks = new ArrayList<KnowledgeChunk>();
        var current = new StringBuilder();

        for (var paragraph : content.split("\\n\\s*\\n")) {
            var normalized = paragraph.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }

            if (current.length() > 0 && current.length() + normalized.length() + 2 > MAX_CHUNK_CHARS) {
                addChunk(chunks, source, title, startIndex + chunks.size(), current.toString().trim());
                current = new StringBuilder(overlapOf(current.toString()));
            }

            if (normalized.length() > MAX_CHUNK_CHARS) {
                if (!current.isEmpty()) {
                    addChunk(chunks, source, title, startIndex + chunks.size(), current.toString().trim());
                    current.setLength(0);
                }
                splitLongParagraph(chunks, source, title, normalized, startIndex);
            }
            else {
                current.append(normalized).append("\n\n");
            }
        }

        if (!current.isEmpty()) {
            addChunk(chunks, source, title, startIndex + chunks.size(), current.toString().trim());
        }

        return chunks;
    }

    private void splitLongParagraph(
            List<KnowledgeChunk> chunks, String source, String title, String paragraph, int startIndex) {
        var start = 0;
        while (start < paragraph.length()) {
            var end = Math.min(start + MAX_CHUNK_CHARS, paragraph.length());
            addChunk(chunks, source, title, startIndex + chunks.size(), paragraph.substring(start, end).trim());
            if (end == paragraph.length()) {
                break;
            }
            start = Math.max(0, end - OVERLAP_CHARS);
        }
    }

    private void addChunk(List<KnowledgeChunk> chunks, String source, String title, int index, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        chunks.add(new KnowledgeChunk(
                stableChunkId(source, index),
                source,
                index,
                title,
                content.length(),
                content));
    }

    private String overlapOf(String content) {
        if (content.length() <= OVERLAP_CHARS) {
            return content;
        }
        return content.substring(content.length() - OVERLAP_CHARS);
    }

    private Document toDocument(KnowledgeChunk chunk) {
        return new Document(chunk.id(), chunk.content(), Map.of(
                "source", chunk.source(),
                "chunkIndex", chunk.chunkIndex(),
                "title", chunk.title(),
                "charLength", chunk.charLength()));
    }

    /**
     * Qwen Embedding 单次最多处理 10 条文本，这里按批次写入向量库。
     */
    private void addDocumentsInBatches(VectorStore vectorStore, List<Document> documents) {
        for (var start = 0; start < documents.size(); start += EMBEDDING_BATCH_SIZE) {
            var end = Math.min(start + EMBEDDING_BATCH_SIZE, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
    }

    private String stableChunkId(String source, int index) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest((source + ":" + index).getBytes(StandardCharsets.UTF_8));
            return "kb-" + HexFormat.of().formatHex(hash).substring(0, 24);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private int countSources(List<KnowledgeChunk> chunks) {
        return (int) chunks.stream().map(KnowledgeChunk::source).distinct().count();
    }

    private record Section(String title, String content) {
    }
}
