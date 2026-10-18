package com.erikjarquin.compras.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.erikjarquin.compras.model.entity.PermissionEntity;
import com.erikjarquin.compras.model.entity.RoleEntity;
import com.erikjarquin.compras.model.entity.UserEntity;
import com.erikjarquin.compras.model.enums.PermissionName;

/**
 * Tests del repositorio de usuarios sobre H2 en memoria (no requiere Postgres).
 *
 * Verifica las dos consultas de login: findByEmail y
 * findByEmailWithRoleAndPermissions (el JOIN FETCH que evita N+1 y carga
 * rol + permisos en una sola consulta para construir la autorizacion).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @BeforeEach
    void seed() {
        PermissionEntity verProductos = createPermission(PermissionName.VER_PRODUCTOS);
        PermissionEntity crearVentas = createPermission(PermissionName.CREAR_VENTAS);

        RoleEntity admin = new RoleEntity();
        admin.setName("ADMIN");
        admin.setPermissions(Set.of(verProductos, crearVentas));
        roleRepository.save(admin);

        UserEntity user = new UserEntity();
        user.setName("Ana");
        user.setEmail("ana@test.com");
        user.setPassword("hash");
        user.setRole(admin);
        user.setActive(true);
        userRepository.save(user);
    }

    private PermissionEntity createPermission(PermissionName name) {
        PermissionEntity permission = new PermissionEntity();
        permission.setName(name);
        return permissionRepository.save(permission);
    }

    @Test
    void findByEmail_devuelveUsuario() {
        Optional<UserEntity> result = userRepository.findByEmail("ana@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Ana");
        assertThat(result.get().getPassword()).isEqualTo("hash");
    }

    @Test
    void findByEmail_noEncuentraEmailInexistente() {
        assertThat(userRepository.findByEmail("nadie@test.com")).isNotPresent();
    }

    @Test
    void findByEmailWithRoleAndPermissions_cargaRolYPermisos() {
        Optional<UserEntity> result =
                userRepository.findByEmailWithRoleAndPermissions("ana@test.com");

        assertThat(result).isPresent();

        RoleEntity role = result.get().getRole();
        assertThat(role).isNotNull();
        assertThat(role.getName()).isEqualTo("ADMIN");

        List<PermissionName> permissions = role.getPermissions().stream()
                .map(PermissionEntity::getName)
                .toList();
        assertThat(permissions).containsExactlyInAnyOrder(
                PermissionName.VER_PRODUCTOS, PermissionName.CREAR_VENTAS);
    }

    @Test
    void findByResetToken_devuelveUsuario() {
        UserEntity user = userRepository.findByEmail("ana@test.com").orElseThrow();
        user.setResetToken("token-abc");
        userRepository.save(user);

        Optional<UserEntity> result = userRepository.findByResetToken("token-abc");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("ana@test.com");
    }
}