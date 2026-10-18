package com.erikjarquin.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.erikjarquin.compras.config.JwtUtil;
import com.erikjarquin.compras.config.SecurityConfig;
import com.erikjarquin.compras.config.security.SecurityAuthorityMapper;
import com.erikjarquin.compras.exceptions.ReportException;
import com.erikjarquin.compras.model.dto.Reports.CashReportDTO;
import com.erikjarquin.compras.model.dto.Reports.PeriodSalesDTO;
import com.erikjarquin.compras.model.dto.Reports.ProfitDTO;
import com.erikjarquin.compras.model.dto.Reports.ReportsSummaryDTO;
import com.erikjarquin.compras.model.enums.ReportGroup;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.ReportsService;

/**
 * Tests del controlador de reportes (dashboard).
 *
 * Verifica los principales endpoints y el enrutado de parámetros opcionales
 * (from/to/groupBy), además del permiso único VER_REPORTES compartido por todos.
 */
@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportsService reportsService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    @Test
    @WithMockUser(authorities = "VER_REPORTES")
    void tendencia_deVentas_devuelvePuntos() throws Exception {
        when(reportsService.getSalesTrend(isNull(), isNull(), any(ReportGroup.class)))
                .thenReturn(List.of(
                        new PeriodSalesDTO("2026-09", new BigDecimal("1500.00"), 12L),
                        new PeriodSalesDTO("2026-10", new BigDecimal("1800.00"), 15L)));

        mockMvc.perform(get("/api/local/reports/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].period").value("2026-09"));

        verify(reportsService).getSalesTrend(isNull(), isNull(), any(ReportGroup.class));
    }

    @Test
    @WithMockUser(authorities = "VER_REPORTES")
    void topProductos_respetaElLimite() throws Exception {
        // IMPORTANTE: si usas matchers (isNull), TODOS los argumentos deben ser
        // matchers. El límite va con eq(10), no con el valor crudo "10".
        when(reportsService.getTopProducts(isNull(), isNull(), eq(10))).thenReturn(List.of());

        mockMvc.perform(get("/api/local/reports/top-products").param("limit", "10"))
                .andExpect(status().isOk());

        verify(reportsService).getTopProducts(isNull(), isNull(), eq(10));
    }

    @Test
    @WithMockUser(authorities = "VER_REPORTES")
    void stockBajo_conUmbral_devuelveProductos() throws Exception {
        when(reportsService.getLowStock(5)).thenReturn(List.of());

        mockMvc.perform(get("/api/local/reports/low-stock").param("threshold", "5"))
                .andExpect(status().isOk());

        verify(reportsService).getLowStock(5);
    }

    @Test
    @WithMockUser(authorities = "VER_REPORTES")
    void resumen_devuelveTotales() throws Exception {
        ReportsSummaryDTO summary = new ReportsSummaryDTO();
        summary.setTotalSales(100L);
        summary.setTotalAmount(new BigDecimal("50000.00"));

        when(reportsService.getSummary(isNull(), isNull())).thenReturn(summary);

        mockMvc.perform(get("/api/local/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSales").value(100L));
    }

    @Test
    @WithMockUser(authorities = "OTRO_PERMISO")
    void reportes_sinPermiso_devuelve403() throws Exception {
        mockMvc.perform(get("/api/local/reports/summary"))
                .andExpect(status().isForbidden());

        verify(reportsService, never()).getSummary(any(), any());
        verify(reportsService, never()).getLowStock(anyInt());
    }

    // =========================================================================
    //  Utilidad y detalle por caja
    // =========================================================================

    @Test
    @WithMockUser(authorities = "VER_REPORTES")
    void utilidad_devuelveIngresosCostoYGanancia() throws Exception {
        ProfitDTO profit = new ProfitDTO(
                new BigDecimal("500.00"),   // ingresos
                new BigDecimal("300.00"),   // costo de lo vendido
                new BigDecimal("200.00"),   // utilidad
                new BigDecimal("40.00"),    // margen 40%
                12L, 30L, 0L);

        when(reportsService.getProfit(isNull(), isNull())).thenReturn(profit);

        mockMvc.perform(get("/api/local/reports/profit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(500.00))
                .andExpect(jsonPath("$.costOfGoodsSold").value(300.00))
                .andExpect(jsonPath("$.grossProfit").value(200.00))
                .andExpect(jsonPath("$.marginPercent").value(40.00))
                .andExpect(jsonPath("$.tickets").value(12))
                .andExpect(jsonPath("$.itemsWithoutCost").value(0));
    }

    @Test
    @WithMockUser(authorities = "VER_REPORTES")
    void utilidad_aceptaRangoDeFechas() throws Exception {
        ProfitDTO profit = new ProfitDTO(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0L, 0L, 0L);

        when(reportsService.getProfit(eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30))))
                .thenReturn(profit);

        mockMvc.perform(get("/api/local/reports/profit")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk());

        verify(reportsService).getProfit(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    }

    @Test
    @WithMockUser(authorities = "VER_REPORTES")
    void detalleDeCaja_devuelveLaCajaConSusVentas() throws Exception {
        CashReportDTO dto = new CashReportDTO();
        dto.setCashId(3L);
        dto.setNumber("CAJA-1");
        dto.setOpeningAmount(new BigDecimal("500.00"));
        dto.setCashSales(new BigDecimal("1200.00"));
        dto.setTotalSales(new BigDecimal("1200.00"));
        dto.setGrossProfit(new BigDecimal("480.00"));
        dto.setTotalTickets(9L);
        dto.setSales(List.of());

        when(reportsService.getCashReport(3L)).thenReturn(dto);

        mockMvc.perform(get("/api/local/reports/cash/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("CAJA-1"))
                .andExpect(jsonPath("$.grossProfit").value(480.00))
                .andExpect(jsonPath("$.totalTickets").value(9));
    }

    /**
     * Una caja inexistente es 404, no 400: el recurso no existe. El servicio
     * declara el código en ReportException y el handler solo lo traduce.
     */
    @Test
    @WithMockUser(authorities = "VER_REPORTES")
    void detalleDeCaja_inexistente_devuelve404() throws Exception {
        when(reportsService.getCashReport(99L))
                .thenThrow(new ReportException("No existe la caja indicada", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/local/reports/cash/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "OTRO_PERMISO")
    void utilidad_sinPermiso_devuelve403() throws Exception {
        mockMvc.perform(get("/api/local/reports/profit"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/local/reports/cash/3"))
                .andExpect(status().isForbidden());

        verify(reportsService, never()).getProfit(any(), any());
        verify(reportsService, never()).getCashReport(any());
    }
}