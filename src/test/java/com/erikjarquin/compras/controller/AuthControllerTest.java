package com.erikjarquin.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.erikjarquin.compras.config.JwtUtil;
import com.erikjarquin.compras.config.SecurityConfig;
import com.erikjarquin.compras.config.security.SecurityAuthorityMapper;
import com.erikjarquin.compras.model.dto.Login.LoginResponse;
import com.erikjarquin.compras.model.entity.UserEntity;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.AuthService;

/**
 * Tests del controlador de autenticación.
 *
 * A diferencia del resto, {@code /api/local/auth/**} es PÚBLICO en SecurityConfig
 * (permitAll): login/forgot/reset funcionan SIN token. El único endpoint que
 * necesita sesión es change-password (usa el principal del contexto).
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    @Test
    void login_publico_devuelveToken() throws Exception {
        when(authService.login(any()))
                .thenReturn(new LoginResponse(true, 1L, "Erik", "erik@correo.com",
                        "ADMIN", List.of("VER_VENTAS"), "jwt-fake"));

        // Sin @WithMockUser: el endpoint es público.
        mockMvc.perform(post("/api/local/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"erik@correo.com\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-fake"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_credencialesInvalidas_devuelve400() throws Exception {
        // El servicio lanza error de negocio → GlobalExceptionHandler → 400 uniforme.
        when(authService.login(any()))
                .thenThrow(new IllegalArgumentException("Credenciales inválidas"));

        mockMvc.perform(post("/api/local/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"erik@correo.com\",\"password\":\"mala\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void forgotPassword_publico_devuelve200() throws Exception {
        mockMvc.perform(post("/api/local/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"erik@correo.com\"}"))
                .andExpect(status().isOk());

        verify(authService).forgotPassword("erik@correo.com");
    }

    @Test
    void resetPassword_publico_devuelve200() throws Exception {
        mockMvc.perform(post("/api/local/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc123\",\"newPassword\":\"nueva123\"}"))
                .andExpect(status().isOk());

        verify(authService).resetPassword("abc123", "nueva123");
    }

    @Test
    void resetPassword_conTokenInvalido_devuelve400() throws Exception {
        doThrow(new IllegalArgumentException("Token inválido o expirado"))
                .when(authService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/api/local/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"malo\",\"newPassword\":\"nueva123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void changePassword_conSesion_devuelve200() throws Exception {
        // Un UserEntity como principal (lo espera el cast de AuthController).
        UserEntity admin = new UserEntity();
        admin.setId(1L);
        admin.setName("Erik");
        admin.setEmail("erik@correo.com");

        mockMvc.perform(post("/api/local/auth/change-password")
                        .with(authentication(new UsernamePasswordAuthenticationToken(admin, null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"vieja\",\"newPassword\":\"nueva\"}"))
                .andExpect(status().isOk());

        // El email del principal debe enviarse al servicio.
        verify(authService).changePassword(anyString(), any());
    }
}