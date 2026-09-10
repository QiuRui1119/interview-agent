package com.interview.modules.knowledgebase.model;

import org.springframework.web.multipart.MultipartFile;

public record KnowledgeBaseUploadRequestDTO(
    MultipartFile file,
    String name,
    String category
) {}
