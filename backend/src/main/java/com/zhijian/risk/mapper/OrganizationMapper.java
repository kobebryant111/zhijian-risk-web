package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.Organization;

import java.util.Optional;

@Mapper
public interface OrganizationMapper extends BaseMapper<Organization> {

    default Organization save(Organization entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<Organization> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<Organization> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default Optional<Organization> findByCode(String code) {
        return Optional.ofNullable(selectOne(Wrappers.<Organization>lambdaQuery()
                .eq(Organization::getCode, code)));
    }

    default boolean existsByCode(String code) {
        return selectCount(Wrappers.<Organization>lambdaQuery()
                .eq(Organization::getCode, code)) > 0;
    }
}
