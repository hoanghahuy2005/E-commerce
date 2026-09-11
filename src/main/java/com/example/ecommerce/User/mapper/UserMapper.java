package com.example.ecommerce.user.mapper;

import com.example.ecommerce.user.dto.respone.UserRespone;
import com.example.ecommerce.user.entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserRespone toUserRespone(User u){
        return new UserRespone(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getCreatedAt());
    }
}
