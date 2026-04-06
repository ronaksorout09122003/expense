package com.expense.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser extends AuditableEntity {

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(length = 30)
    private String mobile;

    @Column(nullable = false, length = 190, unique = true)
    private String email;

    @Column(nullable = false, length = 8)
    private String currencyCode = "INR";

    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 255)
    private String pinHash;
}
