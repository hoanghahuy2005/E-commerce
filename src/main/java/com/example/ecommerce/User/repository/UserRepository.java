package com.example.ecommerce.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.ecommerce.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByName(String username);
    boolean existsByEmail(String username);

}