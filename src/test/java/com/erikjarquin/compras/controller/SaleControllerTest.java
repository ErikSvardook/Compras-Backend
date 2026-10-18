package com.erikjarquin.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.http.HttpStatus;
import com.erikjarquin.compras.config.JwtUtil;
import com.erikjarquin.compras.config.SecurityConfig;
import com.erikjarquin.compras.config.security.SecurityAuthorityMapper;
import com.erikjarquin.compras.exceptions.SaleException;
import com.erikjarquin.compras.model.dto.Sale.SaleHistoryResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleResponse;
import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.SaleService;

/**
 * Tests del controlador de ventas.
 *
 * Verifica el POST de venta (200), el historial GET (lista y detalle) y que
 * un error de negocio del servicio (SaleException) se traduzca al código HTTP
 * con el formato uniforme ({@code SALE_ERROR}).
 *
 * Sobre los códigos de error (2026-09-30): hasta entonces todo
 * {@code SaleException} se traducía a 400. Ahora la excepción trae su propio
 * {@code HttpStatus}, y estos tests fijan esa traducción:
 *   400: la venta no se puede procesar tal cual (stock insuficiente, caja
 *       cerrada) → {@code new SaleException(msg)}.
 *   404: la venta no existe.
 *   409: la venta existe pero su estado choca con la operación (anular una
 *       confirmada, anular dos veces, anular con tarjeta).
 *
 * La distinción 400/404/409 importa porque es lo que le permite al frontend
 * decidir si mostrar "reintentá", "no existe" o "esa operación no se puede
 * hacer": son tres pantallas distintas, no un único "algo falló".
 */
@WebMvcTest(SaleController.class)
@Import(SecurityConfig.class)
class SaleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SaleService saleService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    private SaleResponse ventaAprobada() {
        SaleResponse response = new SaleResponse();
        response.setSaleId(7L);
        response.setTotal(new BigDecimal("100.00"));
        response.setPaymentMethod(PaymentMethod.CASH);
        response.setCashReceived(new BigDecimal("120.00"));
        response.setChangeAmount(new BigDecimal("20.00"));
        response.setPaymentStatus(PaymentStatus.APPROVED);
        return response;
    }

    @Test
    @WithMockUser(authorities = "CREAR_VENTAS")
    void registrarVenta_devuelveVentaAprobada() throws Exception {
        when(saleService.processSale(any())).thenReturn(ventaAprobada());

        mockMvc.perform(post("/api/local/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"cashReceived\":120,\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleId").value(7L))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"));
    }

    @Test
    @WithMockUser(authorities = "CREAR_VENTAS")
    void registrarVenta_sinStock_devuelve400() throws Exception {
        // Regla de negocio (stock insuficiente) -> SaleException -> 400 SALE_ERROR.
        when(saleService.processSale(any()))
                .thenThrow(new SaleException("Stock insuficiente para el producto Café"));

        mockMvc.perform(post("/api/local/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SALE_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "VER_VENTAS")
    void listarVentas_devuelveHistorial() throws Exception {
        com.erikjarquin.compras.model.dto.Sale.SaleHistoryResponse item =
                new com.erikjarquin.compras.model.dto.Sale.SaleHistoryResponse();
        item.setId(1L);
        item.setSaleDate(LocalDateTime.now());
        item.setTotal(new BigDecimal("50.00"));
        item.setPaymentStatus(PaymentStatus.APPROVED);

        when(saleService.getSales()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/local/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    @WithMockUser(authorities = "VER_VENTAS")
    void obtenerVentaPorId_devuelveDetalle() throws Exception {
        com.erikjarquin.compras.model.dto.Sale.SaleDetailHistoryResponse detalle =
                new com.erikjarquin.compras.model.dto.Sale.SaleDetailHistoryResponse();
        detalle.setSaleId(5L);
        detalle.setTotal(new BigDecimal("80.00"));
        detalle.setItems(List.of());

        when(saleService.getSaleById(5L)).thenReturn(detalle);

        mockMvc.perform(get("/api/local/sales/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleId").value(5L));
    }

    @Test
    @WithMockUser(authorities = "OTRO")
    void registrarVenta_sinPermiso_devuelve403() throws Exception {
        mockMvc.perform(post("/api/local/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"items\":[]}"))
                .andExpect(status().isForbidden());

        verify(saleService, never()).processSale(any());
        verify(saleService, never()).getSaleById(anyLong());
    }

    // =========================================================================
    //  CICLO DE VIDA DE LA VENTA (2026-09-30): confirmar / anular
    // =========================================================================

    /**
     * Construye una venta en el estado "abierta" que usan los tests de ciclo de
     * vida. Se separa en un método para que cada test se lea como su propio
     * escenario y no haya que repetir 8 líneas de setUp en los cinco.
     */
    private SaleHistoryResponse ventaAbierta(){
        SaleHistoryResponse r = new SaleHistoryResponse();
        r.setId(5L);
        r.setSaleDate(LocalDateTime.now());
        r.setTotal(new BigDecimal("80.00"));
        r.setPaymentMethod(PaymentMethod.CASH);
        r.setPaymentStatus(PaymentStatus.APPROVED);
        return r;
    }

    @Test
    @WithMockUser(authorities = "CONFIRMAR_VENTAS")
    void confirmarVenta_devuelve200ConConfirmedTrue() throws Exception {
        SaleHistoryResponse confirmada = ventaAbierta();
        confirmada.setConfirmed(true);
        confirmada.setConfirmedAt(LocalDateTime.now());

        when(saleService.confirm(5L)).thenReturn(confirmada);

        // PATCH y no POST: la venta ya existe, solo se le cambia su estado.
        mockMvc.perform(patch("/api/local/sales/5/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.confirmed").value(true));
    }

    @Test
    @WithMockUser(authorities = "CONFIRMAR_VENTAS")
    void confirmarVenta_yaConfirmada_sigueDevolviendo200() throws Exception {
        // Idempotencia: el usuario reintentó tras un corte de red. Un 409 aquí
        // sería confuso; el estado pedido ya está, así que se responde 200.
        SaleHistoryResponse confirmada = ventaAbierta();
        confirmada.setConfirmed(true);

        when(saleService.confirm(5L)).thenReturn(confirmada);

        mockMvc.perform(patch("/api/local/sales/5/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(true));
    }

    @Test
    @WithMockUser(authorities = "CONFIRMAR_VENTAS")
    void confirmarVenta_anulada_devuelve409() throws Exception {
        when(saleService.confirm(5L))
                .thenThrow(new SaleException("No se puede confirmar una venta que ya fue anulada",
                        HttpStatus.CONFLICT));

        mockMvc.perform(patch("/api/local/sales/5/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALE_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "CANCELAR_VENTAS")
    void anularVenta_devuelve200ConCancelledTrue() throws Exception {
        SaleHistoryResponse anulada = ventaAbierta();
        anulada.setCancelled(true);
        anulada.setCancelledAt(LocalDateTime.now());

        when(saleService.cancel(5L)).thenReturn(anulada);

        mockMvc.perform(patch("/api/local/sales/5/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));
    }

    @Test
    @WithMockUser(authorities = "CANCELAR_VENTAS")
    void anularVenta_yaConfirmada_devuelve409() throws Exception {
        when(saleService.cancel(5L))
                .thenThrow(new SaleException(
                    "No se puede anular una venta ya confirmada: el pedido ya salió y el stock es real",
                    HttpStatus.CONFLICT));

        mockMvc.perform(patch("/api/local/sales/5/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("ya confirmada")));
    }

    @Test
    @WithMockUser(authorities = "CANCELAR_VENTAS")
    void anularVenta_conTarjeta_devuelve409() throws Exception {
        // El pago quedó capturado: anular la venta no devolvería el dinero, así
        // que se exige la reversa real en el módulo de pagos.
        when(saleService.cancel(5L))
                .thenThrow(new SaleException(
                    "Una venta con tarjeta no se anula desde aquí: el pago fue capturado y necesita una reversa real",
                    HttpStatus.CONFLICT));

        mockMvc.perform(patch("/api/local/sales/5/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "CANCELAR_VENTAS")
    void anularVenta_inexistente_devuelve404() throws Exception {
        when(saleService.cancel(99L))
                .thenThrow(new SaleException("Venta no encontrada con ID: 99", HttpStatus.NOT_FOUND));

        mockMvc.perform(patch("/api/local/sales/99/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SALE_ERROR"));
    }

    /**
     * Un cajero con CREAR_VENTAS NO puede confirmar ni anular.
     *
     * Es el motivo de haber creado CONFIRMAR_VENTAS y CANCELAR_VENTAS como
     * permisos separados en lugar de reutilizar CREAR_VENTAS: cobrar y cerrar la
     * venta son responsabilidades distintas.
     */
    @Test
    @WithMockUser(authorities = "CREAR_VENTAS")
    void confirmarYAnular_soloConPermisoDeVenta_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/local/sales/5/confirm"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/local/sales/5/cancel"))
                .andExpect(status().isForbidden());

        verify(saleService, never()).confirm(anyLong());
        verify(saleService, never()).cancel(anyLong());
    }

    /**
     * El historial expone el estado de vida para que el frontend sepa qué
     * botón pintar sin recalcular la máquina de estados.
     */
    @Test
    @WithMockUser(authorities = "VER_VENTAS")
    void listarVentas_incluyeElEstadoDeVidaEnCadaFila() throws Exception {
        SaleHistoryResponse abierta = ventaAbierta();
        SaleHistoryResponse confirmada = ventaAbierta();
        confirmada.setId(6L);
        confirmada.setConfirmed(true);
        confirmada.setConfirmedAt(LocalDateTime.now());

        SaleHistoryResponse anulada = ventaAbierta();
        anulada.setId(7L);
        anulada.setCancelled(true);
        anulada.setCancelledAt(LocalDateTime.now());

        when(saleService.getSales()).thenReturn(List.of(abierta, confirmada, anulada));

        mockMvc.perform(get("/api/local/sales"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].confirmed").value(false))
                .andExpect(jsonPath("$[0].cancelled").value(false))
                .andExpect(jsonPath("$[1].confirmed").value(true))
                .andExpect(jsonPath("$[2].cancelled").value(true));
    }
}