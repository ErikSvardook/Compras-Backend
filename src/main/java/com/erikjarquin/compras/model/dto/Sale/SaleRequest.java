package com.erikjarquin.compras.model.dto.Sale;

import java.math.BigDecimal;
import java.util.List;

import com.erikjarquin.compras.model.dto.Payment.CardPaymentRequest;
import com.erikjarquin.compras.model.enums.PaymentMethod;

public class SaleRequest {
    private PaymentMethod paymentMethod;
    private BigDecimal cashReceived;
    private List<SaleItemRequest> items;
    private CardPaymentRequest cardPayment; //Pago con tarjeta

    /**
     * Clave de idempotencia del intento de cobro.
     *
     * La genera el cliente (un UUID) y la repite en cada reintento del mismo
     * cobro. Si el backend ya tiene una venta con esta clave, devuelve esa en
     * vez de crear otra.
     *
     * Opcional a propósito. Si fuera obligatoria, cualquier cliente
     * viejo (o una prueba) sin la clave empezaría a recibir 400. Con ella
     * opcional, el que no la manda sigue funcionando como siempre y solo quien
     * la manda queda protegido. El frontend del POS sí la manda siempre.
     */
    private String idempotencyKey;

    public SaleRequest(){}

    //Getter y setter de paymentMethod
    public PaymentMethod getPaymentMethod(){
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod){
        this.paymentMethod=paymentMethod;
    }

    //Getter y setter de paymentMethod
    public BigDecimal getCashReceived(){
        return cashReceived;
    }

    public void setCashReceived(BigDecimal cashReceived){
        this.cashReceived=cashReceived;
    }

    //Getter y setter de paymentMethod
    public List<SaleItemRequest> getItems(){
        return items;
    }

    public void setItems(List<SaleItemRequest> items){
        this.items=items;
    }

    //Getter y setter de cardPayment
    public CardPaymentRequest getCardPayment(){
        return cardPayment;
    }

    public void setCardPayment(CardPaymentRequest cardPayment){
        this.cardPayment=cardPayment;
    }

    //Getter y setter de idempotencyKey (V6)
    public String getIdempotencyKey(){
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey){
        this.idempotencyKey=idempotencyKey;
    }
}
