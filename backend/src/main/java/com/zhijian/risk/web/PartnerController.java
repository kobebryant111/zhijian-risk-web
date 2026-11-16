package com.zhijian.risk.web;

import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.service.PartnerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 机构名单。 */
@RestController
@RequestMapping("/api/partners")
public class PartnerController {
    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @GetMapping
    public ApiResult<List<Dtos.PartnerView>> list() {
        return ApiResult.ok(partnerService.list(AuthSupport.requireOrgId()));
    }

    @PostMapping
    public ApiResult<Dtos.PartnerView> create(@Valid @RequestBody Dtos.PartnerReq req) {
        return ApiResult.ok(partnerService.create(AuthSupport.requireOrgId(), req));
    }

    @PutMapping("/{id}")
    public ApiResult<Dtos.PartnerView> update(@PathVariable Long id, @Valid @RequestBody Dtos.PartnerReq req) {
        return ApiResult.ok(partnerService.update(AuthSupport.requireOrgId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        partnerService.delete(AuthSupport.requireOrgId(), id);
        return ApiResult.ok(null);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        AuthSupport.requireOrgId();
        return ExcelDownload.attachment(partnerService.template(), "机构名单导入模板.xlsx");
    }

    @PostMapping("/import")
    public ApiResult<Dtos.ImportOutcome> importExcel(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(partnerService.importExcel(AuthSupport.requireOrgId(), ExcelDownload.bytes(file)));
    }
}
