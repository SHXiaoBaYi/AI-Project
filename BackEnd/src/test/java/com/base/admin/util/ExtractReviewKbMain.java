package com.base.admin.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/** 一次性抽取 GEO/内容审核智能体要求 下材料文本。 */
public class ExtractReviewKbMain {

    public static void main(String[] args) throws Exception {
        Path src = Path.of("D:/AI-XBY/AI-Project/GEO/内容审核智能体要求");
        Path out = src.resolve("_extracted");
        Files.createDirectories(out);
        try (Stream<Path> stream = Files.list(src)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String name = file.getFileName().toString();
                    String lower = name.toLowerCase(Locale.ROOT);
                    String text;
                    if (lower.endsWith(".docx")) {
                        text = extractDocx(file);
                    } else if (lower.endsWith(".pptx")) {
                        text = extractPptx(file);
                    } else if (lower.endsWith(".pdf")) {
                        text = extractPdf(file);
                    } else {
                        System.out.println("skip " + name);
                        return;
                    }
                    String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                    Path target = out.resolve(safe + ".txt");
                    try (Writer w = new OutputStreamWriter(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
                        w.write("# SOURCE: " + name + "\n\n");
                        w.write(text == null ? "" : text);
                    }
                    System.out.println("ok " + name + " -> " + text.length() + " chars");
                } catch (Exception e) {
                    System.err.println("fail " + file + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    private static String extractDocx(Path file) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(file));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return normalize(extractor.getText());
        }
    }

    private static String extractPptx(Path file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(Files.newInputStream(file))) {
            int i = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                i++;
                sb.append("\n===== SLIDE ").append(i).append(" =====\n");
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String t = textShape.getText();
                        if (t != null && !t.isBlank()) {
                            sb.append(t.trim()).append('\n');
                        }
                    }
                }
            }
        }
        return normalize(sb.toString());
    }

    private static String extractPdf(Path file) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return normalize(stripper.getText(doc));
        }
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
