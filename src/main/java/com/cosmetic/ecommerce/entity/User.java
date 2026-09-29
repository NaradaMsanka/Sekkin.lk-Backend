package com.cosmetic.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "users") // Database එකේ Table එකේ නම 'users' විදිහට හැදෙනවා
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    private String password; // මේක Save වෙන්නේ Hash වෙලා

    private String role = "USER"; // "USER" හෝ "ADMIN" විදිහට දෙන්න පුළුවන්
}