package com.ketangpai.service;

import com.ketangpai.model.entity.Submission;
import com.ketangpai.model.entity.SubmissionFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 文本提取服务：从 Word (.docx) / PDF / 纯文本文件中提取文本内容。
 * <p>
 * 用于 AI 批阅时拼接提交的完整文本（content + 附件文件提取结果）。
 */
@Slf4j
@Service
public class TextExtractionService {

    /** 支持文本提取的文件扩展名 */
    private static final Set<String> TEXT_EXTRACTABLE_EXTENSIONS = Set.of("txt", "docx", "pdf");

    /** 提取文本的最大字符数（防止超出 LLM 上下文窗口） */
    private static final int MAX_TEXT_LENGTH = 30_000;

    private static final String TRUNCATION_NOTICE = "\n\n...[内容已截断，仅显示前%d字符]";

    private final FileService fileService;

    public TextExtractionService(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 提取提交的全部文本内容（content + 附件文件提取结果）。
     */
    public String extractText(Submission submission, List<SubmissionFile> files) {
        StringBuilder sb = new StringBuilder();

        // 1. 提交文本内容
        if (submission.getContent() != null && !submission.getContent().isBlank()) {
            sb.append(submission.getContent());
        }

        // 2. 附件文件提取
        if (files != null && !files.isEmpty()) {
            for (int i = 0; i < files.size(); i++) {
                SubmissionFile file = files.get(i);
                String extension = FileService.extractExtension(file.getFileName()).toLowerCase();
                if (!TEXT_EXTRACTABLE_EXTENSIONS.contains(extension)) {
                    continue;
                }
                try {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append("--- 附件 ").append(i + 1).append("：")
                            .append(file.getFileName()).append(" ---\n");
                    byte[] bytes = fileService.downloadBytes(file.getFileUrl());
                    String extracted = extractByExtension(bytes, extension, file.getFileName());
                    sb.append(extracted);
                } catch (Exception e) {
                    log.warn("文件文本提取失败: fileName={}, submissionId={}", file.getFileName(), submission.getId(), e);
                    sb.append("[文件内容无法提取：").append(file.getFileName()).append("]");
                }
            }
        }

        // 3. 长度截断
        String text = sb.toString();
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH) + String.format(TRUNCATION_NOTICE, MAX_TEXT_LENGTH);
        }

        return text;
    }

    /** 根据扩展名选择提取器 */
    private String extractByExtension(byte[] bytes, String extension, String fileName) throws IOException {
        return switch (extension) {
            case "txt" -> extractFromTxt(bytes);
            case "docx" -> extractFromDocx(bytes);
            case "pdf" -> extractFromPdf(bytes);
            default -> "[" + fileName + " 不支持文本提取]";
        };
    }

    /** 从 .docx 提取文本 */
    String extractFromDocx(byte[] bytes) throws IOException {
        try (var bais = new ByteArrayInputStream(bytes);
             var doc = new XWPFDocument(bais);
             var extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    /** 从 PDF 提取文本 */
    String extractFromPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    /** 从纯文本文件提取 */
    String extractFromTxt(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
