package com.example.ecommerce.auth.repository;

import com.example.ecommerce.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRepository extends JpaRepository<User, Long> {
}