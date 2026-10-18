package com.mdm.model;

public record User(
        Long id,
        String name,
        String email,
        String passwordHash,
        String role
) {
}
