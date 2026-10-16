package com.erikjarquin.compras.model.dto.Sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;


public class SaleHistoryResponse {
    private Long id;
    private LocalDateTime saleDate;
    private BigDecimal total;
    private PaymentMethod paymentMethod;
    private BigDecimal cashReceived;
    private BigDecimal changeAmount;
    private PaymentStatus paymentStatus;

    // ===== Ciclo de vida de la venta =====
    // Se exponen las DOS banderas y no un único "estado" derivado, para que el
    // frontend pueda decidir qué botón pintar sin replicar la máquina de
    // estados del backend:
    //   confirmed=false, cancelled=false -> abierta: se puede confirmar o anular
    //   confirmed=true                   -> congelada: no se toca
    //   cancelled=true                   -> anulada: el stock ya volvió
    private boolean confirmed;
    private LocalDateTime confirmedAt;
    private boolean cancelled;
    private LocalDateTime cancelledAt;

    public SaleHistoryResponse(){}

    //Getter y setter de id
    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id=id;
    }

    //Getter y setter de saleDate
    public LocalDateTime getSaleDate(){
        return saleDate;
    }

    public void setSaleDate(LocalDateTime saleDate){
        this.saleDate=saleDate;
    }

    //Getters y setter de total
    public BigDecimal getTotal(){
        return total;
    }

    public void setTotal(BigDecimal total){
        this.total=total;
    }

    //Getter y setter de paymentMethod
    public PaymentMethod getPaymentMethod(){
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod){
        this.paymentMethod=paymentMethod;
    }

    //Getter y setter de cashReceived
    public BigDecimal getCashReceived(){
        return cashReceived;
    }

    public void setCashReceived(BigDecimal cashReceived){
        this.cashReceived=cashReceived;
    }

    //Getter y setter de changeAmount
    public BigDecimal getChangeAmount(){
        return changeAmount;
    }

    public void setChangeAmount(BigDecimal changeAmount){
        this.changeAmount=changeAmount;
    }

    //Getter y setter de paymentStatus
    public PaymentStatus getPaymentStatus(){
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus){
        this.paymentStatus = paymentStatus;
    }

    //===== Ciclo de vida: getters y setters =====
    public boolean isConfirmed(){
        return confirmed;
    }

    public void setConfirmed(boolean confirmed){
        this.confirmed = confirmed;
    }

    public LocalDateTime getConfirmedAt(){
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt){
        this.confirmedAt = confirmedAt;
    }

    public boolean isCancelled(){
        return cancelled;
    }

    public void setCancelled(boolean cancelled){
        this.cancelled = cancelled;
    }

    public LocalDateTime getCancelledAt(){
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt){
        this.cancelledAt = cancelledAt;
    }
}
