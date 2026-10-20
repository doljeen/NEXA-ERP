package com.ssafy.migrationservice.domain.migration.controller;

import com.ssafy.migrationservice.domain.migration.dto.FileDetailResponse;
import com.ssafy.migrationservice.domain.migration.dto.FileUploadResponse;
import com.ssafy.migrationservice.domain.migration.dto.MigrationCreateRequest;
import com.ssafy.migrationservice.domain.migration.dto.MigrationCreateResponse;
import com.ssafy.migrationservice.domain.migration.dto.MigrationDetailResponse;
import com.ssafy.migrationservice.domain.migration.entity.enums.MigrationEntity;
import com.ssafy.migrationservice.domain.migration.service.MigrationService;
import com.ssafy.migrationservice.global.dto.ApiResult;
import com.ssafy.migrationservice.global.exception.BusinessException;
import com.ssafy.migrationservice.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** 이관 API. 팀 규칙에 따라 관리자 기능은 /api/v1/admin/** 아래에 둔다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/migrations")
public class MigrationAdminController {

    private final MigrationService migrationService;

    @PostMapping
    public ResponseEntity<ApiResult<MigrationCreateResponse>> create(@Valid @RequestBody MigrationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(migrationService.create(request)));
    }

    @GetMapping("/{migrationId}")
    public ResponseEntity<ApiResult<MigrationDetailResponse>> get(@PathVariable Long migrationId) {
        return ResponseEntity.ok(ApiResult.success(migrationService.get(migrationId)));
    }

    @PostMapping(value = "/{migrationId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<FileUploadResponse>> upload(@PathVariable Long migrationId,
                                                                @RequestParam MigrationEntity entity,
                                                                @RequestPart MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_READ_FAILED);
        }
        FileUploadResponse response = migrationService.upload(migrationId, entity, file.getOriginalFilename(), content);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(response));
    }

    @GetMapping("/{migrationId}/files/{fileId}")
    public ResponseEntity<ApiResult<FileDetailResponse>> getFile(@PathVariable Long migrationId, @PathVariable Long fileId) {
        return ResponseEntity.ok(ApiResult.success(migrationService.getFile(migrationId, fileId)));
    }
}
