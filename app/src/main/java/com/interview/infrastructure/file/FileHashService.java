package com.interview.infrastructure.file;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FileHashService {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192; // 8KB

    /**
     * 计算文件的 SHA-256 哈希值
     *
     * @param file MultipartFile 文件
     * @return 十六进制哈希字符串
     */
    public String calculateHash(MultipartFile file) {
        try(InputStream inputStream = file.getInputStream()) {
            return calculateHash(inputStream);
        }
        catch (IOException e) {
            log.error("Error reading file input stream", e);
            throw new RuntimeException("Error reading file input stream", e);
        }
    }

    /**
     * 流式计算文件的 SHA-256 哈希值（适用于大文件）
     *
     * @param inputStream 输入流
     * @return 十六进制哈希字符串
     */
    public String calculateHash(InputStream inputStream) {
        try{
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return bytesToHex(digest.digest());
        }
        catch (Exception e) {
            log.error("Error calculating hash from input stream", e);
            throw new RuntimeException("Error calculating hash from input stream", e);
        }
    }

    /**
     * 计算字节数组的 SHA-256 哈希值
     *
     * @param data 字节数组
     * @return 十六进制哈希字符串
     */
    public String calculateHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (Exception e) {
            log.error("Error calculating hash", e);
            throw new RuntimeException("Error calculating hash", e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}