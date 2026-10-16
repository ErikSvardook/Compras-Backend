package com.erikjarquin.compras.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erikjarquin.compras.model.dto.Purchases.ProviderDto;
import com.erikjarquin.compras.service.ProviderService;

/**
 * CRUD de PROVEEDORES (quién nos vende la mercancía de las compras).
 *
 * Público dentro del sistema (autenticado), protegido por permiso:
 *  - VER_PROVEEDORES      -> GET (listado y detalle)
 *  - CREAR_PROVEEDORES    -> POST
 *  - EDITAR_PROVEEDORES   -> PUT /{id}
 *  - ELIMINAR_PROVEEDORES -> DELETE /{id}
 */
@RestController
@RequestMapping("/api/local/providers")
public class ProviderController {
    private final ProviderService providerService;

    public ProviderController(ProviderService providerService){
        this.providerService = providerService;
    }

    //Listado alfabético (para selects y tabla)
    @PreAuthorize("hasAuthority('VER_PROVEEDORES')")
    @GetMapping
    public List<ProviderDto> getAll(){
        return providerService.getAll();
    }

    //Detalle de un proveedor
    @PreAuthorize("hasAuthority('VER_PROVEEDORES')")
    @GetMapping("/{id}")
    public ProviderDto getById(@PathVariable Long id){
        return providerService.getById(id);
    }

    //Crear proveedor
    @PreAuthorize("hasAuthority('CREAR_PROVEEDORES')")
    @PostMapping
    public ResponseEntity<ProviderDto> create(@RequestBody ProviderDto dto){
        return ResponseEntity.status(HttpStatus.CREATED).body(providerService.save(dto));
    }

    //Editar proveedor
    @PreAuthorize("hasAuthority('EDITAR_PROVEEDORES')")
    @PutMapping("/{id}")
    public ProviderDto update(@PathVariable Long id, @RequestBody ProviderDto dto){
        return providerService.update(id, dto);
    }

    //Borrar proveedor (409 si tiene compras)
    @PreAuthorize("hasAuthority('ELIMINAR_PROVEEDORES')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        providerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}