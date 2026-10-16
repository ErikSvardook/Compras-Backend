package com.erikjarquin.compras.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Entidad {@code purchases}: cabecera de una compra a un proveedor.
 *
 * Relaciones: 1:N a PurchaseDetailEntity (artículos comprados, cascade ALL +
 * orphanRemoval, mismo patrón que sales/details) y N:1 a ProviderEntity (quién
 * nos vendió). total = Σ unitCost * quantity de los detalles.
 *
 * Efectos secundarios al registrar/cancelar (transaccionales, en el
 * servicio): la compra SUMA stock del producto y guarda su costo real; la
 * cancelación lo revierte.
 */
@Entity
@Table(name = "purchases")
public class PurchaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime purchaseDate;

    @ManyToOne(optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderEntity provider;

    @OneToMany(
        mappedBy = "purchase",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<PurchaseDetailEntity> details;

    private BigDecimal total;

    //---- Ciclo de vida de la compra ----

    /**
     * {@code true} = la compra está CONFIRMADA: la mercancía entró al almacén y
     * ya se le sumó el stock y se guardó el costo real del producto. Desde este
     * punto es inmutable: no se puede cancelar, porque el stock ya se usó
     * o se vendió y "deshacerlo" escribiría inventario falso.
     *
     * {@code false} = la compra está PENDIENTE: se registró pero no tocó
     * el inventario. Se puede cancelar (se borra, sin revertir nada).
     *
     * Es el mismo patrón que {@code confirmed}/{@code cancelled} de la venta
     * y por la misma razón: el estado terminal es lo que protege al
     * inventario. Antes de esta migración, crear la compra ya sumaba stock de
     * inmediato; las compras que ya existían se marcaron confirmadas en el
     * backfill, porque su stock ya se había aplicado.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean confirmed;

    /**
     * Cuándo se confirmó (y por tanto cuándo se aplicó el stock). NULL mientras
     * la compra siga pendiente.
     */
    private LocalDateTime confirmedAt;

    public PurchaseEntity(){}

    //Getter y setter de id
    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id=id;
    }

    //Getter y setter de purchaseDate
    public LocalDateTime getPurchaseDate(){
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDateTime purchaseDate){
        this.purchaseDate=purchaseDate;
    }

    //Getter y setter de provider
    public ProviderEntity getProvider(){
        return provider;
    }

    public void setProvider(ProviderEntity provider){
        this.provider=provider;
    }

    //Getter y setter de details
    public List<PurchaseDetailEntity> getDetails(){
        return details;
    }

    public void setDetails(List<PurchaseDetailEntity> details){
        this.details=details;
    }

    //Getter y setter de total
    public BigDecimal getTotal(){
        return total;
    }

    public void setTotal(BigDecimal total){
        this.total=total;
    }

    //Getter y setter de confirmed
    public boolean isConfirmed(){
        return confirmed;
    }

    public void setConfirmed(boolean confirmed){
        this.confirmed=confirmed;
    }

    //Getter y setter de confirmedAt
    public LocalDateTime getConfirmedAt(){
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt){
        this.confirmedAt=confirmedAt;
    }
}