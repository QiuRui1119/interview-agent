-- ============================================
-- V3__enable_pgvector_and_create_vector_store.sql
-- 启用 pgvector 扩展并创建向量存储表
-- 兼容 Spring AI 2.0 PgVectorStore
-- ============================================

-- 1. 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 创建向量存储表（Spring AI 2.0 PgVectorStore 标准结构）
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    embedding VECTOR(1024) NOT NULL
);

-- 3. 创建 HNSW 索引（用于 embedding 向量的近似最近邻搜索）
-- 使用 vector_cosine_ops 支持余弦相似度查询
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw 
ON vector_store 
USING HNSW (embedding vector_cosine_ops);

-- 4. 创建 GIN 索引（用于 metadata 的 JSONB 查询）
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_gin 
ON vector_store 
USING GIN (metadata);

-- 5. 创建表达式索引（用于按 kb_id 快速过滤）
-- Spring AI 2.0 会使用 metadata->>'kb_id' 作为过滤条件
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_kb_id 
ON vector_store 
((metadata->>'kb_id'));