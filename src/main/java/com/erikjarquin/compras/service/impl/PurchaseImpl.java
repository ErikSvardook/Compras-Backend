package com.erikjarquin.compras.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erikjarquin.compras.exceptions.PurchaseException;
import com.erikjarquin.compras.mapper.PurchaseMapper;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseDTO;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseItemRequest;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseRequest;
import com.erikjarquin.compras.model.entity.ProductEntity;
import com.erikjarquin.compras.model.entity.ProviderEntity;
import com.erikjarquin.compras.model.entity.PurchaseDetailEntity;
import com.erikjarquin.compras.model.entity.PurchaseEntity;
import com.erikjarquin.compras.repository.ProductRepository;
import com.erikjarquin.compras.repository.ProviderRepository;
import com.erikjarquin.compras.repository.PurchaseRepository;
import com.erikjarquin.compras.service.PurchaseService;

/**
 * Implementación del módulo de compras.
 *
 * Registrar ({@code create}): valida proveedor e items (400/409), arma
 * cabecera + detalles con subtotales y calcula el total. NO toca el
 * inventario: la compra nace PENDIENTE. Desde V3 el efecto en el almacén
 * vive en {@link #confirm(Long)}, no aquí.
 *
 * Confirmar ({@code confirm}): es la operación que SUMA el stock de
 * cada producto y guarda su costo real. A partir de ahí la compra queda
 * CONGELADA y ya no se puede cancelar.
 *
 * Cancelar ({@code cancel}): solo borra la compra, y solo si sigue
 * PENDIENTE. Como una compra pendiente nunca tocó el inventario, no hay nada
 * que revertir y el antiguo {@code Math.max(stock - qty, 0)} que escribía un
 * 0 falso en la BD y devolvía 200 OK ya no tiene dónde aparecer.
 *
 * Por qué el efecto está en confirmar y no en crear: confirmar es lo
 * que declara que la mercancía llegó de verdad al almacén. Una compra en
 * tránsito se registra sin mover el stock, y si el proveedor no la entrega se
 * borra sin dejar rastro. El estado terminal es lo que protege al inventario,
 * igual que en la venta.
 *
 * El costo del producto ({@code product.cost}) lo escribe CONFIRMAR y no se
 * revierte nunca: es el último costo de compra vigente.
 */
@Service
public class PurchaseImpl implements PurchaseService {
    private final PurchaseRepository purchaseRepository;
    private final ProviderRepository providerRepository;
    private final ProductRepository productRepository;

    public PurchaseImpl(
            PurchaseRepository purchaseRepository,
            ProviderRepository providerRepository,
            ProductRepository productRepository){
        this.purchaseRepository = purchaseRepository;
        this.providerRepository = providerRepository;
        this.productRepository = productRepository;
    }

    //Registrar una compra. Nace PENDIENTE: no suma stock ni cambia costo (V3).
    @Override
    @Transactional
    public PurchaseDTO create(PurchaseRequest request){
        validateRequest(request);

        ProviderEntity provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new PurchaseException("El proveedor indicado no existe", HttpStatus.CONFLICT));

        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setPurchaseDate(request.getPurchaseDate() != null
                ? request.getPurchaseDate()
                : java.time.LocalDateTime.now());
        purchase.setProvider(provider);

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseDetailEntity> details = new ArrayList<>();

        for(PurchaseItemRequest item : request.getItems()){
            ProductEntity product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new PurchaseException(
                        "El producto con id " + item.getProductId() + " no existe",
                        HttpStatus.CONFLICT));

            BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;

            //1) Renglón de compra con su subtotal (unitCost se copia del request)
            PurchaseDetailEntity detail = new PurchaseDetailEntity();
            detail.setPurchase(purchase);
            detail.setProduct(product);
            detail.setQuantity(item.getQuantity());
            detail.setUnitCost(unitCost);

            //Precio de VENTA del renglón. Se guarda AHORA (al registrar), no
            //al confirmar, para que la compra pendiente ya muestre el precio que
            //se aplicará. Sigue sin tocar product.price: eso pasa al confirmar.
            detail.setUnitPrice(item.getUnitPrice());

            detail.setSubtotal(unitCost.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, java.math.RoundingMode.HALF_UP));

            total = total.add(detail.getSubtotal());

            //2) Aquí YA NO se toca el inventario.
            //   El stock y el costo se aplican en CONFIRMAR (confirm()), que es
            //   el acto que declara que la mercancía llegó al almacén.
            //   Registrar no es confirmar: una compra en tránsito se anota sin
            //   mover el stock y, si no se entrega, se borra sin dejar rastro.

            details.add(detail);
        }

        purchase.setTotal(total.setScale(2, java.math.RoundingMode.HALF_UP));
        purchase.setDetails(details);

        return PurchaseMapper.toDto(purchaseRepository.save(purchase));
    }

    //Listar todas las compras (EntityGraph precarga proveedor y productos)
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDTO> getAll(){
        return purchaseRepository.findAllByOrderByPurchaseDateDesc().stream()
                .map(PurchaseMapper::toDto)
                .toList();
    }

    //Compras de un proveedor
    @Override
    @Transactional(readOnly = true)
    public List<PurchaseDTO> getByProvider(Long providerId){
        if(providerId != null){
            providerRepository.findById(providerId)
                    .orElseThrow(() -> new PurchaseException("El proveedor indicado no existe", HttpStatus.NOT_FOUND));
        }

        return purchaseRepository.findByProviderIdOrderByPurchaseDateDesc(providerId).stream()
                .map(PurchaseMapper::toDto)
                .toList();
    }

    //Detalle de una compra
    @Override
    @Transactional(readOnly = true)
    public PurchaseDTO getById(Long id){
        PurchaseEntity entity = purchaseRepository.findDetailedById(id)
                .orElseThrow(() -> new PurchaseException("Compra no encontrada"));
        return PurchaseMapper.toDto(entity);
    }

    /**
     * CANCELAR una compra: la borra, y solo si sigue PENDIENTE.
     *
     * Por qué ahora es tan simple
     *
     * Crear una compra ya sumaba el stock, así que cancelar tenía
     * que deshacer esa suma y el método era la parte más delicada del
     * módulo (comprar 10 a vender 5 a cancelar) escribía un
     * {@code Math.max(stock - qty, 0)} = 0 falso con 5 unidades ya
     * vendidas, y devolvía 200 OK. La defensa era validar renglón por renglón y
     * abortar con 409 sin escribir nada.
     *
     * Desde V3 esa situación no es representable: mientras la compra
     * está pendiente nunca tocó el inventario, así que borrarla no deja nada que
     * revertir. El {@code Math.max} ya no tiene dónde aparecer porque el bug
     * que lo motivaba está estructuralmente eliminado, no escondido detrás de
     * una validación.
     *
     * Una compra CONFIRMADA ya suma stock y define el costo vigente, y es
     * evidencia contable de una entrega real: se rechaza con
     * 409 CONFLICT y no se borra, igual que una venta confirmada.
     */
    @Override
    @Transactional
    public void cancel(Long id){
        PurchaseEntity purchase = purchaseRepository.findDetailedById(id)
                .orElseThrow(() -> new PurchaseException("Compra no encontrada", HttpStatus.NOT_FOUND));

        if(purchase.isConfirmed()){
            throw new PurchaseException(
                "La compra ya está confirmada: su mercadería entró al almacén y su costo "
                + "vigente. Una compra confirmada es evidencia de una entrega real y no se "
                + "borra. Si hubo una entrega incorrecta, registre una devolución en el "
                + "módulo de compras.",
                HttpStatus.CONFLICT);
        }

        //Pendiente = nunca tocó stock ni costo, así que borrar es suficiente.
        //Los detalles van en cascade + orphanRemoval: se van con la cabecera.
        purchaseRepository.delete(purchase);
    }

    /**
     * CONFIRMAR una compra: la mercancía llegó, y solo ahora entra al almacén.
     *
     * Aquí es donde se suma el stock de cada producto y se guarda
     * {@code product.cost} (el último costo de compra vigente). Desde este
     * punto la compra queda CONGELADA: {@link #cancel(Long)} la rechazará con
     * 409.
     *
     * Idempotente a propósito
     *
     * Confirmar una compra ya confirmada devuelve 200 OK sin volver
     * a sumar. Motivo: si dos cajas confirman el mismo clic, o el usuario
     * reintenta tras un corte de red, un 409 sería confuso ("¿no la acabo de
     * confirmar?"). Lo que no se puede es sumar dos veces, y el
     * {@code if(!purchase.isConfirmed())} lo garantiza: la segunda pasada
     * entra, ve que ya está confirmada y no toca ningún stock. Es el mismo
     * criterio que {@code confirm()} de la venta en V2.
     *
     * Debilidad conocida: dos peticiones simultáneas podrían pasar
     * ambas el {@code if} y devolver el stock dos veces, porque aquí no hay
     * bloqueo pesimista. El arreglo futuro es
     * {@code @Lock(PESSIMISTIC_WRITE)} sobre la lectura de la compra (o un
     * {@code UPDATE ... WHERE confirmed = false} que devuelva las filas
     * afectadas y sirva de candado atómico). Está anotado como pendiente en
     * AGENTS.md, igual que para las ventas.
     */
    @Override
    @Transactional
    public PurchaseDTO confirm(Long id){
        //Un solo timestamp para la BD y para la entidad en memoria: si se sacara
        //en dos momentos distintos, el confirmedAt que se guardó y el que ve el
        //usuario podrían diferir en milisegundos y no coincidir con el histórico.
        java.time.LocalDateTime cuando = java.time.LocalDateTime.now();

        // ===== PASO 1: ganar la carrera, de forma atómica =====
        //
        //Este UPDATE condicional es lo que garantiza que el stock se sume UNA sola
        //vez. Devuelve 1 si esta peticion fue la que confirmo, o 0 si otra se
        //adelantó. Es atómico: la condición (confirmed = false) y la escritura
        //(confirmed = true) se evalúan juntas, así que dos peticiones simultáneas no
        //pueden pasar las dos.
        //
        //Va ANTES de tocar stock y NO con un simple if(entity.isConfirmed()) porque
        //ese if lee la fila y decide en Java: con dos peticiones simultaneas las
        //dos leen false antes de que ninguna escriba, y las dos suman el stock.
        int filasAfectadas = purchaseRepository.markConfirmedIfPending(id, cuando);

        //Ya confirmada (o la confirmó otra petición): 200 idempotente y CERO
        //escrituras. No se duplica el stock.
        if(filasAfectadas == 0){
            PurchaseEntity yaConfirmada = purchaseRepository.findDetailedById(id)
                    .orElseThrow(() -> new PurchaseException("Compra no encontrada", HttpStatus.NOT_FOUND));
            return PurchaseMapper.toDto(yaConfirmada);
        }

        //Esta petición ganó la carrera: ahora sí se aplica el inventario.
        //
        //Se refleja el UPDATE sobre la entidad en memoria para devolver un DTO
        //correcto. Sin esto habría que volver a leer de la base, y esa segunda
        //lectura es un gasto evitable: el repositorio ya-nos dijo que esta
        //petición ganó, así que el estado final es conocido sin volver a preguntar.
        PurchaseEntity purchase = purchaseRepository.findDetailedById(id)
                .orElseThrow(() -> new PurchaseException("Compra no encontrada", HttpStatus.NOT_FOUND));
        purchase.setConfirmed(true);
        purchase.setConfirmedAt(cuando);

        if(purchase.getDetails() != null){
            for(PurchaseDetailEntity detail : purchase.getDetails()){
                ProductEntity product = productRepository.findById(detail.getProduct().getId())
                        .orElseThrow(() -> new PurchaseException(
                            "El producto asociado a la compra ya no existe; no se puede confirmar",
                            HttpStatus.CONFLICT));

                //1) Entra la mercancía al almacén.
                product.setStock(product.getStock() + detail.getQuantity());

                //2) COSTO: lo que se le PAGA al proveedor. Solo este módulo lo
                //   escribe, porque solo él sabe lo que se pagó. Alimenta
                //   `GET /api/local/reports/margins` (margen = price - cost) y,
                //   desde V3, la utilidad de cada venta via sale_details.unitCost.
                product.setCost(detail.getUnitCost());

                //3) PRECIO DE VENTA: CONFIRMAR también
                //   actualiza product.price con lo que dice el renglón. Hasta V2
                //   este módulo NUNCA escribía price, porque se consideraba
                //   "decisión comercial"; el dueño aclaró el flujo real: al crear
                //   el producto da su precio, y al reponer stock escribe el
                //   precio de venta de ese lote (puede ser el mismo o subirse).
                //
                //   NULL = "esta compra no opina sobre el precio": se deja el que
                //   ya tiene. Por eso el campo es nullable y opcional, y no un 0
                //   (un 0 dejaría el producto gratis).
                if(detail.getUnitPrice() != null){
                    product.setPrice(detail.getUnitPrice());
                }

                productRepository.save(product);
            }
        }

        //El flag confirmed ya quedó escrito en la BD por el UPDATE condicional del
        //paso 1 (y reflejado en la entidad arriba), así que no se vuelve a hacer
        //save(purchase): sería una segunda escritura inútil de la cabecera.
        return PurchaseMapper.toDto(purchase);
    }

    //Validar la petición: proveedor y items obligatorios, cantidades y costos válidos (400)
    private void validateRequest(PurchaseRequest request){
        if(request.getProviderId() == null){
            throw new IllegalArgumentException("El proveedor es obligatorio");
        }
        if(request.getItems() == null || request.getItems().isEmpty()){
            throw new IllegalArgumentException("La compra debe tener al menos un artículo");
        }
        for(PurchaseItemRequest item : request.getItems()){
            if(item.getProductId() == null){
                throw new IllegalArgumentException("Cada artículo debe indicar un producto");
            }
            if(item.getQuantity() == null || item.getQuantity() <= 0){
                throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
            }
            if(item.getUnitCost() != null && item.getUnitCost().signum() < 0){
                throw new IllegalArgumentException("El costo unitario no puede ser negativo");
            }
        }
    }
}