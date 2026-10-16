package com.erikjarquin.compras.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.erikjarquin.compras.model.dto.Reports.CashBoxReportDTO;
import com.erikjarquin.compras.model.dto.Reports.CashReportDTO;
import com.erikjarquin.compras.model.dto.Reports.CategoryPerformanceDTO;
import com.erikjarquin.compras.model.dto.Reports.LowStockDTO;
import com.erikjarquin.compras.model.dto.Reports.MarginDTO;
import com.erikjarquin.compras.model.dto.Reports.PaymentMethodDTO;
import com.erikjarquin.compras.model.dto.Reports.PeriodSalesDTO;
import com.erikjarquin.compras.model.dto.Reports.ProfitDTO;
import com.erikjarquin.compras.model.dto.Reports.ReportsSummaryDTO;
import com.erikjarquin.compras.model.dto.Reports.TopProductDTO;
import com.erikjarquin.compras.model.enums.ReportGroup;
import com.erikjarquin.compras.service.ReportsService;

/**
 * Reportes (dashboard analítico). Todos los endpoints exigen el permiso
 * VER_REPORTES (la exportación PDF/Excel se hace del lado del cliente).
 *
 * Todas las consultas aceptan {@code from}/{@code to} (ISO yyyy-MM-dd,
 * opcionales) y agregan en SQL.
 */
@RestController
@RequestMapping("/api/local/reports")
public class ReportController {
    private final ReportsService reportsService;

    public ReportController(ReportsService reportsService){
        this.reportsService = reportsService;
    }

    //Tendencia de ventas por día/mes/año
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/trend")
    public List<PeriodSalesDTO> getSalesTrend(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "MONTH") ReportGroup groupBy){
        return reportsService.getSalesTrend(from, to, groupBy);
    }

    //Productos más vendidos
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/top-products")
    public List<TopProductDTO> getTopProducts(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "5") int limit){
        return reportsService.getTopProducts(from, to, limit);
    }

    //Distribución por método de pago
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/payment-methods")
    public List<PaymentMethodDTO> getPaymentMethodDistribution(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to){
        return reportsService.getPaymentMethodDistribution(from, to);
    }

    //Rendimiento por categoría
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/categories")
    public List<CategoryPerformanceDTO> getCategoryPerformance(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to){
        return reportsService.getCategoryPerformance(from, to);
    }

    //Stock bajo (inventario)
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/low-stock")
    public List<LowStockDTO> getLowStock(@RequestParam(defaultValue = "10") int threshold){
        return reportsService.getLowStock(threshold);
    }

    //Márgenes por producto (costo real de compras vs precio de venta),
    //ordenados de menor a mayor margen (oportunidades de renegociación)
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/margins")
    public List<MarginDTO> getMargins(){
        return reportsService.getMargins();
    }

    //Resumen del rango (tarjetas del dashboard)
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/summary")
    public ReportsSummaryDTO getSummary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to){
        return reportsService.getSummary(from, to);
    }

    /**
     * UTILIDAD del rango: ingresos − costo de lo vendido.
     *
     * Es el reporte que hace falta para saber si el negocio está ganando
     * dinero. No sirve restar el total de las compras del periodo: ese dinero no
     * se perdió, quedó en el almacén. Se resta el costo de lo que ya se
     * vendió, tomado del costo congelado en cada renglón de venta.
     */
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/profit")
    public ProfitDTO getProfit(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to){
        return reportsService.getProfit(from, to);
    }

    /**
     * Detalle de UNA caja: qué se vendió en esa caja y cuánto se ganó.
     *
     * Va por {@code cashId} y no por número porque el número es dato de
     * entrada del usuario y el id es la clave real; el frontend resuelve el
     * número -> id con {@code GET /api/local/cash/history}. Una caja inexistente
     * devuelve 404 (no un 400), porque el recurso no existe.
     */
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/cash/{cashId}")
    public CashReportDTO getCashReport(@PathVariable Long cashId){
        return reportsService.getCashReport(cashId);
    }

    /**
     * Reporte de una CAJA FÍSICA con todas sus sesiones.
     *
     * Reemplaza la pantalla de "corte de caja" por una de "historial de caja":
     * el selector trae cajas y acá se listan sus turnos, cada uno con sus ventas.
     *
     * Va por {@code boxId} (el id de {@code cash_boxes}) y no por el de un
     * turno, porque la caja es lo que el cajero reconoce.
     *
     * @param from   fecha inicial (inclusive). null = sin límite.
     * @param to     fecha final (inclusive). null = hoy.
     * @param userId filtra por usuario. null = todos.
     */
    @PreAuthorize("hasAuthority('VER_REPORTES')")
    @GetMapping("/cash-box/{boxId}")
    public CashBoxReportDTO getCashBoxReport(
            @PathVariable Long boxId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long userId){
        return reportsService.getCashBoxReport(boxId, from, to, userId);
    }
}