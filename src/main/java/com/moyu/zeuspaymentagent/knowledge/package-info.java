/**
 * 知识库模块。
 *
 * <p>入库流程：管理接口手动触发 -> 读取 doc 目录 Markdown 文档 -> 按标题和段落切分 Chunk
 * -> 通过 Embedding 模型向量化 -> 写入 Chroma 向量库。</p>
 *
 * <p>检索流程：LLM 调用知识库 Tool -> Chroma 相似度检索 -> 返回来源、标题和片段内容。</p>
 */
package com.moyu.zeuspaymentagent.knowledge;
