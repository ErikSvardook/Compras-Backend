package com.erikjarquin.compras.model.dto.Sale;

import java.math.BigDecimal;

public class SaleDetailResponse {
    private String product;
    private Integer quantity;
    private BigDecimal unitPrice;

    /**
     * Costo del producto congelado al momento de la venta. Permite ver la
     * ganancia de la línea (subtotal − quantity × unitCost) sin que un cambio
     * futuro de costo altere el histórico. Null en renglones sin costo conocido.
     */
    private BigDecimal unitCost;

    private BigDecimal subtotal;

    //Getter y setter de product
    public String getProduct(){
        return product;
    }

    public void setProduct(String product){
        this.product = product;
    }

    //Getter y setter de queantity
    public Integer getQuantity(){
        return quantity;
    }

    public void setQuantity(Integer quantity){
        this.quantity = quantity;
    }

    //Getter y setter de unitPrice
    public BigDecimal getUnitPrice(){
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice){
        this.unitPrice = unitPrice;
    }

    //Getter y setter de unitCost (costo congelado del producto al vender)
    public BigDecimal getUnitCost(){
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost){
        this.unitCost = unitCost;
    }

    //Getter y setter de subtotal
    public BigDecimal getSubtotal(){
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal){
        this.subtotal = subtotal;
    }
    

}
