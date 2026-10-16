package com.erikjarquin.compras.model.entity;

import java.time.LocalDateTime;

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
 * Entidad {@code users}: un usuario del sistema con su rol y datos de sesión.
 *
 * La contraseña SIEMPRE se guarda hasheada (BCrypt). Cuando se "elimina"
 * un usuario, en realidad se desactiva (active=false) para conservar el
 * historial de ventas. Los campos resetToken/resetTokenExpiration se usan en
 * la recuperación de contraseña (token de 1 hora de validez).
 */
@Entity
@Table(name = "users")
public class UserEntity {
    
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String name;

    @Column(unique=true, nullable=false)
    private String email;

    @Column(nullable=false)
    private String password;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Última vez que el usuario se dio de alta.
     *
     * Se informa en la creación y en cada re-activación. Junto con
     * {@code active} responde "¿desde cuándo es parte del equipo?".
     */
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    /**
     * Última vez que el usuario se dio de baja.
     *
     * Solo se informa si {@code active == false}. Es lo que hace posible
     * buscar el registro de una persona por el rango de fechas en que se fue,
     * algo que con el booleano `active` solo no se podía contestar.
     *
     * Es la última baja, no un historial: para tener cada cambio
     * haría falta una tabla de auditoría, que no existe (y no hace falta aquí).
     */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiration")
    private LocalDateTime resetTokenExpiration;

    //getter y setter de id
    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id=id;
    }

    //getter y setter de nombre
    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name=name;
    }

    //getter y setter de email
    public String getEmail(){
        return email;
    }

    public void setEmail(String email){
        this.email=email;
    }

    //getter y setter para password 
    public String getPassword(){
        return password;
    }

    public void setPassword(String password){
        this.password=password;
    }

    //getter y setter de role
    public RoleEntity getRole(){
        return role;
    }

    public void setRole(RoleEntity role){
        this.role=role;
    }

    //getter y setter para boolean
    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active=active;
    }

    //Getter y setter de activatedAt (V5)
    public LocalDateTime getActivatedAt(){
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt){
        this.activatedAt=activatedAt;
    }

    //Getter y setter de deactivatedAt (V5)
    public LocalDateTime getDeactivatedAt(){
        return deactivatedAt;
    }

    public void setDeactivatedAt(LocalDateTime deactivatedAt){
        this.deactivatedAt=deactivatedAt;
    }

    //Getter y setter de resetToken
    public String getResetToken(){
        return resetToken;
    }

    public void setResetToken(String resetToken){
        this.resetToken=resetToken;
    }

    //Getter y setter de resetTokenExpiration
    public LocalDateTime getResetTokenExpiration(){
        return resetTokenExpiration;
    }

    public void setResetTokenExpiration(LocalDateTime resetTokenExpiration){
        this.resetTokenExpiration=resetTokenExpiration;
    }

}