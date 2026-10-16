package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.AgentConversation;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {

    default AgentConversation save(AgentConversation entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<AgentConversation> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<AgentConversation> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default List<AgentConversation> findByOrgIdAndUserIdOrderByUpdatedAtDesc(Long orgId, Long userId) {
        return selectList(Wrappers.<AgentConversation>lambdaQuery()
                .eq(AgentConversation::getOrgId, orgId)
                .eq(AgentConversation::getUserId, userId)
                .orderByDesc(AgentConversation::getUpdatedAt));
    }

    default Optional<AgentConversation> findByIdAndOrgIdAndUserId(Long id, Long orgId, Long userId) {
        return Optional.ofNullable(selectOne(Wrappers.<AgentConversation>lambdaQuery()
                .eq(AgentConversation::getId, id)
                .eq(AgentConversation::getOrgId, orgId)
                .eq(AgentConversation::getUserId, userId)));
    }
}
