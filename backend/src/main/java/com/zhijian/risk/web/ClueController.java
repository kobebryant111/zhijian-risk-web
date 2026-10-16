package com.zhijian.risk.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhijian.risk.domain.RiskClue;
import com.zhijian.risk.service.ClueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 线索台账 HTTP 接口。
 */
@RestController
@RequestMapping("/api/clues")
public class ClueController {
    private static final Logger log = LoggerFactory.getLogger(ClueController.class);

    private final ClueService clueService;
    public ClueController(ClueService clueService) { this.clueService = clueService; }

    @GetMapping
    public List<RiskClue> list(@RequestParam(required = false) Integer level,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String q) {
        return clueService.list(AuthSupport.requireOrgId(), level, status, q);
    }

    /**
     * 线索统计。
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        log.debug("stats start");

        return clueService.stats(AuthSupport.requireOrgId());
    }

    /**
     * 按 ID 查询详情。
     * @param id id
     */
    @GetMapping("/{id}")
    public RiskClue get(@PathVariable Long id) {
        log.debug("get id={}", id);

        return clueService.get(AuthSupport.requireOrgId(), id);
    }

    /**
     * 创建资源。
     * @param body body
     */
    @PostMapping
    public RiskClue create(@RequestBody Map<String, Object> body) {
        log.info("create bodyPresent={}", body != null);

        return clueService.create(AuthSupport.requireOrgId(), body);
    }

    /**
     * 更新资源。
     * @param id id
     * @param body body
     */
    @PatchMapping("/{id}")
    public RiskClue update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        log.info("update id={}, bodyPresent={}", body != null);

        return clueService.update(AuthSupport.requireOrgId(), id, body);
    }
}
