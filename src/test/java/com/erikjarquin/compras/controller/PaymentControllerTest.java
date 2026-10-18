package com.erikjarquin.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.erikjarquin.compras.config.JwtUtil;
import com.erikjarquin.compras.config.SecurityConfig;
import com.erikjarquin.compras.config.security.SecurityAuthorityMapper;
import com.erikjarquin.compras.exceptions.PaymentException;
import com.erikjarquin.compras.model.dto.Payment.CardPaymentResponse;
import com.erikjarquin.compras.model.enums.PaymentStatus;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.PaymentService;

/**
 * Tests del controlador de pagos con tarjeta.
 *
 * Cubre cobro, consulta de estado, reintento y REVERSA (POST /reverse/{id}),
 * que es el endpoint sin UI: aquí se documenta su
 * contrato HTTP (200 con true/false) para cuando el frontend agregue el listado
 * de pagos que permita invocarlo.
 */
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    private CardPaymentResponse pagoAprobado() {
        CardPaymentResponse response = new CardPaymentResponse();
        response.setPaymentId(1L);
        response.setSaleId(10L);
        response.setStatus(PaymentStatus.APPROVED);
        response.setAmount(new BigDecimal("150.00"));
        response.setTransactionId("TXN-10-123");
        response.setAuthorizationCode("AUTH-001");
        response.setLastFourDigits("4242");
        return response;
    }

    @Test
    @WithMockUser(authorities = "PROCESAR_PAGOS")
    void cobrarConTarjeta_devuelvePagoAprobado() throws Exception {
        when(paymentService.processCardPayment(any())).thenReturn(pagoAprobado());

        mockMvc.perform(post("/api/local/payments/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":10,\"paymentMethod\":\"CREDIT\",\"cardNumber\":\"4242424242424242\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.authorizationCode").value("AUTH-001"));
    }

    @Test
    @WithMockUser(authorities = "PROCESAR_PAGOS")
    void cobrarConTarjeta_rechazada_devuelve402() throws Exception {
        // El handler de PaymentException reconoce "rechazado" -> 402 PAYMENT_REJECTED.
        when(paymentService.processCardPayment(any()))
                .thenThrow(new PaymentException("Pago rechazado: fondos insuficientes"));

        mockMvc.perform(post("/api/local/payments/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":10,\"paymentMethod\":\"CREDIT\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"));
    }

    @Test
    @WithMockUser(authorities = "PROCESAR_PAGOS")
    void consultarEstado_devuelvePago() throws Exception {
        when(paymentService.getPaymentStatus("TXN-10-123")).thenReturn(pagoAprobado());

        mockMvc.perform(get("/api/local/payments/status/TXN-10-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-10-123"));
    }

    @Test
    @WithMockUser(authorities = "PROCESAR_PAGOS")
    void reintentarPago_devuelvePago() throws Exception {
        when(paymentService.retryPayment(1L)).thenReturn(pagoAprobado());

        mockMvc.perform(post("/api/local/payments/retry/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1L));
    }

    @Test
    @WithMockUser(authorities = "PROCESAR_PAGOS")
    void reversarPago_aprobado_devuelveTrue() throws Exception {
        when(paymentService.reversePayment(1L)).thenReturn(true);

        mockMvc.perform(post("/api/local/payments/reverse/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));

        verify(paymentService).reversePayment(1L);
    }

    @Test
    @WithMockUser(authorities = "PROCESAR_PAGOS")
    void reversarPago_noAprobado_devuelve400() throws Exception {
        when(paymentService.reversePayment(2L))
                .thenThrow(new PaymentException("Solo se pueden reversar pagos aprobados. Estado actual: REJECTED"));

        mockMvc.perform(post("/api/local/payments/reverse/2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "OTRO")
    void cobrarConTarjeta_sinPermiso_devuelve403() throws Exception {
        mockMvc.perform(post("/api/local/payments/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":10}"))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).processCardPayment(any());
        verify(paymentService, never()).reversePayment(anyLong());
        verify(paymentService, never()).getPaymentStatus(anyString());
    }
}