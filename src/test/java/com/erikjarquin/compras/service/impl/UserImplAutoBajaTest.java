package com.erikjarquin.compras.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.erikjarquin.compras.exceptions.UserException;
import com.erikjarquin.compras.model.dto.User.UpdateUserRequest;
import com.erikjarquin.compras.model.entity.RoleEntity;
import com.erikjarquin.compras.model.entity.UserEntity;
import com.erikjarquin.compras.repository.RoleRepository;
import com.erikjarquin.compras.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Usuarios - auto-darbaja y contraseña")
public class UserImplAutoBajaTest {
    @Mock private UserRepository repository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserImpl service;

    @AfterEach 
    void limpiarContexto(){
        SecurityContextHolder.clearContext();
    }

    //Se loguea con el mismo constructos que usar JwtFilter
    private void autenticarComo(UserEntity u){
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, List.of())
        );
    }

    private UserEntity usuario(Long id, String email){
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setName("Erik");
        u.setEmail(email);
        u.setActive(true);
        return u;
    }

    @Nested 
    @DisplayName("No puedes darte de baja a ti mismo")
    class AutoBaja{

        @Test
        @DisplayName("409 y NO escribe nada")
        void desactivarPropio_lanza409YNoGuarda(){
            UserEntity yo = usuario(7L, "yo@correo.com");
            autenticarComo(yo);
            when(repository.findById(7L)).thenReturn(Optional.of(yo));

            UserException e = catchThrowableOfType(
                () -> service.deactivateUser(7L), UserException.class);

            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getMessage()).contains("ti mismo");

            //Lo importante: no solo lanzar, sino no haber escrito nada
            verify(repository, never()).save(any());
            verify(repository, never()).findById(any());
        }

        @Test 
        @DisplayName("Dar de baja a otro si funciona")
        void desactivarOtro_siGuarda(){
            UserEntity yo = usuario(1L, "yo@correo.com");
            UserEntity otro = usuario(7L, "otro@correo.com");
            autenticarComo(yo);
            when(repository.findById(7L)).thenReturn(Optional.of(otro));

            service.deactivateUser(7L);

            assertThat(otro.isActive()).isFalse();
            assertThat(otro.getDeactivatedAt()).isNotNull();

            verify(repository).save(otro);
        }
    }

    @Nested 
    @DisplayName("Sin sesión el chequeo no bloquea")
    class SinSesion{
        @Test 
        void sinSecurityContext_noBloquea(){
            UserEntity otro = usuario(7L, "otro@correo.com");
            when(repository.findById(7L)).thenReturn(Optional.of(otro));

            service.deactivateUser(7L);

            assertThat(otro.isActive()).isFalse();
        }
    }

    @Nested 
    @DisplayName("La contraseña no se cambia al editar")
    class PasswordEnEdicion{
        private UpdateUserRequest editConPassword(String pw){
            UpdateUserRequest r = new UpdateUserRequest();
            r.setName("Erik");
            r.setEmail("erik@correo.com");
            r.setRoleId(1L);
            r.setPassword(pw);
            return r;
        }

        @Test 
        void editarConPassword_lanza400YNoGuarda(){
            UserException e = catchThrowableOfType(
                () -> service.updateUser(7L, editConPassword("nuevaClave123")), UserException.class
            );

            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            
            verify(repository, never()).save(any());
        }

        @Test 
        void editarConPasswordVacia_siEdita(){
            UpdateUserRequest r = editConPassword(" ");
            UserEntity u = usuario(7L, "erik@correo.com");
            when(repository.findById(7L)).thenReturn(Optional.of(u));
            when(repository.findByEmail("erik@correo.com")).thenReturn(Optional.empty());
            when(roleRepository.findById(1L)).thenReturn(Optional.of(new RoleEntity()));

            service.updateUser(7L, r);

            verify(repository).save(u);
            assertThat(u.getPassword()).isNull();
        }
    }
}
