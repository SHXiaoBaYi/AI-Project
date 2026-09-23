package com.base.admin.service;

import com.base.admin.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> IMAGE_EXT = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    @Value("${xby.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 对外访问根（含 /api），例如 http://121.40.119.134/shxby/api。
     * 落库仍用相对路径 /uploads/...；返回给前端时拼成公网地址。
     */
    @Value("${xby.upload-public-base:http://121.40.119.134/shxby/api}")
    private String uploadPublicBase;

    public String saveGeoImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择截图文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (!IMAGE_EXT.contains(ext)) {
            throw new BusinessException("仅支持 png/jpg/jpeg/gif/webp 截图");
        }
        try {
            Path dir = Path.of(uploadDir, "geo").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + ext;
            Path dest = dir.resolve(name);
            try (var in = file.getInputStream()) {
                Files.copy(in, dest);
            }
            return "/uploads/geo/" + name;
        } catch (IOException e) {
            throw new BusinessException("截图保存失败: " + e.getMessage());
        }
    }

    /** 把 Excel 里贴进来的截图落到上传目录，返回可访问路径；格式不对时返回 null */
    public String saveGeoImageBytes(byte[] bytes, String ext) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String dotted = ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT);
        if (!dotted.startsWith(".")) {
            dotted = "." + dotted;
        }
        if (".jpeg".equals(dotted)) {
            dotted = ".jpg";
        }
        if (!IMAGE_EXT.contains(dotted)) {
            return null;
        }
        try {
            Path dir = Path.of(uploadDir, "geo").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + dotted;
            Files.write(dir.resolve(name), bytes);
            return "/uploads/geo/" + name;
        } catch (IOException e) {
            throw new BusinessException("截图保存失败: " + e.getMessage());
        }
    }

    /** 任务完成证明等通用附件（不限图片） */
    public String saveTaskAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择附件文件");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (ext.length() > 16) {
            throw new BusinessException("不支持的文件扩展名");
        }
        try {
            Path dir = Path.of(uploadDir, "task").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + ext;
            Path dest = dir.resolve(name);
            try (var in = file.getInputStream()) {
                Files.copy(in, dest);
            }
            return "/uploads/task/" + name;
        } catch (IOException e) {
            throw new BusinessException("附件保存失败: " + e.getMessage());
        }
    }

    private static final Set<String> RESUME_EXT = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".txt", ".md", ".csv", ".rtf");

    /** 候选人简历，返回绝对路径，便于简历下载直接打开 */
    public Path saveHrResume(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择简历文件");
        }
        String original = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (!RESUME_EXT.contains(ext)) {
            throw new BusinessException("简历支持 pdf/doc/docx/xls/xlsx/txt/csv/rtf");
        }
        try {
            Path dir = Path.of(uploadDir, "hr", "resume").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + ext;
            Path dest = dir.resolve(name);
            try (var in = file.getInputStream()) {
                Files.copy(in, dest);
            }
            return dest;
        } catch (IOException e) {
            throw new BusinessException("简历保存失败: " + e.getMessage());
        }
    }

    private static final Set<String> PORTFOLIO_EXT = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".zip", ".rar", ".7z",
            ".txt", ".md", ".csv");

    /** 候选人作品集附件，返回绝对路径 */
    public Path saveHrPortfolio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择作品集文件");
        }
        String original = file.getOriginalFilename() == null ? "portfolio" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (ext.length() > 16 || !PORTFOLIO_EXT.contains(ext)) {
            throw new BusinessException("作品集支持常见办公/图片/压缩包格式");
        }
        try {
            Path dir = Path.of(uploadDir, "hr", "portfolio").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + ext;
            Path dest = dir.resolve(name);
            try (var in = file.getInputStream()) {
                Files.copy(in, dest);
            }
            return dest;
        } catch (IOException e) {
            throw new BusinessException("作品集保存失败: " + e.getMessage());
        }
    }

    public Path resolveUploadPath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }
        String stored = normalizeStoragePath(storagePath);
        if (stored == null) {
            return null;
        }
        Path path;
        if (stored.startsWith("/uploads/") || stored.startsWith("uploads/")) {
            String relative = stored.startsWith("/") ? stored.substring("/uploads/".length()) : stored.substring("uploads/".length());
            path = Path.of(uploadDir).resolve(relative).toAbsolutePath().normalize();
        } else {
            path = Path.of(stored).normalize();
        }
        Path uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        if (path.startsWith(uploadRoot) && Files.isRegularFile(path)) {
            return path;
        }
        if (Files.isRegularFile(path)) {
            return path;
        }
        return null;
    }

    /**
     * 落库用的相对路径（/uploads/...）。若已是本系统的公网/本地绝对地址，先剥成相对路径。
     */
    public String normalizeStoragePath(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String stored = raw.trim();
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            try {
                URI uri = URI.create(stored);
                String path = uri.getPath() == null ? "" : uri.getPath();
                int idx = path.indexOf("/uploads/");
                if (idx >= 0) {
                    return path.substring(idx);
                }
                if (path.startsWith("/api/uploads/")) {
                    return path.substring("/api".length());
                }
            } catch (Exception ignored) {
                return stored;
            }
            return stored;
        }
        if (stored.startsWith("/api/uploads/")) {
            return stored.substring("/api".length());
        }
        if (!stored.startsWith("/")) {
            stored = "/" + stored;
        }
        return stored;
    }

    /** 给前端/下载用的公网绝对地址 */
    public String toPublicUrl(String storedOrPublic) {
        if (!StringUtils.hasText(storedOrPublic)) {
            return storedOrPublic;
        }
        String stored = normalizeStoragePath(storedOrPublic);
        if (stored == null) {
            return storedOrPublic;
        }
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            // 本机调试地址一律改写到公网
            try {
                URI uri = URI.create(stored);
                String host = uri.getHost() == null ? "" : uri.getHost();
                if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) {
                    String path = uri.getPath() == null ? "" : uri.getPath();
                    int idx = path.indexOf("/uploads/");
                    if (idx >= 0) {
                        stored = path.substring(idx);
                    } else {
                        return stored;
                    }
                } else {
                    return stored;
                }
            } catch (Exception e) {
                return stored;
            }
        }
        String base = uploadPublicBase == null ? "" : uploadPublicBase.trim().replaceAll("/+$", "");
        if (!StringUtils.hasText(base)) {
            return stored.startsWith("/api/") ? stored : "/api" + (stored.startsWith("/") ? stored : "/" + stored);
        }
        return base + (stored.startsWith("/") ? stored : "/" + stored);
    }
}
