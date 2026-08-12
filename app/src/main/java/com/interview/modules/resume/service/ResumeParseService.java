package com.interview.modules.resume.service;

import org.springframework.stereotype.Service;
import com.interview.infrastructure.file.DocumentParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import com.interview.infrastructure.file.FileStorageService;
/**
 * 简历解析服务
 * 委托给通用的 DocumentParseService 处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseService {
    private final DocumentParseService documentParseService;
    private final FileStorageService fileStorageService;

    /**
     * 解析上传的简历文件，提取文本内容
     *
     * @param file 上传的文件（支持PDF、DOCX、DOC、TXT、MD等）
     * @return 提取的文本内容
     */
    public String parseResumeContent(MultipartFile file) {
        log.info("开始解析简历文件: {}", file.getOriginalFilename());
        return documentParseService.parseContent(file);
    }

    /**
     * 从存储下载文件并解析内容
     *
     * @param storageKey       存储键
     * @param originalFilename 原始文件名
     * @return 提取的文本内容
     */
    public String downloadAndParseResume(String storageKey, String originalFilename) {
        log.info("开始下载并解析简历文件: {}", originalFilename);
        return documentParseService.downloadAndParseContent(fileStorageService, storageKey, originalFilename);
    }
}
