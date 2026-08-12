package com.interview.infrastructure.file;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.interview.common.config.StorageConfigProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

  private static final String RESUME_PREFIX = "resumes";
  private static final String KNOWLEDGE_BASE_PREFIX = "knowledgebases";
  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
  private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  private final S3Client s3Client;
  private final StorageConfigProperties storageConfig;

  @PostConstruct
  public void init() {
    ensureBucketExists();
  }

  public String uploadResume(MultipartFile file) {
    return uploadFile(file, RESUME_PREFIX);
  }

  public byte[] downloadResume(String fileKey) {
    return downloadFile(fileKey);
  }

  public void deleteResume(String fileKey) {
    deleteFile(fileKey);
  }

  public String uploadKnowledgeBase(MultipartFile file) {
    return uploadFile(file, KNOWLEDGE_BASE_PREFIX);
  }

  public byte[] downloadKnowledgeBase(String fileKey) {
    return downloadFile(fileKey);
  }

  public void deleteKnowledgeBase(String fileKey) {
    deleteFile(fileKey);
  }

  /**
   * 确保存储桶存在。
   */
  public void ensureBucketExists() {
    String bucket = storageConfig.bucket();
    HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
        .bucket(bucket)
        .build();

    try {
      s3Client.headBucket(headBucketRequest);
      log.info("Storage bucket already exists: {}", bucket);
    } catch (NoSuchBucketException ex) {
      createBucket(bucket);
    } catch (S3Exception ex) {
      if (isNotFound(ex)) {
        createBucket(bucket);
        return;
      }
      log.error("Failed to check storage bucket: {}", bucket, ex);
      throw new FileStorageException("Failed to check storage bucket: " + bucket, ex);
    }
  }

  private String uploadFile(MultipartFile file, String prefix) {
    validateUploadFile(file);

    String fileKey = buildFileKey(prefix, file.getOriginalFilename());
    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
        .bucket(storageConfig.bucket())
        .key(fileKey)
        .contentType(resolveContentType(file))
        .contentLength(file.getSize())
        .build();

    try (InputStream inputStream = file.getInputStream()) {
      s3Client.putObject(
          putObjectRequest,
          RequestBody.fromInputStream(inputStream, file.getSize())
      );
      return fileKey;
    } catch (IOException ex) {
      log.error("Failed to read upload file, prefix: {}, filename: {}", prefix,
          file.getOriginalFilename(), ex);
      throw new FileStorageException("Failed to read upload file", ex);
    } catch (SdkException ex) {
      log.error("Failed to upload file to S3, key: {}", fileKey, ex);
      throw new FileStorageException("Failed to upload file: " + fileKey, ex);
    }
  }

  public byte[] downloadFile(String fileKey) {
    String normalizedKey = normalizeFileKey(fileKey);
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(storageConfig.bucket())
        .key(normalizedKey)
        .build();

    try {
      ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(getObjectRequest);
      return response.asByteArray();
    } catch (S3Exception ex) {
      if (isNotFound(ex)) {
        throw new FileStorageNotFoundException(normalizedKey, ex);
      }
      log.error("Failed to download file from S3, key: {}", normalizedKey, ex);
      throw new FileStorageException("Failed to download file: " + normalizedKey, ex);
    } catch (SdkException ex) {
      log.error("Failed to download file from S3, key: {}", normalizedKey, ex);
      throw new FileStorageException("Failed to download file: " + normalizedKey, ex);
    }
  }

  private void deleteFile(String fileKey) {
    String normalizedKey = normalizeFileKey(fileKey);
    DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
        .bucket(storageConfig.bucket())
        .key(normalizedKey)
        .build();

    try {
      s3Client.deleteObject(deleteObjectRequest);
    } catch (SdkException ex) {
      log.error("Failed to delete file from S3, key: {}", normalizedKey, ex);
      throw new FileStorageException("Failed to delete file: " + normalizedKey, ex);
    }
  }

  private void createBucket(String bucket) {
    CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
        .bucket(bucket)
        .build();

    try {
      log.info("Storage bucket does not exist, creating: {}", bucket);
      s3Client.createBucket(createBucketRequest);
      log.info("Storage bucket created: {}", bucket);
    } catch (SdkException ex) {
      log.error("Failed to create storage bucket: {}", bucket, ex);
      throw new FileStorageException("Failed to create storage bucket: " + bucket, ex);
    }
  }

  private static void validateUploadFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("file must not be empty");
    }
  }

  private static String buildFileKey(String prefix, String originalFilename) {
    String datePath = DATE_PATH_FORMATTER.format(LocalDate.now(ZoneOffset.UTC));
    String sanitizedFilename = sanitizeFilename(originalFilename);
    return prefix + "/" + datePath + "/" + UUID.randomUUID() + "_" + sanitizedFilename;
  }

  private static String sanitizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "unnamed";
    }

    String filename = originalFilename.trim().replace('\\', '/');
    int slashIndex = filename.lastIndexOf('/');
    if (slashIndex >= 0) {
      filename = filename.substring(slashIndex + 1);
    }

    int dotIndex = filename.lastIndexOf('.');
    String baseName = filename;
    String extension = "";
    if (dotIndex > 0 && dotIndex < filename.length() - 1) {
      baseName = filename.substring(0, dotIndex);
      extension = sanitizeExtension(filename.substring(dotIndex + 1));
    }

    String sanitizedBase = sanitizeFilenamePart(baseName);
    if (sanitizedBase.isBlank()) {
      sanitizedBase = "unnamed";
    }
    if (extension.isBlank()) {
      return sanitizedBase;
    }
    return sanitizedBase + "." + extension;
  }

  private static String sanitizeFilenamePart(String value) {
    return value
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9._\\u4e00-\\u9fff\\u3400-\\u4dbf-]", "_")
        .replaceAll("_+", "_")
        .replaceAll("^[_\\.]+", "")
        .replaceAll("[_\\.]+$", "");
  }

  private static String sanitizeExtension(String value) {
    return value
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]", "");
  }

  private static String resolveContentType(MultipartFile file) {
    String contentType = file.getContentType();
    if (contentType == null || contentType.isBlank()) {
      return DEFAULT_CONTENT_TYPE;
    }
    return contentType.trim();
  }

  private static String normalizeFileKey(String fileKey) {
    if (fileKey == null || fileKey.isBlank()) {
      throw new IllegalArgumentException("fileKey must not be blank");
    }
    return fileKey.trim()
        .replace('\\', '/')
        .replaceAll("^/+", "");
  }

  private static boolean isNotFound(S3Exception ex) {
    return ex.statusCode() == 404;
  }

  public String getFileUrl(String fileKey) {
    String normalizedKey = normalizeFileKey(fileKey);
    return storageConfig.endpoint() + "/" + storageConfig.bucket() + "/" + normalizedKey;
  }

}
