package com.erikjarquin.compras.model.dto.Cash;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Respuesta de caja: el estado de una caja y, si está cerrada, su corte.
 *
 * Se usa la misma clase para la caja recién creada, la activa, la cerrada y la
 * del historial. Los campos que todavía no aplican a una caja recién creada
 * (que aún no abre) vienen en null: por eso el frontend tiene que comprobarlos
 * antes de mostrarlos.
 */
public class CashResponse {
    private Long id;

    /**
     * Número de la caja. Es lo que se muestra en la lista de cajas de
     * Reportes y lo que el usuario elige para filtrar. Nonnull desde V3: se
     * asigna al CREAR la caja, no al abrirla.
     */
    private String number;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private BigDecimal openingAmount;
    private BigDecimal closingAmount;
    private Boolean active;
    private BigDecimal expectedAmount;
    private BigDecimal difference;

    /**
     * Motivo del descuadre al cerrar. NULL = el corte cuadró exactamente. Si
     * tiene valor, el cierre se hizo con la salida de emergencia y hay que
     * investigarlo: significa que el dinero contado no coincide con el esperado y
     * alguien decidió cerrar de todos modos, dejando constancia del por qué.
     */
    private String differenceReason;

    private BigDecimal cashSales;
    private BigDecimal debitSales;
    private BigDecimal creditSales;
    private BigDecimal totalSales;
    private int totalTickets;

    //Constructor vacío
    public CashResponse(){}

    //Getter y setter de id
    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id = id;
    }

    //Getter y setter de number
    public String getNumber(){
        return number;
    }

    public void setNumber(String number){
        this.number = number;
    }

    public LocalDateTime getOpenedAt(){
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt){
        this.openedAt = openedAt;
    }

    public LocalDateTime getClosedAt(){
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt){
        this.closedAt = closedAt;
    }

    public BigDecimal getOpeningAmount(){
        return openingAmount;
    }

    public void setOpeningAmount(BigDecimal openingAmount){
        this.openingAmount = openingAmount;
    }

    public BigDecimal getClosingAmount(){
        return closingAmount;
    }

    public void setClosingAmount(BigDecimal closingAmount){
        this.closingAmount = closingAmount;
    }

    public Boolean getActive(){
        return active;
    }

    public void setActive(Boolean active){
        this.active = active;
    }

    public BigDecimal getExpectedAmount(){
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount){
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getDifference(){
        return difference;
    }

    public void setDifference(BigDecimal difference){
        this.difference = difference;
    }

    //Getter y setter de differenceReason (V3)
    public String getDifferenceReason(){
        return differenceReason;
    }

    public void setDifferenceReason(String differenceReason){
        this.differenceReason = differenceReason;
    }

    public BigDecimal getCashSales(){
        return cashSales;
    }

    public void setCashSales(BigDecimal cashSales){
        this.cashSales = cashSales;
    }

    public BigDecimal getDebitSales(){
        return debitSales;
    }

    public void setDebitSales(BigDecimal debitSales){
        this.debitSales = debitSales;
    }

    public BigDecimal getCreditSales(){
        return creditSales;
    }

    public void setCreditSales(BigDecimal creditSales){
        this.creditSales = creditSales;
    }

    public BigDecimal getTotalSales(){
        return totalSales;
    }

    public void setTotalSales(BigDecimal totalSales){
        this.totalSales = totalSales;
    }

    public int getTotalTickets(){
        return totalTickets;
    }

    public void setTotalTickets(int totalTickets){
        this.totalTickets = totalTickets;
    }
}
