package com.zhijian.risk.config;

import com.zhijian.risk.domain.Organization;
import com.zhijian.risk.mapper.OrganizationMapper;
import com.zhijian.risk.service.WhistleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟看一次各组织的吹哨定时。同一天到点只发起一次，避免重复跑批。
 */
@Component
public class WhistleScheduler {
    private static final Logger log = LoggerFactory.getLogger(WhistleScheduler.class);

    private final OrganizationMapper orgs;
    private final WhistleService whistle;

    public WhistleScheduler(OrganizationMapper orgs, WhistleService whistle) {
        this.orgs = orgs;
        this.whistle = whistle;
    }

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        for (Organization org : orgs.findAll()) {
            try {
                whistle.tick(org.getId());
            } catch (Exception e) {
                log.warn("whistle tick orgId={} reason={}", org.getId(), e.getMessage());
            }
        }
    }
}
