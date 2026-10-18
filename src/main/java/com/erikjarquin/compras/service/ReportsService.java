package com.erikjarquin.compras.service;

import java.time.LocalDate;
import java.util.List;

import com.erikjarquin.compras.model.dto.Reports.CategoryPerformanceDTO;
import com.erikjarquin.compras.model.dto.Reports.LowStockDTO;
import com.erikjarquin.compras.model.dto.Reports.MarginDTO;
import com.erikjarquin.compras.model.dto.Reports.CashBoxReportDTO;
import com.erikjarquin.compras.model.dto.Reports.CashReportDTO;
import com.erikjarquin.compras.model.dto.Reports.PaymentMethodDTO;
import com.erikjarquin.compras.model.dto.Reports.PeriodSalesDTO;
import com.erikjarquin.compras.model.dto.Reports.ProfitDTO;
import com.erikjarquin.compras.model.dto.Reports.ReportsSummaryDTO;
import com.erikjarquin.compras.model.dto.Reports.TopProductDTO;
import com.erikjarquin.compras.model.enums.ReportGroup;

/**
 * Contrato del módulo de REPORTES: agregados SQL de ventas e inventario que
 * consume el dashboard del frontend (que solo grafica). Ver
 * {@code service/impl/ReportsImpl}.
 */
public interface ReportsService {
    List<PeriodSalesDTO> getSalesTrend(LocalDate from, LocalDate to, ReportGroup groupBy);

    List<TopProductDTO> getTopProducts(LocalDate from, LocalDate to, int limit);

    List<PaymentMethodDTO> getPaymentMethodDistribution(LocalDate from, LocalDate to);

    List<CategoryPerformanceDTO> getCategoryPerformance(LocalDate from, LocalDate to);

    List<LowStockDTO> getLowStock(int threshold);

    //Márgenes por producto (costo real de compras vs precio de venta)
    List<MarginDTO> getMargins();

    ReportsSummaryDTO getSummary(LocalDate from, LocalDate to);

    //V3: utilidad del periodo (ingresos menos costo de lo vendido)
    ProfitDTO getProfit(LocalDate from, LocalDate to);

    //V3: detalle de una caja (sus ventas y su utilidad) por id
    CashReportDTO getCashReport(Long cashId);

    /**
     * Reporte de una CAJA FISICA con todas sus sesiones.
     *
     * @param boxId  id de {@code cash_boxes}
     * @param from   fecha inicial (inclusive); null = sin limite
     * @param to     fecha final (inclusive); null = hoy
     * @param userId solo los turnos con ventas de este usuario; null = todos
     */
    CashBoxReportDTO getCashBoxReport(Long boxId, LocalDate from, LocalDate to, Long userId);
}