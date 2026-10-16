package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.Partner;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PartnerMapper extends BaseMapper<Partner> {

    default Partner save(Partner entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<Partner> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<Partner> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default List<Partner> findByOrgIdOrderByUpdatedAtDesc(Long orgId) {
        return selectList(Wrappers.<Partner>lambdaQuery()
                .eq(Partner::getOrgId, orgId)
                .orderByDesc(Partner::getUpdatedAt));
    }

    default Optional<Partner> findByIdAndOrgId(Long id, Long orgId) {
        return Optional.ofNullable(selectOne(Wrappers.<Partner>lambdaQuery()
                .eq(Partner::getId, id)
                .eq(Partner::getOrgId, orgId)));
    }
}
