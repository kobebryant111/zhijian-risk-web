package com.zhijian.risk.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Excel 模板下载和上传文件校验。只接受 .xlsx，空文件直接拒绝。 */
public final class ExcelDownload {
    private ExcelDownload() {}

    public static ResponseEntity<byte[]> attachment(byte[] body, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    public static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "请选择 Excel 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx")) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "请上传 .xlsx 文件");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "无法读取上传文件");
        }
    }
}
