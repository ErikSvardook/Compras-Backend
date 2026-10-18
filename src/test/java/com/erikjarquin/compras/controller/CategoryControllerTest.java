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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.erikjarquin.compras.exceptions.CategoryException;
import com.erikjarquin.compras.model.dto.Categories.CategoryDto;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.CategoryService;

/**
 * Tests del controlador de categorías (@WebMvcTest + MockMvc).
 *
 * Verifican que cada endpoint: devuelve 200/204 con el body correcto cuando
 * el usuario tiene el permiso requerido, devuelve 403 sin el permiso adecuado y
 * mapea las excepciones de negocio al formato JSON uniforme del
 * GlobalExceptionHandler (p. ej. Categoría no encontrada → 404 CATEGORY_ERROR).
 */
@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    // Dependencias del JwtFilter real que carga @WebMvcTest (se dejan como mocks
    // para no tocar BD ni cifrar tokens reales durante el slice de web).
    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    @Test
    @WithMockUser(authorities = "VER_CATEGORIAS")
    void listarCategorias_conPermiso_devuelveLista() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of(
                new CategoryDto(1L, "Bebidas"),
                new CategoryDto(2L, "Snacks")));

        mockMvc.perform(get("/api/local/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Bebidas"));

        verify(categoryService).getAll();
    }

    @Test
    @WithMockUser(authorities = "OTRO_PERMISO")
    void listarCategorias_sinPermiso_devuelve403() throws Exception {
        mockMvc.perform(get("/api/local/categories"))
                .andExpect(status().isForbidden());

        // El servicio NO debe ejecutarse si el permiso no está.
        verify(categoryService, never()).getAll();
    }

    @Test
    void listarCategorias_sinAutenticacion_devuelve403() throws Exception {
        // Sin token JWT el filtro deja pasar sin autenticación y la cadena de
        // seguridad rechaza la ruta protegida (403 por no haber entry point de login).
        mockMvc.perform(get("/api/local/categories"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "CREAR_CATEGORIAS")
    void guardarCategoria_conPermiso_devuelveLaCreada() throws Exception {
        when(categoryService.save(any(CategoryDto.class)))
                .thenReturn(new CategoryDto(3L, "Lácteos"));

        mockMvc.perform(post("/api/local/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lácteos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3L))
                .andExpect(jsonPath("$.name").value("Lácteos"));
    }

    @Test
    @WithMockUser(authorities = "EDITAR_CATEGORIAS")
    void editarCategoria_existente_devuelve200() throws Exception {
        when(categoryService.update(anyLong(), any(CategoryDto.class)))
                .thenReturn(new CategoryDto(1L, "Bebidas Editadas"));

        mockMvc.perform(put("/api/local/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bebidas Editadas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bebidas Editadas"));
    }

    @Test
    @WithMockUser(authorities = "ELIMINAR_CATEGORIAS")
    void eliminarCategoria_existente_devuelve200() throws Exception {
        // Nota: CategoryController#delete devuelve void -> el status es 200 OK
        // (no 204 como en Product/Role/Provider, que usan ResponseEntity.noContent()).
        mockMvc.perform(delete("/api/local/categories/1"))
                .andExpect(status().isOk());

        verify(categoryService).delete(1L);
    }

    @Test
    @WithMockUser(authorities = "ELIMINAR_CATEGORIAS")
    void eliminarCategoria_inexistente_devuelve404ConFormatoError() throws Exception {
        // Excepción de negocio -> el GlobalExceptionHandler responde 404 uniforme.
        doThrow(new CategoryException("Categoría no encontrada"))
                .when(categoryService).delete(99L);

        mockMvc.perform(delete("/api/local/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CATEGORY_ERROR"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(authorities = "CREAR_CATEGORIAS")
    void guardarCategoria_nombreDuplicado_devuelve409() throws Exception {
        when(categoryService.save(any(CategoryDto.class)))
                .thenThrow(new CategoryException("Ya existe una categoría con ese nombre", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/local/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bebidas\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ERROR"));
    }
}