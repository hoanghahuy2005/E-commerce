package com.example.ecommerce.category.entity;

import com.example.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "categories")
public class Category extends BaseEntity {
    @Column(nullable = false, length = 150)
    String name;

    @Column(nullable = false, unique = true, length = 191)
    String slug;

    @Column(name = "image_url", length = 500)
    String imageUrl;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    Category parent;
}
