package com.example.ecommerce.user.entity;

import com.example.ecommerce.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Builder
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity{
    String name;
    String email;
    String password;
    @Column(name = "phone_number", length = 30)
    String phoneNumber;
    @Column(length = 500)
    String address;
    @Enumerated(EnumType.STRING)
    Role role;
    boolean isActive;
    LocalDateTime emailVerifiedAt;
}
