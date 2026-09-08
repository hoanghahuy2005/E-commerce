package com.example.ecommerce.User.entity;

import com.example.ecommerce.common.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity 
@Table(name = "users")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity{
    String name;
    String email;
    String password;
    Role role;
    String emailVerifiedAt;
}
