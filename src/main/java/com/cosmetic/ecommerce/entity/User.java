package com.cosmetic.ecommerce.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @JsonIgnore
    @ToString.Exclude
    private String password; // Server-only password hash; never serialize or log.

    @JsonIgnore
    @ToString.Exclude
    @Column(name = "session_version", nullable = false)
    private long sessionVersion = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    private String role = "USER"; // "USER" හෝ "ADMIN" විදිහට දෙන්න පුළුවන්
}
