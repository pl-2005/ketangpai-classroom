package com.ketangpai.controller;

import com.ketangpai.common.Result;
import com.ketangpai.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 文件上传 Controller
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> result = fileService.upload(
                file.getBytes(), file.getOriginalFilename(), file.getContentType());
        return Result.ok(result);
    }

    @GetMapping("/{fileId}/download")
    public Result<Map<String, String>> download(@PathVariable Long fileId) {
        String url = fileService.getDownloadUrl(fileId);
        return Result.ok(Map.of("url", url));
    }

    @GetMapping("/{fileId}/preview")
    public Result<Map<String, String>> preview(@PathVariable Long fileId) {
        String url = fileService.getPreviewUrl(fileId);
        return Result.ok(Map.of("url", url));
    }
}
