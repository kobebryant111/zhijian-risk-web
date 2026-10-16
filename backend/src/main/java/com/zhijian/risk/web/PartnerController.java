package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.dto.Dtos;
import com.zhijian.risk.service.PartnerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机构名单 HTTP 接口。
 */
@RestController
@RequestMapping("/api/partners")
public class PartnerController {
    private static final Logger log = LoggerFactory.getLogger(PartnerController.class);

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    /**
     * 列表查询。
     */
    @GetMapping
    public List<Dtos.PartnerView> list() {
        log.debug("list start");

        return partnerService.list(AuthSupport.requireOrgId());
    }

    /**
     * 创建资源。
     * @param req req
     */
    @PostMapping
    public Dtos.PartnerView create(@Valid @RequestBody Dtos.PartnerReq req) {
        log.info("create start");

        return partnerService.create(AuthSupport.requireOrgId(), req);
    }

    /**
     * 更新资源。
     * @param id id
     * @param req req
     */
    @PutMapping("/{id}")
    public Dtos.PartnerView update(@PathVariable Long id, @Valid @RequestBody Dtos.PartnerReq req) {
        log.info("update id={}", id);

        return partnerService.update(AuthSupport.requireOrgId(), id, req);
    }

    /**
     * 删除资源。
     * @param id id
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        log.info("delete id={}", id);

        partnerService.delete(AuthSupport.requireOrgId(), id);
    }
}
