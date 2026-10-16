package com.erikjarquin.compras.model.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad {@code products}: artículos del inventario para la venta.
 *
 * {@code img} guarda el NOMBRE del archivo en disco (UUID+ext), no la URL;
 * la URL completa la construye ProductMapper con ${app.upload-url}.
 * barcode y sku son únicos; stock es la cantidad física disponible.
 */
@Entity
@Table(name="products")
public class ProductEntity {
   
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    /**
     * Costo unitario REAL del producto, alimentado por el módulo de COMPRAS:
     * al registrar una compra se guarda el último unitCost recibido. 0 cuando
     * nunca se ha comprado. price - cost = margen (estadística de Márgenes).
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(nullable = false)
    private int stock;

    @ManyToOne(fetch=FetchType.LAZY)//No genera posibles consultas dobles, una por cada producto
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    private String img;

    /**
     * Código de barras del producto (bligatorio).
     *
     * `nullable = false` debe ir ACOMPAÑADO del `NOT NULL` de la migración
     * {@code V7__campos_obligatorios.sql}, y no al revés. La aplicación corre
     * con {@code ddl-auto: validate}, así que Hibernate no toca el esquema: solo
     * lo comprueba al arrancar. Si la entidad dice que no puede ser nulo y
     * la columna lo permite, la aplicación no levanta, y ese arranque
     * fallido es justamente la red que hace honesto mantener las dos cosas
     * sincronizadas.
     */
    @Column(unique = true, nullable = false)
    private String barcode;

    /**
     * SKU del producto (V7: <b>obligatorio</b>).
     *
     * <p>Mismo criterio que {@link #barcode}: el {@code nullable = false} de aquí
     * y el {@code NOT NULL} de la base van de la mano.
     */
    @Column(unique = true, nullable = false)
    private String sku;

    /**
     * Borrado lógico: false = producto "dado de baja". Un producto dado de baja
     * NO se borra de la BD (así conserva el histórico de ventas/compras que lo
     * referencian) y deja de aparecer en el catálogo del POS y en las ventas
     * nuevas. Se puede reactivar volviendo a active=true.
     * {@code columnDefinition = "boolean default true"} asegura que las filas
     * preexistentes queden activas al aplicar el ddl-auto=update.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    public ProductEntity(){}

    //getter y setter de id
    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id=id;
    }

    //getter y setter de name
    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name=name;
    }

    //getter y setter de price
    public BigDecimal getPrice(){
        return price;
    }

    public void setPrice(BigDecimal price){
        this.price=price;
    }

    //Getter y setter de cost
    public BigDecimal getCost(){
        return cost;
    }

    public void setCost(BigDecimal cost){
        this.cost=cost;
    }

    //getter y setter de stock
    public int getStock(){
        return stock;
    }

    public void setStock(int stock){
        this.stock=stock;
    }

    //getter y setter de category
    public CategoryEntity getCategory(){
        return category;
    }

    public void setCategory(CategoryEntity category){
        this.category=category;
    }

    //getter y setter de img
    public String getImg(){
        return img;
    }

    public void setImg(String img){
        this.img=img;
    }

    //Getter y setter de barcode
    public String getBarcode(){
        return barcode;
    }

    public void setBarcode(String barcode){
        this.barcode=barcode;
    }

    //Getter y setter de sku
    public String getSku(){
        return sku;
    }

    public void setSku(String sku){
        this.sku=sku;
    }

    //Getter y setter de active (borrado lógico: false = dado de baja)
    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active=active;
    }
}

