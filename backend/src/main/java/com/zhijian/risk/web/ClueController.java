package com.zhijian.risk.web;

import com.zhijian.risk.domain.RiskClue;
import com.zhijian.risk.service.ClueService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 线索台账：按组织查询、统计与维护。 */
@RestController
@RequestMapping("/api/clues")
public class ClueController {
    private final ClueService clueService;

    public ClueController(ClueService clueService) {
        this.clueService = clueService;
    }

    @GetMapping
    public ApiResult<List<RiskClue>> list(@RequestParam(required = false) Integer level,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String q) {
        return ApiResult.ok(clueService.list(AuthSupport.requireOrgId(), level, status, q));
    }

    @GetMapping("/stats")
    public ApiResult<Map<String, Object>> stats() {
        return ApiResult.ok(clueService.stats(AuthSupport.requireOrgId()));
    }

    @GetMapping("/{id}")
    public ApiResult<RiskClue> get(@PathVariable Long id) {
        return ApiResult.ok(clueService.get(AuthSupport.requireOrgId(), id));
    }

    @PostMapping
    public ApiResult<RiskClue> create(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(clueService.create(AuthSupport.requireOrgId(), body));
    }

    @PatchMapping("/{id}")
    public ApiResult<RiskClue> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResult.ok(clueService.update(AuthSupport.requireOrgId(), id, body));
    }
}
