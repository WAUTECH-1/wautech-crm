package com.wautech.crm.identity.repository;

import com.wautech.crm.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, UUID id);
    Optional<User> findByEmail(String email);

    @Modifying
    @Query("update User u set u.passwordHash = :passwordHash, u.updatedAt = CURRENT_TIMESTAMP where u.id = :id and u.passwordHash is null")
    int provisionPasswordHashIfMissing(@Param("id") UUID id, @Param("passwordHash") String passwordHash);
}
