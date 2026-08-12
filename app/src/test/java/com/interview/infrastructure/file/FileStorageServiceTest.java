package com.interview.infrastructure.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.interview.common.config.StorageConfigProperties;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@DisplayName("文件存储服务")
class FileStorageServiceTest {

  private S3Client s3Client;
  private FileStorageService fileStorageService;

  @BeforeEach
  void setUp() {
    s3Client = mock(S3Client.class);
    StorageConfigProperties storageConfig = new StorageConfigProperties(
        "http://localhost:9000",
        "access-key",
        "secret-key",
        "interview-agent",
        "us-east-1"
    );
    fileStorageService = new FileStorageService(s3Client, storageConfig);
  }

  @Test
  @DisplayName("上传简历文件时使用 resumes 前缀")
  void uploadResumeWithResumePrefix() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "ZhangSan Resume.PDF",
        "application/pdf",
        "resume".getBytes(StandardCharsets.UTF_8)
    );

    String fileKey = fileStorageService.uploadResume(file);

    ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

    PutObjectRequest request = requestCaptor.getValue();
    assertThat(fileKey).matches(
        "resumes/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}_zhangsan_resume\\.pdf");
    assertThat(request.bucket()).isEqualTo("interview-agent");
    assertThat(request.key()).isEqualTo(fileKey);
    assertThat(request.contentType()).isEqualTo("application/pdf");
    assertThat(request.contentLength()).isEqualTo(file.getSize());
  }

  @Test
  @DisplayName("上传知识库文件时使用 knowledgebases 前缀")
  void uploadKnowledgeBaseWithKnowledgeBasePrefix() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "manual.md",
        null,
        "docs".getBytes(StandardCharsets.UTF_8)
    );

    String fileKey = fileStorageService.uploadKnowledgeBase(file);

    ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

    PutObjectRequest request = requestCaptor.getValue();
    assertThat(fileKey).matches(
        "knowledgebases/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}_manual\\.md");
    assertThat(request.contentType()).isEqualTo("application/octet-stream");
  }

  @Test
  @DisplayName("下载简历文件时返回字节数组")
  void downloadResumeAsBytes() {
    byte[] content = "resume".getBytes(StandardCharsets.UTF_8);
    GetObjectResponse response = GetObjectResponse.builder()
        .contentLength((long) content.length)
        .build();
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenReturn(ResponseBytes.fromByteArray(response, content));

    byte[] result = fileStorageService.downloadResume("/resumes/file.pdf");

    ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3Client).getObjectAsBytes(requestCaptor.capture());
    assertThat(result).isEqualTo(content);
    assertThat(requestCaptor.getValue().bucket()).isEqualTo("interview-agent");
    assertThat(requestCaptor.getValue().key()).isEqualTo("resumes/file.pdf");
  }

  @Test
  @DisplayName("删除知识库文件时封装 deleteObject 调用")
  void deleteKnowledgeBaseFile() {
    fileStorageService.deleteKnowledgeBase("\\knowledgebases\\manual.md");

    ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(requestCaptor.capture());
    assertThat(requestCaptor.getValue().bucket()).isEqualTo("interview-agent");
    assertThat(requestCaptor.getValue().key()).isEqualTo("knowledgebases/manual.md");
  }

  @Test
  @DisplayName("下载不存在的文件时抛出存储未找到异常")
  void throwNotFoundWhenDownloadMissingFile() {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

    assertThatThrownBy(() -> fileStorageService.downloadResume("resumes/missing.pdf"))
        .isInstanceOf(FileStorageNotFoundException.class);
  }

  @Test
  @DisplayName("存储桶已存在时不重复创建")
  void doesNotCreateBucketWhenBucketAlreadyExists() {
    when(s3Client.headBucket(any(HeadBucketRequest.class)))
        .thenReturn(HeadBucketResponse.builder().build());

    fileStorageService.ensureBucketExists();

    ArgumentCaptor<HeadBucketRequest> requestCaptor = ArgumentCaptor.forClass(HeadBucketRequest.class);
    verify(s3Client).headBucket(requestCaptor.capture());
    verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    assertThat(requestCaptor.getValue().bucket()).isEqualTo("interview-agent");
  }

  @Test
  @DisplayName("存储桶不存在时自动创建")
  void createsBucketWhenBucketDoesNotExist() {
    when(s3Client.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(NoSuchBucketException.builder().message("not found").build());

    fileStorageService.ensureBucketExists();

    ArgumentCaptor<CreateBucketRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateBucketRequest.class);
    verify(s3Client).createBucket(requestCaptor.capture());
    assertThat(requestCaptor.getValue().bucket()).isEqualTo("interview-agent");
  }
}
