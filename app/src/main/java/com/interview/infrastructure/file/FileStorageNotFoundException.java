package com.interview.infrastructure.file;

public class FileStorageNotFoundException extends FileStorageException {

  public FileStorageNotFoundException(String fileKey, Throwable cause) {
    super("Stored file not found: " + fileKey, cause);
  }
}
