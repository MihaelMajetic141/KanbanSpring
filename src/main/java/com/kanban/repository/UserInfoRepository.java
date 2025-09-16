package com.kanban.repository;


import com.kanban.model.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {
    Optional<UserInfo> findByUsername(String username);
    Optional<UserInfo> findByEmail(String email);
    Optional<UserInfo> findById(Long id);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
}
