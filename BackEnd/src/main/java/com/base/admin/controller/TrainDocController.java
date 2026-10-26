package com.base.admin.controller;

import com.base.admin.annotation.RequiresPermission;
import com.base.admin.common.PageResult;
import com.base.admin.common.Result;
import com.base.admin.domain.dto.TrainDocSaveDTO;
import com.base.admin.domain.dto.TrainQaItemQueryDTO;
import com.base.admin.domain.dto.TrainQaItemSaveDTO;
import com.base.admin.domain.vo.TrainDocFileVO;
import com.base.admin.domain.vo.TrainDocVO;
import com.base.admin.domain.vo.TrainDocVersionVO;
import com.base.admin.domain.vo.TrainQaItemVO;
import com.base.admin.service.TrainDocService;
import com.base.admin.service.TrainQaItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Tag(name = "收银培训文档", description = "培训文档与版本管理")
@RestController
@RequestMapping("/train/doc")
@RequiredArgsConstructor
public class TrainDocController {

    private final TrainDocService trainDocService;
    private final TrainQaItemService trainQaItemService;

    @Operation(summary = "文档列表")
    @GetMapping("/list")
    @RequiresPermission("train:doc:list")
    public Result<List<TrainDocVO>> list(@RequestParam(required = false) String category) {
        return Result.ok(trainDocService.list(category));
    }

    @Operation(summary = "新建/编辑文档")
    @PostMapping
    @RequiresPermission("train:doc:add")
    public Result<Long> save(@Valid @RequestBody TrainDocSaveDTO dto) {
        return Result.ok(trainDocService.saveDoc(dto));
    }

    @Operation(summary = "编辑文档")
    @PutMapping
    @RequiresPermission("train:doc:edit")
    public Result<Long> update(@Valid @RequestBody TrainDocSaveDTO dto) {
        return Result.ok(trainDocService.saveDoc(dto));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    @RequiresPermission("train:doc:delete")
    public Result<Void> delete(@PathVariable Long id) {
        trainDocService.deleteDoc(id);
        return Result.ok();
    }

    @Operation(summary = "版本列表")
    @GetMapping("/{id}/versions")
    @RequiresPermission("train:doc:list")
    public Result<List<TrainDocVersionVO>> versions(@PathVariable Long id) {
        return Result.ok(trainDocService.versions(id));
    }

    @Operation(summary = "本地上传新建文档（含首版文件）")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("train:doc:upload")
    public Result<Long> createWithFile(@RequestParam("file") MultipartFile file,
                                       @RequestParam("title") String title,
                                       @RequestParam(required = false) String description,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String versionLabel,
                                       @RequestParam(required = false) String remark) {
        return Result.ok(trainDocService.createWithFile(file, title, description, category, versionLabel, remark));
    }

    @Operation(summary = "上传新版本")
    @PostMapping(value = "/{id}/version", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("train:doc:upload")
    public Result<Long> uploadVersion(@PathVariable Long id,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) String versionLabel,
                                      @RequestParam(required = false) String remark) {
        return Result.ok(trainDocService.uploadVersion(id, file, versionLabel, remark));
    }

    @Operation(summary = "某版本下的收银文档")
    @GetMapping("/version/{versionId}/files")
    @RequiresPermission("train:doc:list")
    public Result<List<TrainDocFileVO>> files(@PathVariable Long versionId) {
        return Result.ok(trainDocService.listFiles(versionId));
    }

    @Operation(summary = "往已有版本追加一份收银文档，不升版本")
    @PostMapping(value = "/{id}/version/{versionId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("train:doc:upload")
    public Result<Long> addFile(@PathVariable Long id,
                                @PathVariable Long versionId,
                                @RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) String title) {
        return Result.ok(trainDocService.addFileToVersion(id, versionId, file, title));
    }

    @Operation(summary = "删除版本下某一份收银文档（级联软删题目与对应答疑校准）")
    @DeleteMapping("/{id}/version/{versionId}/file/{fileId}")
    @RequiresPermission("train:doc:delete")
    public Result<Void> deleteFile(@PathVariable Long id,
                                   @PathVariable Long versionId,
                                   @PathVariable Long fileId) {
        trainDocService.deleteFileFromVersion(id, versionId, fileId);
        return Result.ok();
    }

    @Operation(summary = "把某一版本下每份收银文档拆成题目")
    @PostMapping("/{id}/version/{versionId}/qa-items/rebuild")
    @RequiresPermission("train:doc:upload")
    public Result<Void> rebuildVersionQa(@PathVariable Long id, @PathVariable Long versionId) {
        trainQaItemService.rebuildVersion(id, versionId);
        return Result.ok();
    }

    @Operation(summary = "预览版本（抽取文本 HTML）")
    @GetMapping("/version/{versionId}/preview")
    @RequiresPermission("train:doc:list")
    public Result<Map<String, Object>> preview(@PathVariable Long versionId) {
        return Result.ok(trainDocService.preview(versionId));
    }

    @Operation(summary = "下载原文件（本机无文件时 302 到线上公网 uploads）")
    @GetMapping("/version/{versionId}/file")
    @RequiresPermission("train:doc:list")
    public ResponseEntity<?> download(@PathVariable Long versionId, HttpServletResponse response) {
        String fileName = trainDocService.versionFileName(versionId);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else if (lower.endsWith(".doc")) {
            type = MediaType.parseMediaType("application/msword");
        } else if (lower.endsWith(".pdf")) {
            type = MediaType.APPLICATION_PDF;
        }
        Path path = trainDocService.findVersionFile(versionId).orElse(null);
        if (path != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(type)
                    .body(new FileSystemResource(path));
        }
        // 本机磁盘无附件（共用线上库）：跳转公网标准 uploads 链接
        String publicUrl = trainDocService.versionPublicUrl(versionId);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, publicUrl)
                .build();
    }

    @Operation(summary = "题目分页，带所属文档与版本")
    @GetMapping("/qa-item/page")
    @RequiresPermission("train:doc:list")
    public Result<PageResult<TrainQaItemVO>> pageQaItems(TrainQaItemQueryDTO query) {
        return Result.ok(trainQaItemService.page(query));
    }

    @Operation(summary = "最新版拆出的问答")
    @GetMapping("/{id}/qa-items")
    @RequiresPermission("train:doc:list")
    public Result<List<TrainQaItemVO>> qaItems(@PathVariable Long id) {
        return Result.ok(trainQaItemService.listLatest(id));
    }

    @Operation(summary = "按原文件重新拆题")
    @PostMapping("/{id}/qa-items/rebuild")
    @RequiresPermission("train:doc:upload")
    public Result<Void> rebuildQaItems(@PathVariable Long id) {
        Long versionId = trainDocService.latestVersionId(id);
        if (versionId == null) {
            return Result.fail("请先上传培训文档");
        }
        trainQaItemService.rebuildVersion(id, versionId);
        return Result.ok();
    }

    @Operation(summary = "修改题干、同义问法或答文")
    @PutMapping("/qa-item/{itemId}")
    @RequiresPermission("train:doc:edit")
    public Result<Void> updateQaItem(@PathVariable Long itemId, @Valid @RequestBody TrainQaItemSaveDTO dto) {
        trainQaItemService.update(itemId, dto);
        return Result.ok();
    }
}
