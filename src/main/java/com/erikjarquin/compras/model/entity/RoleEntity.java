package com.erikjarquin.compras.model.entity;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entidad {@code roles}: agrupa usuarios y les otorga PERMISOS.
 *
 * Relación N:M con PermissionEntity (tabla role_permissions, con UK que
 * impide duplicar role+permiso). Los permisos se cargan EAGER porque
 * SecurityAuthorityMapper los necesita en cada request autenticado.
 * users se ignora en JSON para evitar ciclos de serialización.
 */
@Entity
@Table(name="roles")
public class RoleEntity {
    
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false, unique=true)
    private String name;

    @JsonIgnore
    @OneToMany(mappedBy = "role")
    private List<UserEntity> users = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"),
                inverseJoinColumns = @JoinColumn(name="permission_id"),
                uniqueConstraints = {@UniqueConstraint(name = "uk_role_permission",
                                                        columnNames = {"role_id", "permission_id"}
                                                    )
                                    }
                )   
    private Set<PermissionEntity> permissions = new HashSet<>();

    public RoleEntity(){}

    public RoleEntity(Long id, String name){
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

    //Getter y setter de name
    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    //Getter y setter de users
    public List<UserEntity> getUsers(){
        return users;
    }

    public void setUsers(List<UserEntity> users){
        this.users = users;
    }

    //Getter y setter de permissions
    public Set<PermissionEntity> getPermissions(){
        return permissions;
    } 

    public void setPermissions(Set<PermissionEntity> permissions){
        this.permissions=permissions;
    }

}
