package com.erikjarquin.compras.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
  Entidad cash_boxes: el registro de las cajas fisicas del local
  No habia ninguna: cada fila de cash_registers era "una caja" y por eso esa
  fila se cerraba una sola vez, para siempre. Pero un negocio real tiene dos o tres
  cajas que se abren y cierran todas los días.
  */

@Entity 
@Table(name = "cash_boxes")
public class CashBoxEntity {
  
  @Id 
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String number;

  @Column(length = 255)
  private String description;

  /**
   *Nunca se borra una caja que ya tuvo cortes
   *Borrarla rompería el historial del reporte 
   */
  @Column(nullable = false, columnDefinition = "boolean default true")
  private boolean active = true;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public CashBoxEntity(){}

  //Getter y setter de Id
  public Long getId(){
    return id;
  }

  public void setId(Long id){
    this.id=id;
  }

  //Getter y setter de number
  public String getNumber(){
    return number;
  }

  public void setNumber(String number){
    this.number=number;
  }

  //Getter y setter de descripción
  public String getDescription(){
    return description;
  }

  public void setDescription(String description){
    this.description=description;
  }

  //Getter y setter de active
  public boolean isActive(){
    return active;
  }

  public void setActive(boolean active){
    this.active=active;
  }

  //Getter y setter de createdAt
  public LocalDateTime getCreatedAt(){
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt){
    this.createdAt=createdAt;
  }

  //Getter y setter de updatedAt
  public LocalDateTime getUpdatedAt(){
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updateAt){
    this.updatedAt=updateAt;
  }
}
