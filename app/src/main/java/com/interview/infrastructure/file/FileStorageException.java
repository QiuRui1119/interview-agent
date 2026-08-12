package com.interview.infrastructure.file;

public class FileStorageException extends RuntimeException {

  public FileStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
