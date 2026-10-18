package com.erikjarquin.compras.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.erikjarquin.compras.model.entity.PermissionEntity;
import com.erikjarquin.compras.model.entity.RoleEntity;
import com.erikjarquin.compras.model.enums.PermissionName;
import com.erikjarquin.compras.repository.PermissionRepository;
import com.erikjarquin.compras.repository.RoleRepository;

/**
 * Tests de {@link RolePermissionBootstrap}: la regla clave; el seed de permisos solo ocurre cuando los roles base NO tienen
 * ningún permiso (arranque inicial). Si ya hay asignaciones se respeta la
 * gestión manual y los roles borrados a mano NO vuelven a aparecer.
 */
@ExtendWith(MockitoExtension.class)
class RolePermissionBootstrapTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private PermissionEntity perm(PermissionName name) {
        return new PermissionEntity(null, name);
    }

    /** Catálogo pequeño pero representativo (mezcla permisos de los 3 roles). */
    private List<PermissionEntity> catalogo() {
        return List.of(
                perm(PermissionName.VER_PRODUCTOS),
                perm(PermissionName.VER_VENTAS),
                perm(PermissionName.CREAR_VENTAS),
                perm(PermissionName.VER_CAJA),
                perm(PermissionName.ABRIR_CAJA),
                perm(PermissionName.CERRAR_CAJA),
                perm(PermissionName.VER_COMPRAS),
                perm(PermissionName.PROCESAR_PAGOS)
        );
    }

    private RoleEntity rol(String nombre, PermissionName... asignados) {
        RoleEntity r = new RoleEntity();
        r.setName(nombre);
        Set<PermissionEntity> permisos = new HashSet<>();
        for (PermissionName nombrePermiso : asignados) {
            permisos.add(perm(nombrePermiso));
        }
        r.setPermissions(permisos);
        return r;
    }

    private void stubRoles(RoleEntity admin, RoleEntity cajero, RoleEntity almacenista) {
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.ofNullable(admin));
        when(roleRepository.findByName("CAJERO")).thenReturn(Optional.ofNullable(cajero));
        when(roleRepository.findByName("ALMACENISTA")).thenReturn(Optional.ofNullable(almacenista));
    }

    @Test
    void permisosYaAsignados_noVuelveASembrar() {
        // Roles que YA tienen permisos → el bootstrap NO debe tocarlos.
        RoleEntity admin = rol("ADMIN", PermissionName.VER_PRODUCTOS);
        RoleEntity cajero = rol("CAJERO", PermissionName.VER_VENTAS);
        RoleEntity almacenista = rol("ALMACENISTA", PermissionName.VER_COMPRAS);
        stubRoles(admin, cajero, almacenista);
        when(permissionRepository.findAll()).thenReturn(catalogo());

        RolePermissionBootstrap bootstrap = new RolePermissionBootstrap(roleRepository, permissionRepository, true);
        bootstrap.run();

        // Ningún roleRepository.save: no se reasigna lo que ya está configurado.
        verify(roleRepository, never()).save(any());
    }

    @Test
    void bdVacia_asignaPermisosBase_soloLaPrimeraVez() {
        RoleEntity admin = rol("ADMIN");
        RoleEntity cajero = rol("CAJERO");
        RoleEntity almacenista = rol("ALMACENISTA");
        stubRoles(admin, cajero, almacenista);
        when(permissionRepository.findAll()).thenReturn(catalogo());

        RolePermissionBootstrap bootstrap = new RolePermissionBootstrap(roleRepository, permissionRepository, true);
        bootstrap.run();

        verify(roleRepository, times(3)).save(any());
        // ADMIN recibe TODO el catálogo.
        assertThat(admin.getPermissions()).hasSize(8);
        // CAJERO solo su subconjunto (sin VER_COMPRAS ni PROCESAR_PAGOS).
        assertThat(cajero.getPermissions()).extracting(p -> p.getName().name())
                .containsExactlyInAnyOrder("VER_PRODUCTOS", "VER_VENTAS", "CREAR_VENTAS",
                        "VER_CAJA", "ABRIR_CAJA", "CERRAR_CAJA");
        // ALMACENISTA: productos + compras, sin ventas/caja/pagos.
        assertThat(almacenista.getPermissions()).extracting(p -> p.getName().name())
                .containsExactlyInAnyOrder("VER_PRODUCTOS", "VER_COMPRAS");
    }

    @Test
    void rolBorrado_noSeRecreaNiFalla() {
        // CAJERO borrado a mano → no debe reaparecer ni romper el arranque.
        RoleEntity admin = rol("ADMIN");
        RoleEntity almacenista = rol("ALMACENISTA");
        stubRoles(admin, null, almacenista);
        when(permissionRepository.findAll()).thenReturn(catalogo());

        RolePermissionBootstrap bootstrap = new RolePermissionBootstrap(roleRepository, permissionRepository, true);

        assertThatCode(bootstrap::run).doesNotThrowAnyException();
        verify(roleRepository, times(2)).save(any());
        assertThat(admin.getPermissions()).hasSize(8);
        assertThat(almacenista.getPermissions()).hasSize(2);
    }

    @Test
    void seedDesactivado_noHaceNada() {
        RolePermissionBootstrap bootstrap = new RolePermissionBootstrap(roleRepository, permissionRepository, false);
        bootstrap.run();

        verify(roleRepository, never()).save(any());
        verify(roleRepository, never()).findByName("ADMIN");
    }
}