package com.interview.modules.resume;

import com.interview.modules.interview.model.ResumeAnalysisResponse;
import com.interview.modules.resume.service.ResumePersistenceService;
import com.interview.modules.resume.service.ResumeUploadService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Resume Management", description = "Resume upload, analysis, export, and deletion")
public class ResumeController {

  private final ResumeUploadService resumeUploadService;
  private final ResumePersistenceService resumePersistenceService;

  /**
   * 上传简历并触发分析。
   *
   * @param file 简历文件，支持 PDF、DOCX、DOC、TXT、MD 等格式
   * @return 上传结果、存储信息，以及同步分析完成后的最新分析结果
   */
  @PostMapping(value = "/api/resumes/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> uploadAndAnalyzeResume(
      @RequestParam("file") MultipartFile file
  ) {
    Map<String, Object> analysisResult = resumeUploadService.uploadAndAnalyzeResume(file);
    return ResponseEntity.ok(analysisResult);
  }

  /**
   * 查询指定简历的最新分析结果。
   *
   * @param resumeId 简历 ID
   * @return 最新一次 AI 分析结果
   */
  @GetMapping("/api/resumes/{resumeId}/analysis")
  public ResponseEntity<ResumeAnalysisResponse> getLatestAnalysisResult(
      @PathVariable Long resumeId
  ) {
    ResumeAnalysisResponse analysisResult =
        resumePersistenceService.getLatestAnalysisResultOrThrow(resumeId);
    return ResponseEntity.ok(analysisResult);
  }
}
