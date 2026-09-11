package com.example.ecommerce.user.entity;

import com.example.ecommerce.common.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Builder
@Table(name = "users")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity{
    String name;
    String email;
    String password;
    Role role;
    boolean isActive;
    LocalDateTime emailVerifiedAt;
}
