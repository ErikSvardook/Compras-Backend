package com.erikjarquin.compras.model.dto.Reports;

import java.math.BigDecimal;

/**
 * Utilidad de un periodo ({@code GET /api/local/reports/profit}, V3).
 *
 * Es el reporte que responde "¿cuánto gané?", y para eso hace falta el
 * costo de lo vendido, no el total de las compras del periodo. Son dos
 * cifras distintas y confundirlas es el error clásico:
 *   compras del periodo: dinero que salió, pero todavía está en el
 *       almacén. Gastarlo todo como si fuera pérdida subestima la utilidad.
 *   costo de lo vendido ({@code costOfGoodsSold}): lo que costó
 *       específicamente lo que los clientes ya se llevaron. Es lo que se
 *       descuenta del ingreso.
 *
 * El costo sale de {@code sale_details.unitCost}, el costo CONGELADO en el
 * renglón al momento de vender. Si se usara {@code product.cost} (el del
 * último purchase de hoy) comprar algo más barato mañana reescribiría la
 * ganancia de ayer.
 *
 * Excluye ventas anuladas y con pago no aprobado, igual que los demás
 * reportes.
 */
public class ProfitDTO {
    /** Ingresos: total de las ventas válidas del periodo. */
    private BigDecimal revenue;

    /** Costo de lo vendido: quantity × unitCost de los renglones. */
    private BigDecimal costOfGoodsSold;

    /** Utilidad bruta = revenue − costOfGoodsSold. */
    private BigDecimal grossProfit;

    /** Margen sobre venta = grossProfit / revenue × 100 (0 si no hubo ingresos). */
    private BigDecimal marginPercent;

    /** Tickets válidos del periodo. */
    private long tickets;

    /** Unidades vendidas del periodo. */
    private long itemsSold;

    /**
     * Renglones vendidos SIN costo conocido (0 en ventas nuevas; posible en
     * ventas anteriores). Existe para poder avisar en pantalla en vez de
     * mostrar una utilidad inflada sin que nadie entienda por qué.
     */
    private long itemsWithoutCost;

    public ProfitDTO(){}

    public ProfitDTO(
            BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal grossProfit,
            BigDecimal marginPercent, long tickets, long itemsSold, long itemsWithoutCost){
        this.revenue = revenue;
        this.costOfGoodsSold = costOfGoodsSold;
        this.grossProfit = grossProfit;
        this.marginPercent = marginPercent;
        this.tickets = tickets;
        this.itemsSold = itemsSold;
        this.itemsWithoutCost = itemsWithoutCost;
    }

    public BigDecimal getRevenue(){ return revenue; }
    public void setRevenue(BigDecimal revenue){ this.revenue=revenue; }

    public BigDecimal getCostOfGoodsSold(){ return costOfGoodsSold; }
    public void setCostOfGoodsSold(BigDecimal costOfGoodsSold){ this.costOfGoodsSold=costOfGoodsSold; }

    public BigDecimal getGrossProfit(){ return grossProfit; }
    public void setGrossProfit(BigDecimal grossProfit){ this.grossProfit=grossProfit; }

    public BigDecimal getMarginPercent(){ return marginPercent; }
    public void setMarginPercent(BigDecimal marginPercent){ this.marginPercent=marginPercent; }

    public long getTickets(){ return tickets; }
    public void setTickets(long tickets){ this.tickets=tickets; }

    public long getItemsSold(){ return itemsSold; }
    public void setItemsSold(long itemsSold){ this.itemsSold=itemsSold; }

    public long getItemsWithoutCost(){ return itemsWithoutCost; }
    public void setItemsWithoutCost(long itemsWithoutCost){ this.itemsWithoutCost=itemsWithoutCost; }
}
