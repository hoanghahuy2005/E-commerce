package com.example.ecommerce.User.mapper;

import com.example.ecommerce.User.dto.respone.UserRespone;
import com.example.ecommerce.User.entity.User;

public class UserMapper {
    public UserRespone toUserRespone(User u){
        return new UserRespone(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getCreatedAt());
    }
}
