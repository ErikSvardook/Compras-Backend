package com.erikjarquin.compras.model.dto.Cash;

import java.time.LocalDateTime;

/**
 * Respuesta de una CAJA FÍSICA, no de un corte.
 *
 * Es lo que muestran la tabla "Ver cajas" y el selector de "Abrir caja". Se
 * diferencia de {@link CashResponse} en que no tiene importes: una caja no
 * tiene ventas propias, las tienen sus SESIONES.
 *
 * Por eso los tres campos de enriquecimiento al final:
 *   {@code sessionsCount}: cuántos turnos ha tenido. Si es 0, nunca se abrió
 *       y por eso se puede dar de baja sin dejar historial huérfano.
 *   {@code inUse}: tiene un turno abierto ahora mismo. No se puede dar de
 *       baja ni abrir.
 *   {@code lastOpenedAt}: cuándo fue el último turno.
 */
public class CashBoxResponse {
    private Long id;
    private String number;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;

    /**
     * Cuántos turnos ha tenido esta caja.
     *
     * Es un {@code int} y no un {@code Long} a propósito: es un conteo que
     * siempre existe (si no hay sesiones, es 0). Con un {@code Long} el JSON
     * podría traer {@code null} y el frontend tendría que guardar contra
     * {@code null} una cuenta que nunca es nula.
     */
    private int sessionsCount;

    /** true si tiene algún turno abierto ahora mismo. */
    private boolean inUse;

    /** Último turno (fecha) o null si nunca se abrió. */
    private LocalDateTime lastOpenedAt;

    public CashBoxResponse(){}

    public Long getId(){
        return id;
    }

    public void setId(Long id){
        this.id=id;
    }

    public String getNumber(){
        return number;
    }

    public void setNumber(String number){
        this.number=number;
    }

    public String getDescription(){
        return description;
    }

    public void setDescription(String description){
        this.description=description;
    }

    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active=active;
    }

    public LocalDateTime getCreatedAt(){
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt){
        this.createdAt=createdAt;
    }

    public int getSessionsCount(){
        return sessionsCount;
    }

    public void setSessionsCount(int sessionsCount){
        this.sessionsCount=sessionsCount;
    }

    public boolean isInUse(){
        return inUse;
    }

    public void setInUse(boolean inUse){
        this.inUse=inUse;
    }

    public LocalDateTime getLastOpenedAt(){
        return lastOpenedAt;
    }

    public void setLastOpenedAt(LocalDateTime lastOpenedAt){
        this.lastOpenedAt=lastOpenedAt;
    }
}