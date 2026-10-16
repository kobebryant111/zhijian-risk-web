package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.WhistleReport;

import java.util.List;
import java.util.Optional;

@Mapper
public interface WhistleReportMapper extends BaseMapper<WhistleReport> {

    default WhistleReport save(WhistleReport entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<WhistleReport> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<WhistleReport> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default List<WhistleReport> findByOrgIdOrderByCreatedAtDesc(Long orgId) {
        return selectList(Wrappers.<WhistleReport>lambdaQuery()
                .eq(WhistleReport::getOrgId, orgId)
                .orderByDesc(WhistleReport::getCreatedAt));
    }

    default Optional<WhistleReport> findByIdAndOrgId(Long id, Long orgId) {
        return Optional.ofNullable(selectOne(Wrappers.<WhistleReport>lambdaQuery()
                .eq(WhistleReport::getId, id)
                .eq(WhistleReport::getOrgId, orgId)));
    }
}
