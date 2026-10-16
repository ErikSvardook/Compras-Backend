package com.erikjarquin.compras.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Entidad {@code sales}: cabecera de una venta.
 *
 * Relaciones: 1:N a SaleDetailEntity (productos vendidos, cascade ALL +
 * orphanRemoval), N:1 a CashRegisterEntity (caja donde se registró) y 1:1 a
 * PaymentEntity (solo si fue pagada con tarjeta). paymentStatus recorre:
 * PENDING → APPROVED | REJECTED | REVERSED.
 *
 * Ciclo de vida de la venta
 *
 * Son ejes separados porque una venta con tarjeta APROBADA puede estar
 * CANCELADA: se cobró y después se devolvió el dinero. Meter un estado
 * "CANCELLED" dentro de {@link PaymentStatus} habría mezclado "estado del pago"
 * con "estado de la venta" y habría hecho touched también al CHECK
 * {@code payments_status_check} de la BD.
 *
 * Reglas (las aplica SaleImpl, no la entidad):
 * 
 *   Venta recien creada: {@code confirmed=false}, {@code cancelled=false}
 *       -> se puede anular (error de caja).
 *   Confirmada: {@code confirmed=true} -> CONGELADA. Ya no se anula ni se
 *       borra jamás, porque el stock y el dinero son reales.
 *   Anulada: {@code cancelled=true} -> el stock volvió al inventario, pero
 *       la fila se CONSERVA (auditoría: no se borran ventas).
 *   Nunca ambas a la vez: son mutuamente excluyentes.
 *
 * Por qué {@code boolean} y no un {@code enum}: un enum obligaría a que las
 * filas ya existentes migraran de un valor a otro y a que el estado "no
 * decidido" fuera un valor más. Con dos banderas el estado inicial es el valor
 * natural (false/false) y {@code DEFAULT false} deja las ventas históricas en
 * "abierta", que es lo correcto: se pueden confirmar después.
 */
@Entity
@Table(name = "sales")
public class SaleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime saleDate;

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private BigDecimal cashReceived;

    private BigDecimal changeAmount;

    @OneToMany(
        mappedBy = "sale",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<SaleDetailEntity> details;

    @ManyToOne
    @JoinColumn(name = "cash_register_id")
    private CashRegisterEntity cashRegister;

    /**
     * Usuario (empleado) que registró la venta.
     *
     * Es lo que permite responder "¿qué caja vendió Juan?" y "¿cuánto vendió
     * cada cajero?": antes la venta solo apuntaba al turno, y de un turno no se
     * puede saber quién cobró cada ticket.
     *
     * LAZY porque el filtro por usuario solo necesita el id (que ya
     * viene en la propia venta) y el nombre se pide en una consulta aparte de
     * reportes. Con EAGER, listar el historial de ventas haría un SELECT extra
     * por venta.
     *
     * NULL en las ventas anteriores: el dato nunca se guardó y no
     * se inventa. Por eso el filtro por usuario solo aplica a ventas nuevas.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @OneToOne(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private PaymentEntity payment;

    // ===== Ciclo de vida de la venta (ver V2__venta_confirmada.sql) =====
    // `nullable = false` en el mapeo NO es lo mismo que un NOT NULL en BD: la
    // BD ya lo garantiza, JPA solo lo documenta. El `columnDefinition` fija
    // el DEFAULT para que un INSERT que no mencione la columna (p. ej. un
    // test que arma la entidad a mano) guarde false y no null.

    /** true = venta congelada: no se puede anular ni borrar. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean confirmed;

    /** Momento en que se confirmó; null mientras la venta siga abierta. */
    private LocalDateTime confirmedAt;

    /** true = venta anulada (el stock volvió al inventario). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean cancelled;

    /** Momento de la anulación; null si la venta no fue anulada. */
    private LocalDateTime cancelledAt;

    public SaleEntity(){}

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

    //Getter y setter de total
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

    //Getter y setter de details
    public List<SaleDetailEntity> getDetails(){
        return details;
    }

    public void setDetails(List<SaleDetailEntity> details){
        this.details=details;
    }

    //Getter y setter de CashRegister
    public CashRegisterEntity getCashRegister(){
        return cashRegister;
    }

    public void setCashRegister(CashRegisterEntity cashRegister){
        this.cashRegister=cashRegister;
    }

    //Getter y setter de user (V5: quién registró la venta)
    public UserEntity getUser(){
        return user;
    }

    public void setUser(UserEntity user){
        this.user=user;
    }

    /**
     * Clave de idempotencia del intento de cobro.
     *
     * El cliente la genera (un UUID por intento) y la repite en cada reintento
     * del MISMO cobro. Si la clave ya existe en la tabla, el backend devuelve la
     * venta ya creada en vez de crear otra: sin esto, un doble "Enter" en el
     * modal de cobro descuenta el stock dos veces y genera dos tickets por una
     * sola compra.
     *
     * La genera el CLIENTE, no el servidor. Si la generara el backend en
     * cada petición, cada una sería distinta y la protección no serviría de
     * nada: la clave tiene que viajar con la intención de cobro, no con la
     * llamada.
     *
     * Es nullable: las ventas anteriores y las creadas por otros caminos
     * no la tienen. En PostgreSQL un UNIQUE admite varios NULL (porque
     * NULL != NULL), así que no estorban.
     */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    public String getIdempotencyKey(){
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey){
        this.idempotencyKey=idempotencyKey;
    }

    //Getter y setter de paymentStatus
    public PaymentStatus getPaymentStatus(){
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus){
        this.paymentStatus=paymentStatus;
    }

    //Getter y setter de payment
    public PaymentEntity getPayment(){
        return payment;
    }

    public void setPayment(PaymentEntity payment){
        this.payment = payment;
    }

    //===== Ciclo de vida: getters y setters =====
    //En una entidad JPA, `boolean` (no Boolean) evita el,null-trap: un Boolean
    //sin asignar es null y un `if (confirmado)` sobre un Boolean desboxea null
    //y lanza NullPointerException. Con boolean primitivo el valor por defecto
    //del objeto Java es false y no hay sorpresas.
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
