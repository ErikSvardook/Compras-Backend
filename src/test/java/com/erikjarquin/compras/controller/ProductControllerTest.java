package com.erikjarquin.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.erikjarquin.compras.config.JwtUtil;
import com.erikjarquin.compras.config.SecurityConfig;
import com.erikjarquin.compras.config.security.SecurityAuthorityMapper;
import com.erikjarquin.compras.exceptions.ProductException;
import com.erikjarquin.compras.model.dto.Products.ProductDto;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.ProductService;

/**
 * Tests del controlador de productos.
 *
 * Cubre el listado (con/sin filtro de categoría), búsquedas, el alta
 * multipart (imagen opcional), el borrado y el mapeo del conflicto 409 cuando
 * el producto tiene ventas/compras asociadas.
 */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    private ProductDto producto() {
        ProductDto dto = new ProductDto();
        dto.setId(10L);
        dto.setName("Café 1kg");
        dto.setPrice(new BigDecimal("150.00"));
        dto.setStock(5);
        dto.setBarcode("7501234567890");
        return dto;
    }

    @Test
    @WithMockUser(authorities = "VER_PRODUCTOS")
    void listarProductos_sinFiltro_llamaGetAll() throws Exception {
        when(productService.getAll()).thenReturn(List.of(producto()));

        mockMvc.perform(get("/api/local/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Café 1kg"));

        verify(productService).getAll();
        verify(productService, never()).getByCategory(anyString());
    }

    @Test
    @WithMockUser(authorities = "VER_PRODUCTOS")
    void listarProductos_conCategoria_llamaGetByCategory() throws Exception {
        when(productService.getByCategory("Bebidas")).thenReturn(List.of(producto()));

        mockMvc.perform(get("/api/local/products").param("category", "Bebidas"))
                .andExpect(status().isOk());

        verify(productService).getByCategory("Bebidas");
        verify(productService, never()).getAll();
    }

    @Test
    @WithMockUser(authorities = "VER_PRODUCTOS")
    void buscarPorCodigoDeBarras_devuelveProducto() throws Exception {
        when(productService.findByBarcode("7501234567890")).thenReturn(producto());

        mockMvc.perform(get("/api/local/products/barcode/7501234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("7501234567890"));
    }

    @Test
    @WithMockUser(authorities = "VER_PRODUCTOS")
    void buscarPorNombre_devuelveCoincidencias() throws Exception {
        when(productService.search("caf")).thenReturn(List.of(producto()));

        mockMvc.perform(get("/api/local/products/search").param("q", "caf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(authorities = "CREAR_PRODUCTOS")
    void crearProducto_multipart_devuelveProducto() throws Exception {
        when(productService.save(anyString(), any(), anyInt(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(producto());

        MockMultipartFile imagen = new MockMultipartFile("image", "foto.jpg", "image/jpeg", "fake".getBytes());

        mockMvc.perform(multipart("/api/local/products")
                        .file(imagen)
                        .param("name", "Café 1kg")
                        .param("price", "150.00")
                        .param("stock", "5")
                        .param("categoryId", "1")
                        .param("sku", "CAF-001")
                        .param("barcode", "7501234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));

        verify(productService).save(eq("Café 1kg"), any(), eq(5), eq(1L), eq("CAF-001"), eq("7501234567890"), any());
    }

    @Test
    @WithMockUser(authorities = "ELIMINAR_PRODUCTOS")
    void eliminarProducto_sinHistorico_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/local/products/10"))
                .andExpect(status().isNoContent());

        verify(productService).delete(10L);
    }

    @Test
    @WithMockUser(authorities = "ELIMINAR_PRODUCTOS")
    void eliminarProducto_conHistorico_devuelve409() throws Exception {
        // Producto con ventas/compras → el servicio lanza 409 con mensaje claro.
        doThrow(new ProductException("No puedes eliminar un producto con ventas registradas",
                org.springframework.http.HttpStatus.CONFLICT))
                .when(productService).delete(10L);

        mockMvc.perform(delete("/api/local/products/10"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "VER_PRODUCTOS")
    void buscarProductoInexistente_porCodigo_devuelve404() throws Exception {
        when(productService.findByBarcode("no-existe"))
                .thenThrow(new ProductException("Producto no encontrado"));

        mockMvc.perform(get("/api/local/products/barcode/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(authorities = "VER_PRODUCTOS")
    void listarProductosDadosDeBaja_llamaGetInactive() throws Exception {
        ProductDto inactivo = producto();
        inactivo.setActive(false);
        inactivo.setHasHistory(true);
        when(productService.getInactive()).thenReturn(List.of(inactivo));

        mockMvc.perform(get("/api/local/products/inactive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].hasHistory").value(true));

        verify(productService).getInactive();
    }

    @Test
    @WithMockUser(authorities = "DESACTIVAR_PRODUCTOS")
    void darDeBajaProducto_devuelveProductoInactivo() throws Exception {
        ProductDto inactivo = producto();
        inactivo.setActive(false);
        inactivo.setHasHistory(true);
        when(productService.deactivate(10L)).thenReturn(inactivo);

        mockMvc.perform(patch("/api/local/products/10/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(productService).deactivate(10L);
    }

    @Test
    @WithMockUser(authorities = "ACTIVAR_PRODUCTOS")
    void reactivarProducto_devuelveProductoActivo() throws Exception {
        when(productService.activate(10L)).thenReturn(producto());

        mockMvc.perform(patch("/api/local/products/10/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        verify(productService).activate(10L);
    }

    @Test
    @WithMockUser(authorities = "VER_PRODUCTOS")
    void darDeBajaProducto_sinPermiso_devuelve403() throws Exception {
        //Con solo VER_PRODUCTOS no se puede dar de baja (falta DESACTIVAR_PRODUCTOS).
        mockMvc.perform(patch("/api/local/products/10/deactivate"))
                .andExpect(status().isForbidden());

        verify(productService, never()).deactivate(anyLong());
    }
}