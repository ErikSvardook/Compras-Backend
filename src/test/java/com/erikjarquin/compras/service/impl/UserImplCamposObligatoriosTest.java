package com.erikjarquin.compras.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.erikjarquin.compras.exceptions.UserException;
import com.erikjarquin.compras.model.dto.User.CreateUserRequest;
import com.erikjarquin.compras.model.dto.User.UpdateUserRequest;
import com.erikjarquin.compras.model.entity.RoleEntity;
import com.erikjarquin.compras.model.entity.UserEntity;
import com.erikjarquin.compras.repository.RoleRepository;
import com.erikjarquin.compras.repository.UserRepository;

/**
 * Tests de campos OBLIGATORIOS en el alta y la edición de usuarios.
 *
 * El motivo de que este archivo exista es un caso que sonaba improbable y no
 * lo era: un usuario con la contraseña en blanco se guardaba.
 * {@code BCryptPasswordEncoder} hashea la cadena vacía sin quejarse, así que la
 * cuenta quedaba creada y con fila en la base. El problema aparecía después, al
 * entrar: {@code matches} devuelve {@code false} ante una cadena vacía, siempre,
 * así que esa persona quedaba bloqueada para siempre y sin explicación.
 *
 * Y el caso hermano: un nombre o correo vacíos no los frenaba el código, solo
 * el {@code NOT NULL} de la base, que respondía con un 409 diciendo "el dato ya
 * existe". El mensaje describía un problema que no era el que había.
 *
 * Todos estos tests terminan en {@code IllegalArgumentException}, que
 * {@code GlobalExceptionHandler} traduce a 400: eso es lo que el usuario ve.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Usuarios - campos obligatorios (V7)")
class UserImplCamposObligatoriosTest {

    @Mock private UserRepository repository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private UserImpl service;

    private CreateUserRequest create(String name, String email, String password){
        CreateUserRequest r = new CreateUserRequest();
        r.setName(name);
        r.setEmail(email);
        r.setPassword(password);
        r.setRoleId(1L);
        return r;
    }

    private UpdateUserRequest update(String name, String email){
        UpdateUserRequest r = new UpdateUserRequest();
        r.setName(name);
        r.setEmail(email);
        r.setRoleId(1L);
        return r;
    }

    // ======================= ALTA =======================

    @Nested
    @DisplayName("Alta (POST)")
    class Alta {

        @Test
        @DisplayName("nombre vacío: 400 y NO se guarda nada")
        void nombreVacio(){
            assertThatThrownBy(() -> service.createUser(create("", "ana@x.com", "clave1234")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nombre")
                    .hasMessageContaining("obligatorio");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("nombre con solo espacios: 400 (el caso que != null no atrapa)")
        void nombreSoloEspacios(){
            // Este es el caso que un `if (nombre == null)` deja pasar: el
            // formulario manda "   " cuando el usuario deja el campo en blanco.
            assertThatThrownBy(() -> service.createUser(create("   ", "ana@x.com", "clave1234")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");
        }

        @Test
        @DisplayName("nombre null: 400")
        void nombreNull(){
            assertThatThrownBy(() -> service.createUser(create(null, "ana@x.com", "clave1234")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");
        }

        @Test
        @DisplayName("correo vacío: 400, y no llega a la base")
        void correoVacio(){
            assertThatThrownBy(() -> service.createUser(create("Ana", "", "clave1234")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("correo")
                    .hasMessageContaining("obligatorio");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("correo null: 400")
        void correoNull(){
            assertThatThrownBy(() -> service.createUser(create("Ana", null, "clave1234")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");
        }

        @Test
        @DisplayName("contraseña vacía: 400 y NUNCA se hashea")
        void passwordVacia(){
            // El assert clave es el verify: antes se llamaba a
            // passwordEncoder.encode("") y el usuario quedaba guardado.
            assertThatThrownBy(() -> service.createUser(create("Ana", "ana@x.com", "")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contraseña")
                    .hasMessageContaining("obligatoria");

            verify(passwordEncoder, never()).encode(any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("contraseña demasiado corta: 400")
        void passwordCorta(){
            assertThatThrownBy(() -> service.createUser(create("Ana", "ana@x.com", "corta")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contraseña")
                    .hasMessageContaining("8");
        }

        @Test
        @DisplayName("los tres vacíos a la vez: 400 y no un 500 de la base")
        void todoVacio(){
            // No importa qué campo se reporte: importa que sea 400 y no el 409
            // engañoso que devolvía el NOT NULL de la base.
            assertThatThrownBy(() -> service.createUser(create("", "", "")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");
        }
    }

    // ======================= EDICIÓN =======================

    @Nested
    @DisplayName("Edición (PUT)")
    class Edicion {

        private UserEntity usuarioExistente(){
            UserEntity user = new UserEntity();
            user.setId(7L);
            user.setName("Ana");
            user.setEmail("ana@x.com");

            RoleEntity rol = new RoleEntity();
            rol.setId(1L);

            when(repository.findById(7L)).thenReturn(Optional.of(user));
            when(roleRepository.findById(1L)).thenReturn(Optional.of(rol));
            when(repository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));

            return user;
        }

        @Test
        @DisplayName("nombre vacío: 400 (antes se guardaba en crudo)")
        void nombreVacio(){
            usuarioExistente();

            // Este método copiaba request.getName() tal cual: sin trim, sin
            // límite de longitud y sin comprobar nada.
            assertThatThrownBy(() -> service.updateUser(7L, update("", "ana@x.com")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("correo vacío: 400")
        void correoVacio(){
            usuarioExistente();

            assertThatThrownBy(() -> service.updateUser(7L, update("Ana", "")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("correo con formato inválido: 400")
        void correoInvalido(){
            usuarioExistente();

            // El PUT no validaba el formato: solo lo frenaba el NOT NULL.
            assertThatThrownBy(() -> service.updateUser(7L, update("Ana", "esto-no-es-correo")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("correo");
        }

        @Test
        @DisplayName("el correo se normaliza antes de buscar duplicados")
        void normalizaAntesDeBuscarDuplicados(){
            usuarioExistente();

            // Con espacios y mayúsculas es el MISMO correo. Si la búsqueda se
            // hiciera con el texto crudo, el duplicado no se vería y el UNIQUE
            // de la base saltaría después, con el mensaje genérico de 409.
            when(repository.findByEmail("ana@x.com")).thenReturn(Optional.empty());

            service.updateUser(7L, update("Ana", "  ANA@X.COM  "));

            verify(repository).findByEmail("ana@x.com");
        }

        @Test
        @DisplayName("correo que ya pertenece a OTRO usuario: 409")
        void correoDuplicadoDeOtro(){
            usuarioExistente();

            UserEntity otro = new UserEntity();
            otro.setId(99L);
            otro.setEmail("ana@x.com");
            when(repository.findByEmail("ana@x.com")).thenReturn(Optional.of(otro));

            assertThatThrownBy(() -> service.updateUser(7L, update("Ana", "ana@x.com")))
                    .isInstanceOf(UserException.class)
                    .hasMessageContaining("ya está registrado");
        }

        @Test
        @DisplayName("guardar sin cambiar el correo NO choca consigo mismo")
        void noChocaContigoMismo(){
            usuarioExistente();

            // El duplicado con el propio usuario hay que EXCLUIRLO. Sin ese
            // filtro, editar a alguien sin tocarle el correo siempre daría
            // conflicto y sería imposible editar a un usuario.
            UserEntity elMismo = new UserEntity();
            elMismo.setId(7L);
            elMismo.setEmail("ana@x.com");
            when(repository.findByEmail("ana@x.com")).thenReturn(Optional.of(elMismo));

            assertThat(service.updateUser(7L, update("Ana", "ana@x.com"))).isNotNull();
        }
    }

    // ======================= POR QUÉ =======================

    @Nested
    @DisplayName("Por qué el chequeo va antes de hashear")
    class PorQue {

        @Test
        @DisplayName("BCrypt hashea la cadena vacía y matches siempre da false")
        void bcryptAceptaLaCadenaVacia(){
            // Esta es la razón de que `password()` se llame ANTES de encode() y
            // no después: validando el hash sería demasiado tarde, porque BCrypt
            // devuelve un hash perfectamente válido de una contraseña vacía.
            //
            // Se usa un BCryptPasswordEncoder REAL, no el mock: el comportamiento
            // que se quiere documentar es el de la librería, y un mock no lo
            // reproduce.
            PasswordEncoder real = new BCryptPasswordEncoder();

            // Hashea sin quejarse...
            assertThat(real.encode("")).isNotBlank();

            // ...pero esa contraseña nunca va a autenticar.
            String hashDeVacio = real.encode("");
            assertThat(real.matches("", hashDeVacio)).isFalse();
        }
    }
}
