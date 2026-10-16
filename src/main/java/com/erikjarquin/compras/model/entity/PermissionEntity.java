package com.erikjarquin.compras.model.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.erikjarquin.compras.model.enums.PermissionName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Entidad {@code permissions}: acción individual autorizable.
 *
 * name es el enum PermissionName (32 valores) y se persiste como String.
 * equals/hashCode se basan SOLO en name (necesario para que los Sets de
 * permisos funcionen y el bootstrap los compare por nombre).
 */
@Entity
@Table(name = "permissions")
public class PermissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private PermissionName name;

    @ManyToMany(mappedBy = "permissions")
    @JsonIgnore
    private List<RoleEntity> roles = new ArrayList<>();

    public PermissionEntity(){}

    public PermissionEntity(Long id, PermissionName name){
        this.id=id;
        this.name=name;
    }

    //Getter y setter de id
    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id=id;
    }

    //Getter y setter de nombre
    public PermissionName getName(){
        return name;
    }

    public void setName(PermissionName name){
        this.name=name;
    }

    //Getter y setter de roles
    public List<RoleEntity> getRoles(){
        return roles;
    }

    public void setRoles(List<RoleEntity> roles){
        this.roles=roles;
    }

    @Override
    public boolean equals(Object o){
        if(this == o){
            return true;
        }

        if(!(o instanceof PermissionEntity)){
            return false;
        }

        PermissionEntity that = (PermissionEntity) o;

        return name == that.name;
    }

    @Override
    public int hashCode(){
        return Objects.hash(name);
    }
    
}
