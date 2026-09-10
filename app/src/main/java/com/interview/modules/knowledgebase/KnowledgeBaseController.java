package com.interview.modules.knowledgebase;

import com.interview.modules.knowledgebase.model.KnowledgeBaseUploadResponseDTO;
import com.interview.modules.knowledgebase.model.KnowledgeBaseDetailDTO;
import com.interview.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import com.interview.modules.knowledgebase.service.KnowledgeBaseManagementService;
import com.interview.modules.knowledgebase.service.KnowledgeBaseUploadService;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Tag(name = "Knowledge Base Management", description = "Knowledge base document management")
public class KnowledgeBaseController {

  private final KnowledgeBaseUploadService knowledgeBaseUploadService;
  private final KnowledgeBaseManagementService knowledgeBaseManagementService;

  @GetMapping("/api/knowledgebase/list")
  public ResponseEntity<List<KnowledgeBaseListItemDTO>> list() {
    return ResponseEntity.ok(knowledgeBaseManagementService.findAll());
  }

  @GetMapping("/api/knowledgebase/{id}")
  public ResponseEntity<KnowledgeBaseDetailDTO> findById(@PathVariable Long id) {
    return ResponseEntity.ok(knowledgeBaseManagementService.findById(id));
  }

  @PostMapping(
      value = "/api/knowledgebase/upload",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<KnowledgeBaseUploadResponseDTO> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam("name") String name,
      @RequestParam("category") String category
  ) {
    return ResponseEntity.ok(knowledgeBaseUploadService.upload(file, name, category));
  }

  @PostMapping("/api/knowledgebase/{id}/revectorize")
  public ResponseEntity<KnowledgeBaseUploadResponseDTO> revectorize(@PathVariable Long id) {
    return ResponseEntity.ok(knowledgeBaseManagementService.revectorize(id));
  }

  @DeleteMapping("/api/knowledgebase/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    knowledgeBaseManagementService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
