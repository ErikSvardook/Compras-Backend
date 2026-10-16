package com.erikjarquin.compras.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad {@code payments}: pago con tarjeta asociado 1:1 a una venta.
 *
 * SEGURIDAD: solo se persiste los últimos 4 dígitos (lastFourDigits) y el
 * código de autorización; NUNCA el número completo de tarjeta ni el PIN.
 * Estados: PENDING → PROCESSING → APPROVED | REJECTED, y APPROVED → REVERSED.
 * transactionId es único (generado por PaymentImpl).
 *
 * @PrePersist/@PreUpdate registran timestamps e inicializan contadores.
 */
@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "sale_id")
    private SaleEntity sale;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod; //DEBIT O CREDIT

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;//PEDDING, APPROVED, REJECTED

    private BigDecimal amount;
    private String authorizationCode; //Código de autorización simulado
    private LocalDateTime paymentDate;
    private String lastFourDigits; //Últimos cuatro dígitos de la tarjeta (por seguridad)
    private String errorMessage; // Si fue rechazado, razón

    @Column(unique = true, nullable = false)
    private String transactionId; // ID de la transacción

    private Integer attemptCount; //Para registrar reintentos
    private boolean statusQueried; //Para saber si ya se consulto el estado
    private LocalDateTime lastStatusQuery; //
    private String cardBrand; // Marca de la tarjeta (Visa, MC)
    private String cardType; // CREDIT O DEBIT
    private String responseCode; // Código de respuesta del banco
    private String responseMessage; // Mensaje del banco

    //Reversa
    private String reversalTransactionId;
    private LocalDateTime reversalDate;
    private String reversalReason;
    private String reversalErrorMessage;
    private Integer reversalAttempCount;

    //Timestamps
    private LocalDateTime createAt;
    private LocalDateTime updateAt;

    @PrePersist
    protected void onCreate(){
        createAt = LocalDateTime.now();
        updateAt = LocalDateTime.now();

        if(attemptCount == null){
            attemptCount = 0;
        }

        if(reversalAttempCount == null){
            reversalAttempCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate(){
        updateAt = LocalDateTime.now();
    }

}
