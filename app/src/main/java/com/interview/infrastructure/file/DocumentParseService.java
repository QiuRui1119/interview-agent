package com.interview.infrastructure.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.io.IOException;
import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;
import com.interview.common.exception.BusinessException;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.parser.pdf.PDFParserConfig;
import com.interview.common.exception.ErrorCode;
import java.io.ByteArrayInputStream;

/**
 * 通用文档解析服务
 * 使用 Apache Tika 解析多种文档格式，提取文本内容
 * 供知识库和简历模块共同使用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseService {

    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024; // 5MB
    //private final TextCleaningService textCleaningService;

    /**
     * 解析上传的文件，提取文本内容
     *
     * @param file 上传的文件（支持PDF、DOCX、DOC、TXT、MD等）
     * @return 提取的文本内容
     */
    public String parseContent(MultipartFile file) {

        String fileName = file.getOriginalFilename();
        log.info("开始解析文件内容: {}", fileName);

        if(file.isEmpty()||file.getSize() == 0) {
            log.warn("文件为空: {}", fileName);
            return "";
        }
        try (InputStream inputStream = file.getInputStream()) {
            // 使用 Apache Tika 解析文件内容
            String content = parseContent(inputStream);
            // 清理提取的文本
            //String cleanedText = textCleaningService.cleanText(content);
            log.info("文件解析完成: {}, 提取文本长度: {}", fileName, content.length());
            return content;
        } catch (Exception e) {
            log.error("解析文件时发生错误: {}", fileName, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 核心解析方法：使用显式 Parser + Context 方式解析文档
     * <p>
     * 优化点：
     * 1. 使用 BodyContentHandler 只提取正文内容
     * 2. 禁用 EmbeddedDocumentExtractor，不解析嵌入资源（图片、附件）
     * 3. 配置 PDFParserConfig，关闭图片和注释提取
     * 4. 显式指定 Parser 到 Context，增强健壮性
     *
     * @param inputStream 文件输入流
     * @return 提取的文本内容
     * @throws IOException     IO 异常
     * @throws TikaException   Tika 解析异常
     * @throws SAXException    SAX 解析异常
     */
    public String parseContent(InputStream inputStream) throws IOException, TikaException, SAXException {
        
        //1.创建自动检测解析器
        AutoDetectParser parser = new AutoDetectParser();

        //2.创建内容处理器，只提取正文内容，限制最大文本长度
        BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);

        //3.创建元数据对象
        Metadata metadata = new Metadata();

        //4.创建解析上下文
        ParseContext context = new ParseContext();

        //5. 显式指定 Parser 到 Context（增强健壮性）
        context.set(Parser.class, parser);

        //6. 禁用嵌入文档解析（关键：避免提取图片引用和临时文件路径）
        context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());

        //7.PDF 专用配置：关闭图片提取，按位置排序文本
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(false);
        pdfConfig.setSortByPosition(true);
        context.set(PDFParserConfig.class, pdfConfig);

        //8.执行解析
        parser.parse(inputStream, handler, metadata, context);

        return handler.toString();
    }

    /**
     * 从存储下载文件并解析内容
     *
     * @param storageService   文件存储服务
     * @param storageKey       存储键
     * @param originalFilename 原始文件名
     * @return 提取的文本内容
     */
    public String downloadAndParseContent(FileStorageService fileStorageService, String storageKey, String originalFilename) {
        try{
            byte[] fileBytes = fileStorageService.downloadFile(storageKey);
            if (fileBytes == null || fileBytes.length == 0) {
                log.warn("下载文件为空: {}", originalFilename);
                return "";
            }
            log.info("下载文件成功: {}", originalFilename);
            try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
                return parseContent(inputStream);
            }
        } catch (Exception e) {
            log.error("下载并解析文件时发生错误: {}", originalFilename, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件下载或解析失败: " + e.getMessage());
        }
    }
}

