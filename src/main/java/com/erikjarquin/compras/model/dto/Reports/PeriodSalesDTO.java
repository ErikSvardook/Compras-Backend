package com.erikjarquin.compras.model.dto.Reports;

import java.math.BigDecimal;

/**
 * Un punto de la tendencia de ventas ({@code GET /api/local/reports/trend}).
 *
 * {@code period} es a string ISO según la agrupación pedida: "yyyy-MM-dd"
 * (DÍA), "yyyy-MM" (MES) o "yyyy" (AÑO). {@code total} es la suma de ventas
 * APPROVED del periodo y {@code count} el número de tickets.
 */
public class PeriodSalesDTO {
    private String period;
    private BigDecimal total;
    private Long count;

    public PeriodSalesDTO(){}

    public PeriodSalesDTO(String period, BigDecimal total, Long count){
        this.period = period;
        this.total = total;
        this.count = count;
    }

    public String getPeriod(){ return period; }
    public void setPeriod(String period){ this.period = period; }

    public BigDecimal getTotal(){ return total; }
    public void setTotal(BigDecimal total){ this.total = total; }

    public Long getCount(){ return count; }
    public void setCount(Long count){ this.count = count; }
}