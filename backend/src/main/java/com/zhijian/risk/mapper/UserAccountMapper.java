package com.zhijian.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import com.zhijian.risk.domain.UserAccount;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {

    default UserAccount save(UserAccount entity) {
        if (entity.getId() == null) {
            insert(entity);
        } else {
            updateById(entity);
        }
        return entity;
    }

    default java.util.Optional<UserAccount> findById(Long id) {
        return java.util.Optional.ofNullable(selectById(id));
    }

    default long count() {
        return selectCount(Wrappers.lambdaQuery());
    }

    default java.util.List<UserAccount> findAll() {
        return selectList(Wrappers.lambdaQuery());
    }

    default boolean existsById(Long id) {
        return selectById(id) != null;
    }

    default Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username)));
    }

    default boolean existsByUsername(String username) {
        return selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username)) > 0;
    }

    default List<UserAccount> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String u, String d) {
        String q1 = u == null ? "" : u;
        String q2 = d == null ? "" : d;
        return selectList(Wrappers.<UserAccount>lambdaQuery()
                .like(UserAccount::getUsername, q1)
                .or()
                .like(UserAccount::getDisplayName, q2));
    }
}
