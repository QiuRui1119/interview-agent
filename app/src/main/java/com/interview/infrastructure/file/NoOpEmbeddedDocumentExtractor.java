package com.interview.infrastructure.file;


import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import java.io.InputStream;
import org.apache.tika.metadata.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.ContentHandler;

@Slf4j
public class NoOpEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

    /**
     * 是否应该解析嵌入文档
     * 
     * @param metadata 文档元数据
     * @return 始终返回 false，禁用嵌入文档解析
     */
    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        // 记录跳过的嵌入文档（使用字符串常量，兼容不同 Tika 版本）
        String resourceName = metadata.get("resourceName");
        if (resourceName != null) {
            log.debug("Skip embedded document: {}", resourceName);
        }
        return false; // 不解析嵌入文档
    }

    @Override
    public void parseEmbedded(InputStream inputStream,ContentHandler handler, Metadata metadata, boolean outputHtml) {
        // 不执行任何操作
    }

}
