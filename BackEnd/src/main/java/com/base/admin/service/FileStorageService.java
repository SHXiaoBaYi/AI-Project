package com.base.admin.service;

import com.base.admin.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
}
