package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.RiskRule;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RiskRuleMapper extends BaseMapper<RiskRule> {

    default RiskRule save(RiskRule entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<RiskRule> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<RiskRule> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default List<RiskRule> findByOrgIdOrderByUpdatedAtDesc(Long orgId) {
        return selectList(Wrappers.<RiskRule>lambdaQuery()
                .eq(RiskRule::getOrgId, orgId)
                .orderByDesc(RiskRule::getUpdatedAt));
    }

    default Optional<RiskRule> findByIdAndOrgId(Long id, Long orgId) {
        return Optional.ofNullable(selectOne(Wrappers.<RiskRule>lambdaQuery()
                .eq(RiskRule::getId, id)
                .eq(RiskRule::getOrgId, orgId)));
    }

    default long countByOrgId(Long orgId) {
        return selectCount(Wrappers.<RiskRule>lambdaQuery().eq(RiskRule::getOrgId, orgId));
    }
}
