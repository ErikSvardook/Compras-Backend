package com.erikjarquin.compras.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad {@code providers}: proveedor de mercancía (compras).
 *
 * rfc y name son el dato de negocio clave: rfc es UNIQUE (un proveedor no
 * puede duplicarse) y name identifica al negocio. phone/email son opcionales.
 * No se guarda relación inversa con purchases para evitar cargas innecesarias;
 * la integridad al borrar la valida el servicio (409 si tiene compras).
 */
@Entity
@Table(name = "providers")
public class ProviderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String rfc;

    private String phone;

    private String email;

    public ProviderEntity(){}

    //Getter y setter de id
    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id=id;
    }

    //Getter y setter de name
    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name=name;
    }

    //Getter y setter de rfc
    public String getRfc(){
        return rfc;
    }

    public void setRfc(String rfc){
        this.rfc=rfc;
    }

    //Getter y setter de phone
    public String getPhone(){
        return phone;
    }

    public void setPhone(String phone){
        this.phone=phone;
    }

    //Getter y setter de email
    public String getEmail(){
        return email;
    }

    public void setEmail(String email){
        this.email=email;
    }
}