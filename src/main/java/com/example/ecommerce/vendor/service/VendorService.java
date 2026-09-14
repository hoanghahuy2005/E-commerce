package com.example.ecommerce.vendor.service;

import com.example.ecommerce.user.entity.Role;
import com.example.ecommerce.user.entity.User;
import com.example.ecommerce.user.repository.UserRepository;
import com.example.ecommerce.vendor.dto.request.VendorRequest;
import com.example.ecommerce.vendor.dto.respone.VendorRespone;
import com.example.ecommerce.vendor.entity.Vendor;
import com.example.ecommerce.vendor.mapper.VendorMapper;
import com.example.ecommerce.vendor.repository.VendorRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VendorService {
    VendorMapper vendorMapper;
    VendorRepository vendorRepository;
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;

    public List<VendorRespone> getVendors(){
        List<VendorRespone> vendorRespones = vendorRepository.findAll()
                .stream()
                .map(vendor -> vendorMapper.toVendorResponse(vendor))
                .toList();
        return  vendorRespones;
    }

    public VendorRespone getVendor(Long id){
        Vendor res = vendorRepository.findById(id).orElse(null);
        return vendorMapper.toVendorResponse(res);
    }

    @Transactional
    public VendorRespone createVendor(VendorRequest vendorRequest) {
        if(!vendorRequest.getPassword().equals(vendorRequest.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu không khớp.");
        }

        String name = vendorRequest.getName().trim();
        String shopName = vendorRequest.getShopName().trim();
        String email = vendorRequest.getEmail().trim().toLowerCase(Locale.ROOT);

        if(userRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên người dùng đã tồn tại.");
        }
        if(userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại.");
        }

        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(vendorRequest.getPassword()))
                .phoneNumber(vendorRequest.getPhone().trim())
                .address(vendorRequest.getAddress().trim())
                .role(Role.VENDOR)
                .isActive(true)
                .build();
        User savedUser = userRepository.save(user);

        Vendor vendor = Vendor.builder()
                .user(savedUser)
                .shopName(shopName)
                .description(vendorRequest.getDescription())
                .isActive(true)
                .build();
        Vendor savedVendor = vendorRepository.save(vendor);

        return vendorMapper.toVendorResponse(savedVendor);
    }
}
