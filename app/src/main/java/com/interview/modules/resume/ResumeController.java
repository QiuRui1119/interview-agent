package com.interview.modules.resume;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import com.interview.modules.resume.service.ResumeUploadService;
/**
 * 简历控制器
 * Resume Controller for upload and analysis
 */
@Slf4j
@RestController
@AllArgsConstructor
@Tag(name = "简历管理", description = "简历上传、分析、导出和删除")
public class ResumeController {

    private final ResumeUploadService resumeUploadService;
    
    /**
     * 上传简历并获取分析结果
     *
     * @param file 简历文件（支持PDF、DOCX、DOC、TXT、MD等）
     * @return 简历分析结果，包含评分和建议
     */
        @PostMapping(value = "/api/resumes/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<Map<String, Object>> uploadAndAnalyzeResume(@RequestParam("file") MultipartFile file) {
            // 调用 ResumeUploadService 处理上传和分析逻辑
            Map<String, Object> analysisResult = resumeUploadService.uploadAndAnalyzeResume(file);
            return ResponseEntity.ok(analysisResult);
        }

}
