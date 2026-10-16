package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.RiskClue;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RiskClueMapper extends BaseMapper<RiskClue> {

    default RiskClue save(RiskClue entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<RiskClue> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<RiskClue> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default List<RiskClue> findByOrgIdOrderByCreatedAtDesc(Long orgId) {
        return selectList(Wrappers.<RiskClue>lambdaQuery()
                .eq(RiskClue::getOrgId, orgId)
                .orderByDesc(RiskClue::getCreatedAt));
    }

    default Optional<RiskClue> findByIdAndOrgId(Long id, Long orgId) {
        return Optional.ofNullable(selectOne(Wrappers.<RiskClue>lambdaQuery()
                .eq(RiskClue::getId, id)
                .eq(RiskClue::getOrgId, orgId)));
    }
}
