package com.erikjarquin.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.erikjarquin.compras.config.JwtUtil;
import com.erikjarquin.compras.config.SecurityConfig;
import com.erikjarquin.compras.config.security.SecurityAuthorityMapper;
import com.erikjarquin.compras.exceptions.RoleException;
import com.erikjarquin.compras.model.dto.Role.RoleDto;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.RoleService;

/**
 * Tests del controlador de roles.
 *
 * Destacan el borrado (204) y el conflicto 409 cuando el rol tiene usuarios
 * asignados, que es la regla de negocio documentada en RoleImpl.
 */
@WebMvcTest(RoleController.class)
@Import(SecurityConfig.class)
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    @Test
    @WithMockUser(authorities = "VER_ROLES")
    void listarRoles_devuelveLista() throws Exception {
        when(roleService.getAllRoles()).thenReturn(List.of(
                new RoleDto(1L, "ADMIN", List.of()),
                new RoleDto(2L, "CAJERO", List.of())));

        mockMvc.perform(get("/api/local/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = "VER_ROLES")
    void obtenerRolPorId_devuelveRol() throws Exception {
        when(roleService.getRoleById(1L)).thenReturn(new RoleDto(1L, "ADMIN", List.of()));

        mockMvc.perform(get("/api/local/roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ADMIN"));
    }

    @Test
    @WithMockUser(authorities = "CREAR_ROLES")
    void crearRol_devuelveRolCreado() throws Exception {
        when(roleService.createRole(any())).thenReturn(new RoleDto(5L, "AUDITOR", List.of()));

        mockMvc.perform(post("/api/local/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AUDITOR\",\"permissions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AUDITOR"));
    }

    @Test
    @WithMockUser(authorities = "EDITAR_ROLES")
    void editarRol_devuelveRolActualizado() throws Exception {
        when(roleService.updateRole(anyLong(), any())).thenReturn(new RoleDto(1L, "ADMIN2", List.of()));

        mockMvc.perform(put("/api/local/roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ADMIN2\",\"permissions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ADMIN2"));
    }

    @Test
    @WithMockUser(authorities = "ELIMINAR_ROLES")
    void eliminarRol_sinUsuarios_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/local/roles/3"))
                .andExpect(status().isNoContent());

        verify(roleService).deleteRole(3L);
    }

    @Test
    @WithMockUser(authorities = "ELIMINAR_ROLES")
    void eliminarRol_conUsuarios_devuelve409() throws Exception {
        doThrow(new RoleException("No puedes eliminar un rol con usuarios asignados", HttpStatus.CONFLICT))
                .when(roleService).deleteRole(1L);

        mockMvc.perform(delete("/api/local/roles/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "OTRO")
    void listarRoles_sinPermiso_devuelve403() throws Exception {
        mockMvc.perform(get("/api/local/roles"))
                .andExpect(status().isForbidden());

        verify(roleService, never()).getAllRoles();
    }

    @Test
    @WithMockUser(authorities = "VER_ROLES")
    void obtenerRolInexistente_devuelve404() throws Exception {
        when(roleService.getRoleById(99L)).thenThrow(new RoleException("Rol no encontrado"));

        mockMvc.perform(get("/api/local/roles/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROLE_ERROR"));
    }
}