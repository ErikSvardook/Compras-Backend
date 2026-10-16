package com.erikjarquin.compras.model.dto.Reports;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.erikjarquin.compras.model.dto.Sale.SaleDetailHistoryResponse;

/**
 * Detalle de una caja concreta (V3, {@code GET /api/local/reports/cash/{cashId}}).
 *
 * Responde "qué se vendió en ESTA caja". Se apoya en el número que escribe
 * el vendedor al abrir ({@code cash_registers.number}), que es único y por eso
 * identifica un corte sin ambigüedad.
 *
 * Trae los mismos datos congelados que el historial de ventas: al abrir el
 * corte la caja ya no cambia, aunque después se reconstruya el histórico.
 * No incluye ventas anuladas.
 */
public class CashReportDTO {
    private Long cashId;

    /** Número de la caja (V3). Es lo que el usuario elige en el filtro. */
    private String number;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    private BigDecimal openingAmount;
    private BigDecimal closingAmount;
    private BigDecimal difference;
    private BigDecimal expectedAmount;

    private BigDecimal totalSales;
    private BigDecimal cashSales;
    private BigDecimal debitSales;
    private BigDecimal creditSales;

    /** Tickets válidos de la caja (sin anuladas). */
    private long totalTickets;

    /** Utilidad de esta caja: ingresos − costo de lo vendido. */
    private BigDecimal grossProfit;

    /** true si la caja sigue abierta. */
    private boolean active;

    private java.util.List<SaleDetailHistoryResponse> sales;

    public CashReportDTO(){}

    public Long getCashId(){ return cashId; }
    public void setCashId(Long cashId){ this.cashId=cashId; }

    public String getNumber(){ return number; }
    public void setNumber(String number){ this.number=number; }

    public LocalDateTime getOpenedAt(){ return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt){ this.openedAt=openedAt; }

    public LocalDateTime getClosedAt(){ return closedAt; }
    public void setClosedAt(LocalDateTime closedAt){ this.closedAt=closedAt; }

    public BigDecimal getOpeningAmount(){ return openingAmount; }
    public void setOpeningAmount(BigDecimal openingAmount){ this.openingAmount=openingAmount; }

    public BigDecimal getClosingAmount(){ return closingAmount; }
    public void setClosingAmount(BigDecimal closingAmount){ this.closingAmount=closingAmount; }

    public BigDecimal getDifference(){ return difference; }
    public void setDifference(BigDecimal difference){ this.difference=difference; }

    public BigDecimal getExpectedAmount(){ return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount){ this.expectedAmount=expectedAmount; }

    public BigDecimal getTotalSales(){ return totalSales; }
    public void setTotalSales(BigDecimal totalSales){ this.totalSales=totalSales; }

    public BigDecimal getCashSales(){ return cashSales; }
    public void setCashSales(BigDecimal cashSales){ this.cashSales=cashSales; }

    public BigDecimal getDebitSales(){ return debitSales; }
    public void setDebitSales(BigDecimal debitSales){ this.debitSales=debitSales; }

    public BigDecimal getCreditSales(){ return creditSales; }
    public void setCreditSales(BigDecimal creditSales){ this.creditSales=creditSales; }

    public long getTotalTickets(){ return totalTickets; }
    public void setTotalTickets(long totalTickets){ this.totalTickets=totalTickets; }

    public BigDecimal getGrossProfit(){ return grossProfit; }
    public void setGrossProfit(BigDecimal grossProfit){ this.grossProfit=grossProfit; }

    public boolean isActive(){ return active; }
    public void setActive(boolean active){ this.active=active; }

    public java.util.List<SaleDetailHistoryResponse> getSales(){ return sales; }
    public void setSales(java.util.List<SaleDetailHistoryResponse> sales){ this.sales=sales; }
}
