package com.example.ecommerce.auth.service;

import com.example.ecommerce.auth.dto.request.RegisterDTO;
import com.example.ecommerce.user.entity.Role;
import com.example.ecommerce.user.entity.User;
import com.example.ecommerce.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;

    public void registerCustomer(RegisterDTO registerDTO) {
        if(!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new IllegalArgumentException ("Mật khẩu không khớp.");
        }
        if(registerDTO.getPassword().length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải lớn hơn 5 kí tự");
        }
        if(userRepository.existsByName(registerDTO.getUsername())) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại.");
        }
        if(userRepository.existsByEmail(registerDTO.getEmail())) {
            throw new IllegalArgumentException("Email đã tồn tại.");
        }
        User user = User.builder().name(registerDTO.getUsername())
                .email(registerDTO.getEmail())
                .password(passwordEncoder.encode(registerDTO.getPassword()))
                .role(Role.CUSTOMER).isActive(true).build();

        userRepository.save(user);
        return;
    }
}
