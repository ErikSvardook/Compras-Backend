package com.erikjarquin.compras.model.dto.User;


public class UpdateUserRequest {
    private String name;
    private String email;
    private Long roleId;
    private boolean active;

    /**
     * Campo trampa: No se usa para cambiar la contraseña
     * 
     * Existe solo para rechazar un "password" que llegue al body, en vez
     * de ignorarlo en silencio. Sin este campo Jackson descarta la propiedad
     * desconocida y la petición responde 200: el cliente cree que cambio la contraseña
     * y no cambio nada.
     */
    private String password;

    //Getter y setter
    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    //Getter y setter
    public String getEmail(){
        return email;
    }

    public void setEmail(String email){
        this.email=email;
    }

    //Getter y setter
    public Long getRoleId(){
        return roleId;
    }

    public void setRoleId(Long roleId){
        this.roleId=roleId;
    }

    //Getter y setter
    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active=active;
    }

    //Getter y setter de password
    public String getPassword(){
        return password;
    }

    public void setPassword(String password){
        this.password=password;
    }
}
