package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.AdmissionReview;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AdmissionReviewMapper extends BaseMapper<AdmissionReview> {

    default AdmissionReview save(AdmissionReview entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<AdmissionReview> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<AdmissionReview> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default List<AdmissionReview> findByOrgIdOrderByCreatedAtDesc(Long orgId) {
        return selectList(Wrappers.<AdmissionReview>lambdaQuery()
                .eq(AdmissionReview::getOrgId, orgId)
                .orderByDesc(AdmissionReview::getCreatedAt));
    }

    default Optional<AdmissionReview> findByIdAndOrgId(Long id, Long orgId) {
        return Optional.ofNullable(selectOne(Wrappers.<AdmissionReview>lambdaQuery()
                .eq(AdmissionReview::getId, id)
                .eq(AdmissionReview::getOrgId, orgId)));
    }
}
