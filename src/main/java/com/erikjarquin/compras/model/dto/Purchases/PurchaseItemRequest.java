package com.erikjarquin.compras.model.dto.Purchases;

import java.math.BigDecimal;

/**
 * Item de compra que llega del cliente (dentro de PurchaseRequest).
 * productId identifica el producto, quantity las unidades y unitCost el costo
 * unitario REAL pagado al proveedor (se copia al producto como su costo).
 *
 * unitPrice es el precio de VENTA que se aplicará al
 * producto al confirmar. Opcional a propósito: si no viene, confirmar deja el
 * precio de venta que el producto ya tiene. Es el campo que hace posible el
 * flujo "reponer stock y de paso revisar el precio de venta" sin que el módulo
 * de Compras tenga que inventar un precio por su cuenta.
 */
public class PurchaseItemRequest {
    private Long productId;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal unitPrice;

    public PurchaseItemRequest(){}

    public Long getProductId(){ return productId; }
    public void setProductId(Long productId){ this.productId=productId; }

    public Integer getQuantity(){ return quantity; }
    public void setQuantity(Integer quantity){ this.quantity=quantity; }

    public BigDecimal getUnitCost(){ return unitCost; }
    public void setUnitCost(BigDecimal unitCost){ this.unitCost=unitCost; }

    public BigDecimal getUnitPrice(){ return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice){ this.unitPrice=unitPrice; }
}