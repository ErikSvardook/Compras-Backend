package com.erikjarquin.compras.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.erikjarquin.compras.model.entity.RoleEntity;
import com.erikjarquin.compras.model.entity.UserEntity;
import com.erikjarquin.compras.repository.RoleRepository;
import com.erikjarquin.compras.repository.UserRepository;

/**
 * Bootstrap que crea el usuario ADMIN inicial, solo la primera vez
 * que arranca la aplicación (si el email no existe aún en la BD).
 *
 * Seguridad: las credenciales NO están hardcodeadas. Se leen de las
 * variables {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}, resueltas en:
 *  - entorno local: application-local.yaml (ignorado por git)
 *  - Railway      : variables de entorno del servicio.
 *
 * Se respeta {@code app.seed-bootstraps}: en false el admin inicial
 * NO se crea automáticamente (lo debe dar de alta alguien con acceso).
 */
@Component
@Order(2) // Se ejecuta después de RoleBootstrap (@Order(1)), que crea el rol ADMIN.
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedEnabled;

    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed-bootstraps:true}") boolean seedEnabled,
            @Value("${ADMIN_EMAIL}") String adminEmail,
            @Value("${ADMIN_PASSWORD}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    /**
     * Crea el primer usuario con rol ADMIN si no existe todavía.
     * La contraseña se guarda hasheada con BCrypt (nunca en texto plano).
     */
    @Override
    public void run(String... args) {
        // Flag externo desactivado → no sembrar el admin inicial automáticamente.
        if(!seedEnabled) return;

        boolean exists = userRepository.findByEmail(adminEmail).isPresent();

        if (!exists) {
            RoleEntity adminRole = roleRepository.findByName("ADMIN").orElseThrow(() ->
                    new RuntimeException("Rol ADMIN no encontrado"));

            UserEntity admin = new UserEntity();
            admin.setName("Usuario administrador");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(adminRole);
            admin.setActive(true);

            userRepository.save(admin);
            System.out.println("ADMIN INICIAL CREADO. Cambia la contraseña tras el primer login.");
        }
    }
}