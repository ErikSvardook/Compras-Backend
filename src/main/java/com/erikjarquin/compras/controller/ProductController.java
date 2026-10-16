package com.erikjarquin.compras.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.erikjarquin.compras.model.dto.Products.ProductDto;
import com.erikjarquin.compras.service.ProductService;
import com.erikjarquin.compras.util.InputValidator;

/**
 * CRUD de productos (multipart para la imagen) y búsquedas.
 *
 * Permisos requeridos: VER_PRODUCTOS (listar/buscar), CREAR_PRODUCTOS,
 * EDITAR_PRODUCTOS, ELIMINAR_PRODUCTOS (borrado real, solo sin histórico),
 * DESACTIVAR_PRODUCTOS y ACTIVAR_PRODUCTOS (borrado/alta lógica).
 *
 * Nota CORS: el origen permitido se define globalmente en SecurityConfig
 * (propiedad ${CORS_ALLOWED_ORIGINS}); por eso aquí ya NO hay @CrossOrigin.
 */
@RestController
@RequestMapping("/api/local/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service){
        this.service = service;
    }

    //Ver productos
    @PreAuthorize("hasAuthority('VER_PRODUCTOS')")
    @GetMapping
    public List<ProductDto> getProducts(@RequestParam(required = false) String category){ 
        if (category != null) {
            return service.getByCategory(category);
        }
        return service.getAll();
    }
    
    /**
     * Crear un nuevo producto.
     *
     * price y stock llegan como String y no como BigDecimal/int. A
     * propósito: para poder rechazar "1.875", "000.2" o "1e5" hay que mirar el
     * TEXTO que escribió el usuario. Si Spring lo convirtiese primero, "000.2"
     * ya habría llegado como {@code 0.2} y el cero a la izquierda se habría
     * perdido. Convertir aquí y devolver un 400 con el motivo exacto es lo que
     * hace que el error se vea y no se acepte en silencio.
     */
    @PreAuthorize("hasAuthority('CREAR_PRODUCTOS')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductDto saveProduct(
        @RequestParam("name") String name,
        @RequestParam("price") String price,
        @RequestParam("stock") String stock,
        @RequestParam("categoryId") Long categoryId,
        @RequestParam("sku") String sku,
        @RequestParam("barcode") String barcode,
        @RequestParam(value = "image", required = false) MultipartFile image
    ){
        BigDecimal precio = InputValidator.precio(price, "precio");
        Integer existencias = InputValidator.entero(stock, "stock");

        if(existencias == null){
            throw new IllegalArgumentException("El stock es obligatorio");
        }

        return service.save(
            name,
            precio,
            existencias,
            categoryId,
            InputValidator.sku(sku),
            InputValidator.barcode(barcode),
            image);
    }

    //Editar un producto (mismas validaciones que al crear: ver el javadoc de saveProduct)
    @PreAuthorize("hasAuthority('EDITAR_PRODUCTOS')")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductDto updateProduct(
        @PathVariable Long id,
        @RequestParam String name,
        @RequestParam String price,
        @RequestParam String stock,
        @RequestParam Long categoryId,
        @RequestParam String sku,
        @RequestParam String barcode,
        @RequestParam(value = "image", required = false) MultipartFile image
    ){
        BigDecimal precio = InputValidator.precio(price, "precio");
        Integer existencias = InputValidator.entero(stock, "stock");

        if(existencias == null){
            throw new IllegalArgumentException("El stock es obligatorio");
        }

        return service.update(
            id,
            name,
            precio,
            existencias,
            categoryId,
            InputValidator.sku(sku),
            InputValidator.barcode(barcode),
            image);
    }

    //Eliminar producto con el ID
    @PreAuthorize("hasAuthority('ELIMINAR_PRODUCTOS')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    //Listar los productos dados de baja (borrado lógico)
    @PreAuthorize("hasAuthority('VER_PRODUCTOS')")
    @GetMapping("/inactive")
    public List<ProductDto> getInactiveProducts(){
        return service.getInactive();
    }

    //Dar de baja un producto (soft delete: active=false, se conserva el histórico)
    @PreAuthorize("hasAuthority('DESACTIVAR_PRODUCTOS')")
    @PatchMapping("/{id}/deactivate")
    public ProductDto deactivateProduct(@PathVariable Long id){
        return service.deactivate(id);
    }

    //Reactivar un producto dado de baja (active=true)
    @PreAuthorize("hasAuthority('ACTIVAR_PRODUCTOS')")
    @PatchMapping("/{id}/active")
    public ProductDto activateProduct(@PathVariable Long id){
        return service.activate(id);
    }

    //Buscar productos con el código de barras
    @PreAuthorize("hasAuthority('VER_PRODUCTOS')")
    @GetMapping("/barcode/{barcode}")
    public ProductDto findByBarcode(@PathVariable String barcode){
        return service.findByBarcode(barcode);
    }

    //Buscar productos por nombre
    @PreAuthorize("hasAuthority('VER_PRODUCTOS')")
    @GetMapping("/search")
    public List<ProductDto> search(@RequestParam String q){
        return service.search(q);
    }
}
