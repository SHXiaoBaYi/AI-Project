package com.base.admin.util;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 对 GEO/内容审核智能体要求 下扫描版 PDF 做全量 OCR。
 * <pre>
 * mvn -s D:/AI-XBY/dev-tools/maven-settings.xml -DskipTests -q exec:java `
 *   -Dexec.classpathScope=test `
 *   -Dexec.mainClass=com.base.admin.util.OcrGeoReviewPdfMain
 * </pre>
 * 依赖：D:/AI-XBY/dev-tools/tessdata-runtime/{chi_sim,eng}.traineddata
 */
public class OcrGeoReviewPdfMain {

    private static final Path SRC = Path.of("D:/AI-XBY/AI-Project/GEO/内容审核智能体要求");
    private static final Path OUT = SRC.resolve("_extracted/ocr");
    private static final Path PAGE_DIR = Path.of("D:/AI-XBY/dev-tools/ocr-work/pages");
    private static final Path TESSDATA = Path.of("D:/AI-XBY/dev-tools/tessdata-runtime");
    private static final float DPI = 250f;

    /** 优先 OCR 的扫描/品宣 PDF（文本层为空或不可信） */
    private static final List<String> TARGETS = List.of(
            "(已瘦身)精品产品手册(竖版）20260602_扫描版_扫描版.pdf",
            "(已瘦身)精品系列介绍PPT20260602_扫描版.pdf",
            "2025品牌介绍.ppt(1).pdf",
            "小巴依对外品宣（通用文稿）.pdf"
    );

    public static void main(String[] args) throws Exception {
        if (!Files.isDirectory(TESSDATA) || !Files.exists(TESSDATA.resolve("chi_sim.traineddata"))) {
            throw new IllegalStateException("缺少 tessdata: " + TESSDATA);
        }
        Files.createDirectories(OUT);
        Files.createDirectories(PAGE_DIR);

        Tesseract tess = new Tesseract();
        tess.setDatapath(TESSDATA.toAbsolutePath().toString());
        tess.setLanguage("chi_sim+eng");
        // 3=全自动分页（不依赖 osd）；扫描页用 6=单块文本也可
        tess.setPageSegMode(3);
        tess.setOcrEngineMode(1);

        List<String> files = args.length > 0 ? List.of(args) : TARGETS;
        for (String name : files) {
            Path pdf = SRC.resolve(name);
            if (!Files.exists(pdf)) {
                System.err.println("missing: " + pdf);
                continue;
            }
            ocrOne(pdf, tess);
        }
        System.out.println("ALL DONE -> " + OUT);
    }

    private static void ocrOne(Path pdf, Tesseract tess) throws Exception {
        String safe = pdf.getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        Path outTxt = OUT.resolve(safe + ".ocr.txt");
        Path pageSub = PAGE_DIR.resolve(safe);
        Files.createDirectories(pageSub);

        System.out.println("OCR start: " + pdf.getFileName());
        long t0 = System.currentTimeMillis();
        StringBuilder all = new StringBuilder();
        all.append("# SOURCE: ").append(pdf.getFileName()).append("\n");
        all.append("# OCR: tesseract chi_sim+eng @ ").append((int) DPI).append("dpi\n\n");

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            int pages = doc.getNumberOfPages();
            System.out.println("  pages=" + pages);
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < pages; i++) {
                int pageNo = i + 1;
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
                Path png = pageSub.resolve(String.format(Locale.ROOT, "p%03d.png", pageNo));
                ImageIO.write(image, "png", png.toFile());
                String text;
                try {
                    text = tess.doOCR(image);
                } catch (Exception e) {
                    text = "[OCR_FAIL] " + e.getMessage();
                    System.err.println("  page " + pageNo + " fail: " + e.getMessage());
                }
                all.append("\n===== PAGE ").append(pageNo).append(" / ").append(pages).append(" =====\n");
                all.append(text == null ? "" : text.trim()).append("\n");
                if (pageNo % 5 == 0 || pageNo == pages) {
                    System.out.println("  done page " + pageNo + "/" + pages
                            + " elapsed=" + ((System.currentTimeMillis() - t0) / 1000) + "s");
                    Files.writeString(outTxt, all.toString(), StandardCharsets.UTF_8);
                }
            }
        }
        Files.writeString(outTxt, all.toString(), StandardCharsets.UTF_8);
        System.out.println("OCR ok: " + outTxt + " chars=" + all.length()
                + " cost=" + ((System.currentTimeMillis() - t0) / 1000) + "s");
    }
}
