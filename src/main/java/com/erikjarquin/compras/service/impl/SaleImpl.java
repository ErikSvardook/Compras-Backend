package com.erikjarquin.compras.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erikjarquin.compras.exceptions.PaymentException;
import com.erikjarquin.compras.exceptions.SaleException;
import com.erikjarquin.compras.mapper.SaleMapper;
import com.erikjarquin.compras.model.dto.Payment.CardPaymentRequest;
import com.erikjarquin.compras.model.dto.Payment.CardPaymentResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleDetailHistoryResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleHistoryResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleItemRequest;
import com.erikjarquin.compras.model.dto.Sale.SaleRequest;
import com.erikjarquin.compras.model.dto.Sale.SaleResponse;
import com.erikjarquin.compras.model.entity.CashRegisterEntity;
import com.erikjarquin.compras.model.entity.ProductEntity;
import com.erikjarquin.compras.model.entity.SaleDetailEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.model.entity.UserEntity;
import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;
import com.erikjarquin.compras.repository.CashRegisterRepository;
import com.erikjarquin.compras.repository.ProductRepository;
import com.erikjarquin.compras.repository.SaleRepository;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.PaymentService;
import com.erikjarquin.compras.service.SaleService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementación del flujo COMPLETO de una venta.
 *
 * Pasos (todo dentro de una transacción con rollback):
 *  1. Valida la solicitud y que exista una caja abierta.
 *  2. Crea la venta como PENDING (para obtener ID).
 *  3. Calcula el total en memoria validando productos y stock.
 *  4. Procesa el pago: efectivo (valida cambio) o tarjeta (delega en
 *     PaymentService / terminal).
 *  5. Solo si el pago fue exitoso: aprueba la venta y DESCUENTA stock.
 *
 * Si el pago con tarjeta falla, la excepción hace rollback total de la
 * transacción (la venta PENDING y el pago no quedan persistidos).
 */
@Slf4j
@Service
@Transactional
public class SaleImpl implements SaleService {
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRepository;
    private final UserRepository userRepository;
    private final SaleMapper mapper;
    private final PaymentService paymentService;

    public SaleImpl(
        ProductRepository productRepository,
        SaleRepository saleRepository,
        CashRegisterRepository cashRepository,
        UserRepository userRepository,
        SaleMapper mapper,
        PaymentService paymentService
    ){
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.cashRepository = cashRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.paymentService=paymentService;
    }

    @Override //
    @Transactional(rollbackFor = Exception.class) //
    public SaleResponse processSale(SaleRequest request){
        log.info("Iniciando proceso de venta. Método de pago: {}", request.getPaymentMethod());

        /*IDEMPOTENCIA (V6): si esta clave de cobro ya se procesó, se devuelve
          la venta que se hizo en su lugar. Es lo que evita que un doble Enter
          en el modal de pago cobre dos veces.*/
        SaleEntity ventaPrevia = buscarVentaPorClave(request.getIdempotencyKey());
        if(ventaPrevia != null){
            log.info("Venta {} ya procesada con la clave de idempotencia. Se devuelve sin cobrar de nuevo.",
                    ventaPrevia.getId());
            return mapper.toResponse(ventaPrevia);
        }

        /*VALIDACIONES INICIALES*/
        validateRequest(request);

        /*VERIFICAR CAJA ABIERTA*/
        CashRegisterEntity cash = getActiveCashRegister();

        /*CREAR VENTA (sin detalles todavía)*/
        SaleEntity sale = createBaseSale(request, cash);
        sale.setPaymentStatus(PaymentStatus.PENDING); //Estado inicial

        //Guardar a venta para obtener un ID
        SaleEntity savedSale = saleRepository.save(sale);
        log.info("Venta creada con ID: {} (estado PENDING)", savedSale.getId());

        /*PROCESAR PRODUCTOS Y CALCULAR TOTAL*/
        ProcessedProducts processed = processProductsInMemory(request.getItems(), sale);
        savedSale.setDetails(processed.getDetails());
        savedSale.setTotal(processed.getTotal());

        //Actualizar la venta con el total calculado
        saleRepository.save(savedSale);

        //Procesar pago según el método y guardar la respuesta
        CardPaymentResponse paymentResponse = null;

        if(request.getPaymentMethod() == PaymentMethod.CASH){
            //Procesar pago en efectivo
            processCashPayment(request, savedSale);
        } else if(request.getPaymentMethod() == PaymentMethod.DEBIT || request.getPaymentMethod() == PaymentMethod.CREDIT){
            //Procesar pago con tarjeta y guardar respuesta
            paymentResponse = processCardPaymentWithResponse(request, savedSale);
        } else {
            throw new SaleException("Método de pago no soportado: " + request.getPaymentMethod());
        }

        /*Solo si llegamos aquí, el pago fue exitoso*/
        // a) Guardar la venta
        savedSale.setPaymentStatus(PaymentStatus.APPROVED);

        // b) Descontar de stock
        for(SaleDetailEntity detail : savedSale.getDetails()){
            ProductEntity product = detail.getProduct();
            product.setStock(product.getStock() - detail.getQuantity());
            productRepository.save(product);
        }

        SaleEntity finalSale = saleRepository.save(savedSale);

        log.info("Venta completada exitosamente. ID: {}, Total: ${}", savedSale.getId(), savedSale.getTotal());

        /* RETORNAR RESPUESTA CON DATOS DE TARJETA SI APLICA */
        SaleResponse response = mapper.toResponse(finalSale);
        
        // Si fue pago con tarjeta, incluir los datos de la respuesta del pago
        if(paymentResponse != null){
            response.setCardPaymentResponse(paymentResponse);
            response.setAuthorizationCode(paymentResponse.getAuthorizationCode());
            response.setLastFourDigits(paymentResponse.getLastFourDigits());
        }
        
        return response;
    }

    // ====== Métodos privados =====
    //Validar el pago
    private void validateRequest(SaleRequest request){
        if(request == null){
            throw new SaleException("La solicitud de venta es obligatoria");        
        }

        if(request.getPaymentMethod() == null){
            throw new SaleException("Debe seleccionar un método de pago");
        }

        if(request.getItems() == null || request.getItems().isEmpty()){
            throw new SaleException("La venta no tiene productos");
        }
    }

    //Ver caja activa
    private CashRegisterEntity getActiveCashRegister(){
        return cashRepository.findByActiveTrue().orElseThrow(() -> new SaleException("No existe una caja abierta"));
    }

    //Crear la base de la venta
    private SaleEntity createBaseSale(SaleRequest request, CashRegisterEntity cash){
        SaleEntity sale = new SaleEntity();
        sale.setSaleDate(LocalDateTime.now());
        sale.setPaymentMethod(request.getPaymentMethod());
        sale.setCashRegister(cash);
        sale.setPaymentStatus(PaymentStatus.PENDING);
        sale.setUser(currentUserOrNull());
        //La clave de idempotencia viaja del cliente (V6). Sin ella la venta se
        //crea normal: es opcional a propósito, para no romper clientes viejos.
        sale.setIdempotencyKey(normalizarClave(request.getIdempotencyKey()));
        return sale;
    }

    /**
     * Busca una venta ya creada con esta clave de idempotencia.
     *
     * Devuelve {@code null} si la clave viene vacía o no existe, que es el
     * caso normal de una venta nueva.
     *
     * Por qué el método NO lleva {@code @Transactional}: la anotación
     * de la clase ya abre una transacción, y aquí solo hace falta una lectura
     * para decidir. Declararlo además sería redundante.
     */
    private SaleEntity buscarVentaPorClave(String clave){
        String limpia = normalizarClave(clave);

        if(limpia == null){
            return null;
        }

        return saleRepository.findByIdempotencyKey(limpia).orElse(null);
    }

    /**
     * Venta ya registrada con esa clave, para la red de seguridad del
     * controlador.
     *
     * Se declara {@code @Transactional(readOnly = true)} a PROPÓSITO, y es un
     * detalle importante: esta lectura se hace desde el
     * {@code @CatchAndRecoverStyle} del controlador, es decir, después de
     * que la transacción de la venta perdedora ya revirtió. Abrir aquí una
     * transacción nueva es lo que permite que la lectura sea válida: leer en la
     * transacción ya marcada como rollback-only lanzaría
     * {@code UnexpectedRollbackException}.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<SaleResponse> findByIdempotencyKey(String idempotencyKey){
        SaleEntity venta = buscarVentaPorClave(idempotencyKey);

        return Optional.ofNullable(venta).map(mapper::toResponse);
    }

    /**
     * Normaliza la clave de idempotencia.
     *
     * Devuelve {@code null} si no viene o viene en blanco, para que la
     * consulta no busque por una cadena vacía (que colisionaría con todas las
     * ventas que tampoco la tienen).
     *
     * Se recorta porque un salto de espacio de más convertiría la misma
     * intención de cobro en dos claves distintas, que es justo lo que la
     * idempotencia debe evitar.
     */
    private String normalizarClave(String clave){
        if(clave == null || clave.isBlank()){
            return null;
        }

        String limpia = clave.trim();

        //Cota de seguridad: si el cliente manda algo enorme, no tiene sentido
        //guardarlo. 64 es el VARCHAR(64) de la columna.
        return limpia.length() > 64 ? limpia.substring(0, 64) : limpia;
    }

    /**
     * Usuario autenticado que está haciendo la venta.
     *
     * Se lee del {@code SecurityContext}, que es donde Spring Security deja
     * al usuario del JWT ya validado. No se pasa por parámetro ni se pide el
     * email en el body: si el cliente pudiera mandar el usuario, cualquiera que
     * tenga un token podría registrar ventas a nombre de otro.
     *
     * El principal es un {@code UserEntity}, no un {@code UserDetails}.
     * Eso lo define {@code JwtFilter}, que autentica así:
     * {@code new UsernamePasswordAuthenticationToken(user, null, autoridades)}
     * con la entidad {@code UserEntity} completa (rol y permisos incluidos). Por
     * eso aquí se lee el principal como entidad y no se busca al usuario en
     * la base: ya está cargado, y volver a consultarlo sería un SELECT
     * por venta.
     *
     * Si no hay usuario autenticado devuelve {@code null} en vez de fallar.
     * Esto lo hace seguro para los tests de integración (que no levantan el
     * filtro de seguridad) y para una eventual llamada interna. El costo es que
     * una venta sin usuario queda con {@code user_id} nulo.
     */
    private UserEntity currentUserOrNull(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if(auth == null || !auth.isAuthenticated()){
            return null;
        }

        Object principal = auth.getPrincipal();

        // Spring pone "anonymousUser" (un String) cuando no hay token: no es un
        // usuario real y no debe buscarse.
        if(principal instanceof UserEntity user){
            return user;
        }

        //Respaldo por si algún día el filtro cambia a un UserDetails (por
        // ejemplo al migrar a Spring Security con un AuthenticationProvider). Con
        // esta rama el servicio sigue funcionando sin tocarlo.
        if(principal instanceof UserDetails details){
            return userRepository.findByEmail(details.getUsername()).orElse(null);
        }

        return null;
    }

    //Procesar pago con tarjeta
    private CardPaymentResponse processCardPaymentWithResponse(SaleRequest request, SaleEntity sale){
        log.info("Procesando pago con tarjeta para venta ID: {}", sale.getId());

        if(request.getCardPayment() == null){
            throw new SaleException("Debe proporcionar datos de la tarjeta");
        }

        try{
            // Preparar request para PaymentService
            CardPaymentRequest cardRequest = request.getCardPayment();
            cardRequest.setSaleId(sale.getId()); // Ya tiene ID porque guardamos antes
            cardRequest.setPaymentMethod(request.getPaymentMethod());

            // Procesar el pago con tarjeta
            CardPaymentResponse paymentResponse = paymentService.processCardPayment(cardRequest);

            // Verificar si fue aprobado
            if(paymentResponse.getStatus() == PaymentStatus.APPROVED){
                log.info("Pago con tarjeta aprobado. Código: {}", paymentResponse.getAuthorizationCode());
                return paymentResponse;
            } else {
                // Si no fue aprobado, marcar como rechazado y lanzar excepción
                sale.setPaymentStatus(PaymentStatus.REJECTED);
                saleRepository.save(sale);
                throw new PaymentException("Pago con tarjeta rechazado: " + paymentResponse.getMessage());
            }

        } catch (PaymentException e){
            log.error("Error en pago con tarjeta: {}", e.getMessage());
            // Marcar la venta como rechazada
            sale.setPaymentStatus(PaymentStatus.REJECTED);
            saleRepository.save(sale);
            throw e; // Relanzar para rollback
        } catch (Exception e){
            log.error("Error inesperado en pago con tarjeta: {}", e.getMessage());
            sale.setPaymentStatus(PaymentStatus.REJECTED);
            saleRepository.save(sale);
            throw new SaleException("Error al procesar pago con tarjeta: " + e.getMessage(), e);
        }
    }

    //No se guarda la venta en BD solo se calcula
    private ProcessedProducts processProductsInMemory(List<SaleItemRequest> items, SaleEntity sale){
        List<SaleDetailEntity> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for(SaleItemRequest item : items){
            validateItem(item);
            ProductEntity product = findProduct(item.getProductId());
            validateStock(product, item.getQuantity());

            //Solo calcular, no guardar
            BigDecimal subtotal = calculateSubtotal(product, item.getQuantity());
            total = total.add(subtotal);

            SaleDetailEntity detail = createDetail(sale, product, item, subtotal);
            details.add(detail);
        }

        return new ProcessedProducts(details, total);
    }

    //Se validan los productos
    private void validateItem(SaleItemRequest item){
        if(item == null){
            throw new SaleException("La venta contiene un producto inválido");
        }

        if(item.getProductId() == null){
            throw new SaleException("El producto es obligatorio");
        }

        if(item.getQuantity() == null || item.getQuantity() <= 0){
            throw new SaleException("La cantidad del producto debe ser mayor a 0");
        }
    }

    //Encontrar producto
    private ProductEntity findProduct(Long productId){
        return productRepository.findById(productId).orElseThrow(() -> new SaleException("Producto no encontrado: " + productId));
    }

    //Validar stock en el inventario
    private void validateStock(ProductEntity product, Integer quantity){
        if(product.getStock() < quantity){
            throw new SaleException("Stock insuficiente para el producto: " + product.getName() + 
            ". Disponible: " + product.getStock() + ", Solicitado: " + quantity);
        }
    }

    //Calcular el subtotal
    private BigDecimal calculateSubtotal(ProductEntity product, Integer quantity){
        return product.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    //Crear el detalle de la venta 
    private SaleDetailEntity createDetail(SaleEntity sale, ProductEntity product, SaleItemRequest item, BigDecimal subtotal){
        SaleDetailEntity detail = new SaleDetailEntity();
        detail.setSale(sale);
        detail.setProduct(product);
        detail.setQuantity(item.getQuantity());
        detail.setUnitPrice(product.getPrice());

        //Se congela el COSTO del producto al momento de vender. Sin esto,
        //la utilidad de esta venta habria que recalcularla con product.cost,
        //que es el costo del ULTIMO purchase de hoy: comprar algo mas barato
        //manana reescribia la ganancia de ayer. Mismo congelamiento que
        //unitPrice, pero del lado del costo.
        detail.setUnitCost(product.getCost());

        detail.setSubTotal(subtotal);
        return detail;
    }

    //Procesar el pago en efectivo
    private void processCashPayment(SaleRequest request, SaleEntity sale){
        log.info("Procesando pago con efectivo para venta ID: {}", sale.getId());

        if(request.getCashReceived() == null){
            throw new SaleException("Debe indicar el efectivo recibido");
        }

        if(request.getCashReceived().compareTo(BigDecimal.ZERO) <= 0){
            throw new SaleException("El efectivo recibido debe ser mayor a cero");
        }

        if(request.getCashReceived().compareTo(sale.getTotal()) <0){
            throw new SaleException("Pago insuficiente. Total: $" + sale.getTotal() + ", Recibido: $" + request.getCashReceived());
        }

        BigDecimal change = request.getCashReceived().subtract(sale.getTotal());
        sale.setCashReceived(request.getCashReceived());
        sale.setChangeAmount(change);
        sale.setPaymentStatus(PaymentStatus.APPROVED);

        log.info("Pago en efectivo aprobado. Cambio: {}", change);
    }

    //===== CLASE AUXILIAR =====
    private static class ProcessedProducts {
        private final List<SaleDetailEntity> details;
        private final BigDecimal total;

        public ProcessedProducts(List<SaleDetailEntity> details, BigDecimal total){
            this.details=details;
            this.total=total;
        }

        public List<SaleDetailEntity> getDetails(){
            return details;
        }

        public BigDecimal getTotal(){
            return total;
        }
    }

    //===== MÉTODOS DE CONSULTA ======
    //Consultar las ventas
    @Override
    @Transactional(readOnly = true)
    public List<SaleHistoryResponse> getSales(){
        return saleRepository.findAll().stream().map(mapper::toHistoryResponse).toList();
    }

    //Consultar la venta por ID
    @Override
    public SaleDetailHistoryResponse getSaleById(Long saleId){
        SaleEntity sale = findSaleOrThrow(saleId);

        return mapper.toDetailResponse(sale);
    }

    // ===== CICLO DE VIDA DE LA VENTA =====
    /**
     * Carga la venta o lanza 404. Extraido como helper porque confirm() y
     * cancel() empiezan exactamente igual: centralizar la búsqueda evita que
     * uno de los dos se olvide del orElseThrow.
     *
     * 404 y no 400: el recurso no existe, no hay nada que "corregir" en el
     * pedido. Un cliente que llama un id inexistente merece un 404 aunque antes
     * este método devolviera 400.
     */
    private SaleEntity findSaleOrThrow(Long saleId){
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleException(
                    "Venta no encontrada con ID: " + saleId, HttpStatus.NOT_FOUND));
    }

    /**
     * CONFIRMAR la venta: la congela definitivamente.
     *
     * Se ejecuta cuando el pedido ya salió del mostrador. Desde este punto el
     * stock descontado y el dinero cobrado son hechos reales, así que la venta
     * ya no se puede anular ni borrar. Es lo que hace imposible, en cascada, que
     * alguien intente "deshacer" una COMPRA cuyo stock ya se vendió.
     *
     * Idempotente por diseño: si ya estaba confirmada devuelve OK sin
     * volver a pisar la fecha. La UI puede sufrir doble clic y el usuario puede
     * reintentar tras un corte de red; un 409 ahí sería confuso ("¿ya la
     * confirmé o no?"). Confirmar dos veces no es un error, es el mismo estado
     * pedido dos veces.
     */
    @Override
    @Transactional
public SaleHistoryResponse confirm(Long saleId){
        SaleEntity sale = findSaleOrThrow(saleId);

        //Una venta anulada es terminal en la otra dirección: no se "revive".
        if(sale.isCancelled()){
            throw new SaleException(
                "No se puede confirmar una venta que ya fue anulada", HttpStatus.CONFLICT);
        }

        // ===== Ganar la carrera de forma atómica =====
        //El UPDATE condicional y la comprobación de arriba NO son redundantes: la
        //de arriba es una regla de negocio (no se revive una venta anulada) que se
        //lee en Java, y la de abajo es la garantía de concurrencia. El
        //"cancelled = false" del WHERE cubre también el caso en que otra
        //transacción anule entre la lectura y este UPDATE.
        int filasAfectadas = saleRepository.markConfirmedIfPending(saleId, LocalDateTime.now());

        //0 filas = ya estaba confirmada (doble clic, reintento, o carrera perdida).
        //No es un error: es idempotencia. Y lo importante es que NO se escribe nada.
        if(filasAfectadas == 0){
            log.info("Venta {} ya estaba confirmada: operación idempotente.", saleId);
            return mapper.toHistoryResponse(sale);
        }

        //Recarga para devolver el DTO con confirmed = true y confirmedAt real: el
        //flag ya quedó escrito por el UPDATE del repositorio.
        SaleEntity saved = saleRepository.findById(saleId).orElseThrow();

        log.info("Venta {} confirmada. Queda congelada: ya no se puede anular.", saleId);
        return mapper.toHistoryResponse(saved);
    }

    /**
     * ANULAR la venta y devolver el stock al inventario.
     *
     * Es la operación inversa de {@link #processSale}: por cada renglón suma
     * de vuelta la cantidad vendida. NO borra la fila, la deja con
     * {@code cancelled=true}: el historial de un POS es evidencia contable y un
     * contador que baja solo es un agujero de fraude. Para el negocio la venta
     * "no contó", y para la base de datos quedó registrado que pasó.
     *
     * Reglas, y el porqué de cada una:
     * 
     *   No se anula una venta CONFIRMADA -> 409. Es el congelamiento: el
     *       cliente ya se llevó la comida.
     *   No se anula dos veces -> 409. Si pasara, el stock volvería a subir
     *       dos veces y el inventario quedaría inflado.
     *   No se anula una venta con TARJETA -> 409. El dinero ya entró a la
     *       cuenta; "anular la venta" aquí no devolvería nada, solo mentiría el
     *       historial. Para ese caso existe el flujo de reversa real
     *       ({@code POST /api/local/payments/reverse/{id}}), que además exige su
     *       propia autorización. Una venta en EFECTIVO sí se puede anular,
     *       porque el efectivo se devuelve en mano y el estado de la venta es
     *       la única fuente de verdad.
     *
     * Devolución: se recorre {@code sale.getDetails()} (los
     * renglones ya guardados) y NO los items del request, porque en una
     * anulación no hay request: hay que deshacer exactamente lo que se hizo.
     * La cantidad a devolver es la del HISTÓRICO, no la que el cliente mande.
     */
    @Override
    @Transactional
    public SaleHistoryResponse cancel(Long saleId){
        SaleEntity sale = findSaleOrThrow(saleId);

        if(sale.isConfirmed()){
            throw new SaleException(
                "No se puede anular una venta ya confirmada: el pedido ya salió y el stock es real",
                HttpStatus.CONFLICT);
        }

        if(sale.isCancelled()){
            throw new SaleException("La venta ya estaba anulada", HttpStatus.CONFLICT);
        }

        if(sale.getPaymentMethod() != PaymentMethod.CASH){
            throw new SaleException(
                "Una venta con tarjeta no se anula desde aquí: el pago fue capturado y necesita una reversa real "
                + "en el módulo de pagos", HttpStatus.CONFLICT);
        }

        //Devolver el stock renglón por renglón.
        if(sale.getDetails() != null){
            for(SaleDetailEntity detail : sale.getDetails()){
                ProductEntity product = productRepository.findById(detail.getProduct().getId())
                        .orElseThrow(() -> new SaleException(
                            "El producto " + detail.getProduct().getName()
                            + " ya no existe; no se puede anular la venta",
                            HttpStatus.CONFLICT));

                //SUMA de vuelta. Sin Math.max aquí a propósito: en una anulación
                // legítima el stock siempre alcanza (o queda en 0) porque
                // validateStock() en processSale ya lo garantizó. Si no
                // alcanzara, el dato de la BD está corrupto, y enmascararlo con
                // max(...,0) solo escondería el problema.
                product.setStock(product.getStock() + detail.getQuantity());
                productRepository.save(product);
            }
        }

        sale.setCancelled(true);
        sale.setCancelledAt(LocalDateTime.now());
        SaleEntity saved = saleRepository.save(sale);

        log.info("Venta {} anulada. Stock devuelto al inventario.", saleId);
        return mapper.toHistoryResponse(saved);
    }
}
