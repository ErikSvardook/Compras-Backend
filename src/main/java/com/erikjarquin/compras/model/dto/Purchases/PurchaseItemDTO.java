package com.erikjarquin.compras.model.dto.Purchases;

import java.math.BigDecimal;

/**
 * Renglón de compra en la respuesta: producto con su costo unitario.
 * Se incluye productName para que el frontend no tenga que cruzar con el
 * catálogo de productos.
 */
public class PurchaseItemDTO {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitCost;

    /**
     * Precio de VENTA aplicado al producto al confirmar. Se devuelve para
     * que el frontend muestre "costo de compra" y "precio de venta" juntos en
     * el renglón, que es donde el usuario compara si el margen sigue siendo
     * sano. NULL = la compra no cambió el precio de venta.
     */
    private BigDecimal unitPrice;

    private BigDecimal subtotal;

    public PurchaseItemDTO(){}

    public Long getProductId(){ return productId; }
    public void setProductId(Long productId){ this.productId=productId; }

    public String getProductName(){ return productName; }
    public void setProductName(String productName){ this.productName=productName; }

    public Integer getQuantity(){ return quantity; }
    public void setQuantity(Integer quantity){ this.quantity=quantity; }

    public BigDecimal getUnitCost(){ return unitCost; }
    public void setUnitCost(BigDecimal unitCost){ this.unitCost=unitCost; }

    public BigDecimal getUnitPrice(){ return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice){ this.unitPrice=unitPrice; }

    public BigDecimal getSubtotal(){ return subtotal; }
    public void setSubtotal(BigDecimal subtotal){ this.subtotal=subtotal; }
}