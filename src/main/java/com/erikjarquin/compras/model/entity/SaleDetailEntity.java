package com.erikjarquin.compras.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad {@code sale_details}: renglón de venta (un producto vendido).
 *
 * Guarda una copia del precio unitario en el momento de la venta (unitPrice)
 * para que cambios futuros de precio no alteren el histórico. subtotal =
 * unitPrice * quantity.
 *
 * unitCost es el mismo congelamiento pero del COSTO:
 * el costo que tenía el producto al momento de venderse. Sin él, la utilidad
 * de una venta pasada había que calcularla con {@code product.cost}, que es el
 * costo del último purchase de hoy: comprar algo más barato mañana
 * reescribía la ganancia de ayer. Nullable porque los renglones anteriores
 * se rellenaron con el costo actual como mejor aproximación, y un producto sin
 * costo queda NULL en vez de un 0 que inflaría la utilidad.
 */
@Entity
@Table(name = "sale_details")
public class SaleDetailEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sale_id")
    private SaleEntity sale;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    private Integer quantity;

    private BigDecimal unitPrice;

    //Costo del producto congelado al vender (base del reporte de utilidad)
    private BigDecimal unitCost;

    private BigDecimal subtotal;

    public SaleDetailEntity(){}

    //Getter y setter de id
    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id=id;
    }

    //Getter y setter de sale
    public SaleEntity getSale(){
        return sale;
    }

    public void setSale(SaleEntity sale){
        this.sale=sale;
    }

    //Getter y setter de product
    public ProductEntity getProduct(){
        return product;
    }

    public void setProduct(ProductEntity product){
        this.product=product;
    }

    //Getter y setter de quantity
    public Integer getQuantity(){
        return quantity;
    }

    public void setQuantity(Integer quantity){
        this.quantity=quantity;
    }

    //Getter y setter de unitPrice
    public BigDecimal getUnitPrice(){
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice){
        this.unitPrice=unitPrice;
    }

    //Getter y setter de unitCost (costo congelado al vender)
    public BigDecimal getUnitCost(){
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost){
        this.unitCost=unitCost;
    }

    //Getter y setter de subtotal
    public BigDecimal getSubTotal(){
        return subtotal;
    }

    public void setSubTotal(BigDecimal subtotal){
        this.subtotal=subtotal;
    }


}
