package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.OrgSetting;

import java.util.List;
import java.util.Optional;

@Mapper
public interface OrgSettingMapper extends BaseMapper<OrgSetting> {

    default OrgSetting save(OrgSetting entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<OrgSetting> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<OrgSetting> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default Optional<OrgSetting> findByOrgIdAndSettingKey(Long orgId, String settingKey) {
        return Optional.ofNullable(selectOne(Wrappers.<OrgSetting>lambdaQuery()
                .eq(OrgSetting::getOrgId, orgId)
                .eq(OrgSetting::getSettingKey, settingKey)));
    }

    default List<OrgSetting> findByOrgId(Long orgId) {
        return selectList(Wrappers.<OrgSetting>lambdaQuery().eq(OrgSetting::getOrgId, orgId));
    }
}
