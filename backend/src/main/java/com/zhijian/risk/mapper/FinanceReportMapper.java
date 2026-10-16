package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.FinanceReport;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FinanceReportMapper extends BaseMapper<FinanceReport> {

    default FinanceReport save(FinanceReport entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<FinanceReport> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<FinanceReport> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default List<FinanceReport> findByOrgIdOrderByCreatedAtDesc(Long orgId) {
        return selectList(Wrappers.<FinanceReport>lambdaQuery()
                .eq(FinanceReport::getOrgId, orgId)
                .orderByDesc(FinanceReport::getCreatedAt));
    }

    default Optional<FinanceReport> findByIdAndOrgId(Long id, Long orgId) {
        return Optional.ofNullable(selectOne(Wrappers.<FinanceReport>lambdaQuery()
                .eq(FinanceReport::getId, id)
                .eq(FinanceReport::getOrgId, orgId)));
    }
}
