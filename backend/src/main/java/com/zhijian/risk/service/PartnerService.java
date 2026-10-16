package com.zhijian.risk.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.Partner;
import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.mapper.PartnerMapper;
import com.zhijian.risk.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
