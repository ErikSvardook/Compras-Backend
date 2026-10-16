package com.erikjarquin.compras.model.dto.Reports;

import java.math.BigDecimal;

/**
 * Margen de un producto ({@code GET /api/local/reports/margins}).
 *
 * cost es el último costo real registrado por una compra (0 si nunca se ha
 * comprado). margin = price - cost y marginPercent = margin/price*100 (cuanto
 * representa la ganancia sobre el precio de venta). Se ordena de MENOR a MAYOR
 * margen: los primeros son los que conviene renegociar con el proveedor.
 */
public class MarginDTO {
    private Long productId;
    private String name;
    private String sku;
    private BigDecimal cost;
    private BigDecimal price;
    private BigDecimal margin;
    private BigDecimal marginPercent;

    public MarginDTO(){}

    public MarginDTO(
            Long productId, String name, String sku,
            BigDecimal cost, BigDecimal price, BigDecimal margin, BigDecimal marginPercent){
        this.productId = productId;
        this.name = name;
        this.sku = sku;
        this.cost = cost;
        this.price = price;
        this.margin = margin;
        this.marginPercent = marginPercent;
    }

    public Long getProductId(){ return productId; }
    public void setProductId(Long productId){ this.productId=productId; }

    public String getName(){ return name; }
    public void setName(String name){ this.name=name; }

    public String getSku(){ return sku; }
    public void setSku(String sku){ this.sku=sku; }

    public BigDecimal getCost(){ return cost; }
    public void setCost(BigDecimal cost){ this.cost=cost; }

    public BigDecimal getPrice(){ return price; }
    public void setPrice(BigDecimal price){ this.price=price; }

    public BigDecimal getMargin(){ return margin; }
    public void setMargin(BigDecimal margin){ this.margin=margin; }

    public BigDecimal getMarginPercent(){ return marginPercent; }
    public void setMarginPercent(BigDecimal marginPercent){ this.marginPercent=marginPercent; }
}