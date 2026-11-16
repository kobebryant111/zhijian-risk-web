package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.Partner;
import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.excel.ExcelFiles;
import com.zhijian.risk.mapper.PartnerMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合作机构名单的组织内 CRUD。
 */
@Service
public class PartnerService {
    private static final Logger log = LoggerFactory.getLogger(PartnerService.class);

    private final PartnerMapper partnerRepo;

    public PartnerService(PartnerMapper partnerRepo) {
        this.partnerRepo = partnerRepo;
    }

    /**
     * 列表查询。
     * @param orgId orgId
     */
    @Transactional(readOnly = true)
    public List<Dtos.PartnerView> list(Long orgId) {
        log.debug("list orgId={}", orgId);

        return partnerRepo.findByOrgIdOrderByUpdatedAtDesc(orgId).stream().map(this::toView).toList();
    }

    /**
     * 创建资源。
     * @param orgId orgId
     * @param req req
     */
    @Transactional
    public Dtos.PartnerView create(Long orgId, Dtos.PartnerReq req) {
        log.info("create orgId={}", orgId);

        Partner p = new Partner();
        p.setOrgId(orgId);
        apply(p, req);
        partnerRepo.save(p);
        return toView(p);
    }

    /**
     * 更新资源。
     * @param orgId orgId
     * @param id id
     * @param req req
     */
    @Transactional
    public Dtos.PartnerView update(Long orgId, Long id, Dtos.PartnerReq req) {
        log.info("update orgId={}, id={}", orgId, id);

        Partner p = partnerRepo.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "机构不存在或不属于当前组织"));
        apply(p, req);
        partnerRepo.save(p);
        return toView(p);
    }

    /**
     * 删除资源。
     * @param orgId orgId
     * @param id id
     */
    @Transactional
    public void delete(Long orgId, Long id) {
        log.info("delete orgId={}, id={}", orgId, id);

        Partner p = partnerRepo.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "机构不存在或不属于当前组织"));
        partnerRepo.deleteById(p.getId());
    }

    public byte[] template() {
        return ExcelFiles.workbook("机构名单", List.of("全称", "简称", "类型", "状态", "统一社会信用代码"), List.of(
                "从第 2 行开始填写，不要修改表头。",
                "全称必填。同一组织内全称相同的机构会更新，不会重复新增。",
                "类型可填：助贷、融资担保、引流、支付、数据、催收、金市同业、运营辅助。",
                "状态可填：合作中、暂停、退出。留空视为合作中。"));
    }

    /**
     * 按全称更新已有机构，全称未见过则新增。单行校验失败不影响其他行。
     */
    @Transactional
    public Dtos.ImportOutcome importExcel(Long orgId, byte[] bytes) {
        List<ExcelFiles.SheetRow> rows = readRows(bytes);
        if (!ExcelFiles.hasHeader(rows, "全称")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "表头需要包含「全称」");
        }
        Map<String, Partner> byName = new LinkedHashMap<>();
        for (Partner existing : partnerRepo.findByOrgIdOrderByUpdatedAtDesc(orgId)) {
            byName.putIfAbsent(existing.getName().trim(), existing);
        }
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();
        for (ExcelFiles.SheetRow row : rows) {
            String name = ExcelFiles.cell(row.cells(), "全称", "name");
            if (name.isBlank()) {
                errors.add(rowMessage(row, "全称不能为空"));
                continue;
            }
            if (name.length() > 200) {
                errors.add(rowMessage(row, "全称不能超过 200 字"));
                continue;
            }
            String typeRaw = ExcelFiles.cell(row.cells(), "类型", "partnerType");
            String type = TYPE_LABELS.get(typeRaw);
            if (type == null) {
                errors.add(rowMessage(row, "类型无法识别"));
                continue;
            }
            String statusRaw = ExcelFiles.cell(row.cells(), "状态", "status");
            String status = statusRaw.isBlank() ? "active" : STATUS_LABELS.get(statusRaw);
            if (status == null) {
                errors.add(rowMessage(row, "状态无法识别"));
                continue;
            }
            String alias = ExcelFiles.cell(row.cells(), "简称", "alias");
            String uscc = ExcelFiles.cell(row.cells(), "统一社会信用代码", "uscc");
            if (alias.length() > 64 || uscc.length() > 64) {
                errors.add(rowMessage(row, "简称或统一社会信用代码过长"));
                continue;
            }
            Partner partner = byName.get(name);
            boolean fresh = partner == null;
            if (fresh) {
                partner = new Partner();
                partner.setOrgId(orgId);
                partner.setName(name);
            }
            partner.setAlias(alias);
            partner.setPartnerType(type);
            partner.setStatus(status);
            partner.setUscc(uscc);
            partner.setUpdatedAt(LocalDateTime.now());
            partnerRepo.save(partner);
            byName.put(name, partner);
            if (fresh) created++;
            else updated++;
        }
        log.info("import partners orgId={} created={} updated={} failed={}", orgId, created, updated, errors.size());
        return new Dtos.ImportOutcome(created, updated, cap(errors));
    }

    private static List<ExcelFiles.SheetRow> readRows(byte[] bytes) {
        try {
            List<ExcelFiles.SheetRow> rows = ExcelFiles.read(bytes);
            if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "表格没有数据行");
            return rows;
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static String rowMessage(ExcelFiles.SheetRow row, String message) {
        return "第" + row.rowNumber() + "行：" + message;
    }

    private static List<String> cap(List<String> errors) {
        if (errors.size() <= 20) return errors;
        List<String> shown = new ArrayList<>(errors.subList(0, 20));
        shown.add("其余 " + (errors.size() - 20) + " 行省略");
        return shown;
    }

    private static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("助贷", "loan"), Map.entry("loan", "loan"),
            Map.entry("融资担保", "guarantee"), Map.entry("融担", "guarantee"), Map.entry("guarantee", "guarantee"),
            Map.entry("引流", "traffic"), Map.entry("traffic", "traffic"),
            Map.entry("支付", "payment"), Map.entry("payment", "payment"),
            Map.entry("数据", "data"), Map.entry("data", "data"),
            Map.entry("催收", "collection"), Map.entry("collection", "collection"),
            Map.entry("金市同业", "interbank"), Map.entry("interbank", "interbank"),
            Map.entry("运营辅助", "ops"), Map.entry("运营", "ops"), Map.entry("ops", "ops"));

    private static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry("合作中", "active"), Map.entry("active", "active"),
            Map.entry("暂停", "paused"), Map.entry("paused", "paused"),
            Map.entry("退出", "exit"), Map.entry("exit", "exit"));

    private void apply(Partner p, Dtos.PartnerReq req) {
        p.setName(req.name().trim());
        p.setAlias(req.alias() == null ? "" : req.alias().trim());
        p.setPartnerType(blank(req.partnerType(), "loan"));
        p.setStatus(blank(req.status(), "active"));
        p.setUscc(req.uscc() == null ? "" : req.uscc().trim());
        p.setUpdatedAt(LocalDateTime.now());
    }

    private String blank(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }

    private Dtos.PartnerView toView(Partner p) {
        return new Dtos.PartnerView(p.getId(), p.getName(), p.getAlias(), p.getPartnerType(),
                p.getStatus(), p.getUscc(), p.getUpdatedAt().toString());
    }
}
