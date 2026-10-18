package com.erikjarquin.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import com.erikjarquin.compras.exceptions.PurchaseException;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseDTO;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.PurchaseService;

/**
 * Tests del controlador de compras.
 *
 * Verifica el CRUD (listado, detalle, alta 201, cancelación 204) y las reglas
 * de negocio que el frontend muestra como mensajes claros: items vacíos → 400,
 * compra no encontrada → 404.
 */
@WebMvcTest(PurchaseController.class)
@Import(SecurityConfig.class)
class PurchaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseService purchaseService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    private PurchaseDTO compra() {
        PurchaseDTO dto = new PurchaseDTO();
        dto.setId(5L);
        dto.setProviderName("Proveedor Alfa");
        dto.setTotal(new BigDecimal("120.00"));
        dto.setTotalItems(1);
        dto.setItems(List.of());
        return dto;
    }

    @Test
    @WithMockUser(authorities = "VER_COMPRAS")
    void listarCompras_devuelveLista() throws Exception {
        when(purchaseService.getAll()).thenReturn(List.of(compra()));

        mockMvc.perform(get("/api/local/purchases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerName").value("Proveedor Alfa"));
    }

    @Test
    @WithMockUser(authorities = "VER_COMPRAS")
    void obtenerCompraPorId_devuelveDetalle() throws Exception {
        when(purchaseService.getById(5L)).thenReturn(compra());

        mockMvc.perform(get("/api/local/purchases/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L));
    }

    @Test
    @WithMockUser(authorities = "VER_COMPRAS")
    void listarComprasPorProveedor_devuelveLista() throws Exception {
        when(purchaseService.getByProvider(3L)).thenReturn(List.of(compra()));

        mockMvc.perform(get("/api/local/purchases/provider/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(purchaseService).getByProvider(3L);
    }

    @Test
    @WithMockUser(authorities = "CREAR_COMPRAS")
    void crearCompra_devuelve201() throws Exception {
        when(purchaseService.create(any())).thenReturn(compra());

        mockMvc.perform(post("/api/local/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":3,\"items\":[{\"productId\":1,\"quantity\":2,\"unitCost\":60}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5L));
    }

    @Test
    @WithMockUser(authorities = "CREAR_COMPRAS")
    void crearCompra_sinItems_devuelve400() throws Exception {
        when(purchaseService.create(any()))
                .thenThrow(new PurchaseException("La compra debe incluir al menos un artículo", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/local/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":3,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PURCHASE_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "CANCELAR_COMPRAS")
    void cancelarCompra_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/local/purchases/5"))
                .andExpect(status().isNoContent());

        verify(purchaseService).cancel(5L);
    }

    @Test
    @WithMockUser(authorities = "CANCELAR_COMPRAS")
    void cancelarCompra_inexistente_devuelve404() throws Exception {
        // Se usa doThrow porque cancel() es un método void.
        doThrow(new PurchaseException("Compra no encontrada"))
                .when(purchaseService).cancel(99L);

        mockMvc.perform(delete("/api/local/purchases/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PURCHASE_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "VER_COMPRAS")
    void crearCompra_sinPermisoDevuelve403() throws Exception {
        mockMvc.perform(post("/api/local/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":3,\"items\":[]}"))
                .andExpect(status().isForbidden());

        verify(purchaseService, never()).create(any());
    }

    // =========================================================================
    //  CONFIRMAR: PATCH /{id}/confirm
    // =========================================================================

    @Test
    @WithMockUser(authorities = "CONFIRMAR_COMPRAS")
    void confirmarCompra_devuelve200ConElEstadoConfirmado() throws Exception {
        PurchaseDTO confirmada = compra();
        confirmada.setConfirmed(true);
        confirmada.setConfirmedAt(LocalDateTime.of(2026, 9, 30, 10, 30));

        when(purchaseService.confirm(5L)).thenReturn(confirmada);

        mockMvc.perform(patch("/api/local/purchases/5/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.confirmed").value(true));

        verify(purchaseService).confirm(5L);
    }

    @Test
    @WithMockUser(authorities = "CONFIRMAR_COMPRAS")
    void confirmarCompra_confirmadaDevuelve409() throws Exception {
        when(purchaseService.confirm(5L)).thenThrow(new PurchaseException(
                "La compra ya está confirmada", HttpStatus.CONFLICT));

        mockMvc.perform(patch("/api/local/purchases/5/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "CONFIRMAR_COMPRAS")
    void confirmarCompra_inexistente_devuelve404() throws Exception {
        when(purchaseService.confirm(99L)).thenThrow(
                new PurchaseException("Compra no encontrada", HttpStatus.NOT_FOUND));

        mockMvc.perform(patch("/api/local/purchases/99/confirm"))
                .andExpect(status().isNotFound());
    }

    /**
     * El permiso es propio y distinto de CREAR_COMPRAS y CANCELAR_COMPRAS: quien
     * registra una compra no debería poder, solo por eso, dar entrada a la
     * mercancía. Y con un 403 el servicio ni se invoca.
     */
    @Test
    @WithMockUser(authorities = "CREAR_COMPRAS")
    void confirmarCompra_sinPermisoDevuelve403() throws Exception {
        mockMvc.perform(patch("/api/local/purchases/5/confirm"))
                .andExpect(status().isForbidden());

        verify(purchaseService, never()).confirm(any());
    }
}