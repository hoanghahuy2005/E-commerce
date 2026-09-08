package com.example.ecommerce.User.service;
import com.example.ecommerce.User.dto.request.UpdateProfileRequest;
import com.example.ecommerce.User.mapper.UserMapper;
import com.example.ecommerce.config.SecurityConfig;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.ecommerce.User.dto.respone.UserRespone;
import com.example.ecommerce.User.entity.User;
import com.example.ecommerce.User.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class UserService {
    final UserRepository userRepository;
    final UserMapper userMapper;
    final PasswordEncoder passwordEncoder;
    public UserRespone getCurrentUser(Long userId){
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        return userMapper.toUserRespone(u);
    }
    public UserRespone updateProfile(Long userId, UpdateProfileRequest updateProfileRequest){
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        u.setName(updateProfileRequest.getName());
        User savedUser = userRepository.save(u);

        return userMapper.toUserRespone(savedUser);
    }
    public UserRespone changePassword(Long userId, String oldPassword, String newPassword, String confirmPassword){
        if(oldPassword == null || newPassword == null || confirmPassword == null){
            throw new IllegalArgumentException("Please enter full information.");
        }
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        if(!passwordEncoder.matches(oldPassword, u.getPassword())){
            throw new IllegalArgumentException("Incorect Password");
        }
        if(!newPassword.equals(confirmPassword)){
            throw new IllegalArgumentException("Passwords do not match.");
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        User savedUser = userRepository.save(u);
        return userMapper.toUserRespone(savedUser);
    }
}
