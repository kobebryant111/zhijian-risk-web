package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.OrgMember;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrgMemberMapper extends BaseMapper<OrgMember> {

    default OrgMember save(OrgMember entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<OrgMember> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<OrgMember> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default List<OrgMember> findByUserId(Long userId) {
        return selectList(Wrappers.<OrgMember>lambdaQuery().eq(OrgMember::getUserId, userId));
    }

    default List<OrgMember> findByOrgId(Long orgId) {
        return selectList(Wrappers.<OrgMember>lambdaQuery().eq(OrgMember::getOrgId, orgId));
    }

    default Optional<OrgMember> findByOrgIdAndUserId(Long orgId, Long userId) {
        return Optional.ofNullable(selectOne(Wrappers.<OrgMember>lambdaQuery()
                .eq(OrgMember::getOrgId, orgId)
                .eq(OrgMember::getUserId, userId)));
    }

    default boolean existsByOrgIdAndUserId(Long orgId, Long userId) {
        return selectCount(Wrappers.<OrgMember>lambdaQuery()
                .eq(OrgMember::getOrgId, orgId)
                .eq(OrgMember::getUserId, userId)) > 0;
    }

    default void deleteByOrgIdAndUserId(Long orgId, Long userId) {
        delete(Wrappers.<OrgMember>lambdaQuery()
                .eq(OrgMember::getOrgId, orgId)
                .eq(OrgMember::getUserId, userId));
    }
}
