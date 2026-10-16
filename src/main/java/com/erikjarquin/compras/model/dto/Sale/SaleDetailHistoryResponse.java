package com.erikjarquin.compras.model.dto.Sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;

public class SaleDetailHistoryResponse {
    private Long saleId;
    private LocalDateTime saleDate;
    private BigDecimal total;
    private PaymentMethod paymentMethod;
    private List<SaleDetailResponse> items;
    private PaymentStatus paymentStatus;

    //Ciclo de vida (mismo contrato que SaleHistoryResponse: se exponen las dos
    //banderas para que el frontend no tenga que derivar el estado).
    private boolean confirmed;
    private LocalDateTime confirmedAt;
    private boolean cancelled;
    private LocalDateTime cancelledAt;

    /**
     * Usuario que registro la venta.
     *
     * NULL en las ventas anteriores a V5: el dato no se guardaba y no se
     * inventa. El frontend lo muestra como "sin usuario" en vez de "-".
     */
    private Long userId;
    private String userName;

    //Getter y setter de saleId
    public Long getSaleId(){
        return saleId;
    }

    public void setSaleId(Long saleId){
        this.saleId = saleId;
    }

    //Getter y setter de saleDate
    public LocalDateTime getSaleDate(){
        return saleDate;
    }

    public void setSaleDate(LocalDateTime saleDate){
        this.saleDate = saleDate;
    }

    //Getter y setter de total
    public BigDecimal getTotal(){
        return total;
    }

    public void setTotal(BigDecimal total){
        this.total = total;
    }

    //Getter y setter paymentMethod
    public PaymentMethod getPaymentMethod(){
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod){
        this.paymentMethod = paymentMethod;
    }

    //Getter y setter items
    public List<SaleDetailResponse> getItems(){
        return items;
    }

    public void setItems(List<SaleDetailResponse> items){
        this.items = items;
    }

    //Getter y setter
    public PaymentStatus getPaymentStatus(){
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus){
        this.paymentStatus=paymentStatus;
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

    //Getter y setter de user (V5)
    public Long getUserId(){
        return userId;
    }

    public void setUserId(Long userId){
        this.userId=userId;
    }

    public String getUserName(){
        return userName;
    }

    public void setUserName(String userName){
        this.userName=userName;
    }

}