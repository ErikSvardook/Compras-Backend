package com.erikjarquin.compras.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.erikjarquin.compras.mapper.SaleMapper;
import com.erikjarquin.compras.model.dto.Sale.SaleItemRequest;
import com.erikjarquin.compras.model.dto.Sale.SaleRequest;
import com.erikjarquin.compras.model.entity.CashRegisterEntity;
import com.erikjarquin.compras.model.entity.ProductEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.model.entity.UserEntity;
import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.repository.CashRegisterRepository;
import com.erikjarquin.compras.repository.ProductRepository;
import com.erikjarquin.compras.repository.SaleRepository;
import com.erikjarquin.compras.repository.UserRepository;

/**
 * Tests de QUIÉN queda como dueño de una venta.
 *
 * Este archivo existe por un bug real. La primera versión de
 * {@code currentUserOrNull()} hacía:
 *
 *   if(!(auth.getPrincipal() instanceof UserDetails details)){
 *       return null;
 *   }
 *
 * Suena correcto, pero en ESTA API el principal no es un
 * {@code UserDetails}: {@code JwtFilter} autentica con
 * {@code new UsernamePasswordAuthenticationToken(userEntity, null, autoridades)},
 * o sea que el principal es un {@code UserEntity}. Como
 * {@code UserEntity} no implementa {@code UserDetails}, el {@code instanceof}
 * NUNCA era cierto y todas las ventas se guardaban sin usuario.
 *
 * Y no daba ningún error: el código era válido, compilaba, y la columna
 * {@code user_id} aceptaba {@code NULL} sin problema. El síntoma era solo
 * "Sin usuario" en el reporte. Estos tests existen para que ese descuido no
 * vuelva a colarse: son los que fallarían si alguien "simplifica" el método.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Venta - usuario que la registra (V5)")
class SaleImplUserTest {

    @Mock private ProductRepository productRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private CashRegisterRepository cashRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.erikjarquin.compras.service.PaymentService paymentService;

    @Spy private SaleMapper mapper = new SaleMapper();

    @InjectMocks private SaleImpl saleService;

    /**
     * Mapper real envuelto en {@code @Spy}, por la misma razón que en
     * {@code SaleImplLifecycleTest}: {@code SaleImpl} se construye por
     * constructor, y sin {@code @Spy} Mockito le inyectaría {@code null}.
     */
    @AfterEach
    void limpiarContexto(){
        //Sin esto, el contexto de seguridad se filtra al siguiente test y una
        //prueba puede pasar por accidente porque "heredó" un usuario.
        SecurityContextHolder.clearContext();
    }

    //===== Helpers =====

    private UserEntity usuario(long id, String nombre){
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setName(nombre);
        u.setEmail(nombre.toLowerCase() + "@test.com");
        u.setActive(true);
        return u;
    }

    /**
     * Autentica EXACTAMENTE como lo hace {@code JwtFilter}: el principal es la
     * entidad, no un {@code UserDetails}.
     *
     * Si este helper usara {@code UserDetails}, el test pasaría por la rama
     * del respaldo y no detectaría el bug del {@code instanceof} equivocado. Por
     * eso replica el constructor real del token.
     */
    private void autenticarComo(UserEntity usuario){
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(usuario, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CashRegisterEntity cajaAbierta(){
        CashRegisterEntity cash = new CashRegisterEntity();
        cash.setId(1L);
        cash.setNumber("CAJA 1");
        cash.setActive(true);
        cash.setTotalTickets(0);
        return cash;
    }

    private ProductEntity producto(long id){
        ProductEntity p = new ProductEntity();
        p.setId(id);
        p.setName("Producto " + id);
        p.setPrice(new BigDecimal("50"));
        p.setStock(100);
        return p;
    }

    /**
     * Venta mínima válida: un producto en efectivo.
     *
     * Se paga en efectivo a propósito: el camino de tarjeta delega en
     * {@code PaymentService} y necesita un {@code CardPaymentRequest} completo,
     * que no es lo que se quiere probar acá.
     */
    private SaleRequest ventaDe(){
        SaleRequest request = new SaleRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setCashReceived(new BigDecimal("50"));

        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(1L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        return request;
    }

    //===== Tests =====

    @Nested
    @DisplayName("Con usuario en sesión")
    class ConUsuarioEnSesion {

        @Test
        @DisplayName("la venta guarda el usuario que está en sesión")
        void guardaElUsuarioDeLaSesion(){
            UserEntity admin = usuario(7L, "Admin");

            autenticarComo(admin);

            when(cashRepository.findByActiveTrue()).thenReturn(java.util.Optional.of(cajaAbierta()));
            when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(producto(1L)));
            when(saleRepository.save(any(SaleEntity.class))).thenAnswer(i -> i.getArgument(0));

            saleService.processSale(ventaDe());

            ArgumentCaptor<SaleEntity> captor = ArgumentCaptor.forClass(SaleEntity.class);
            org.mockito.Mockito.verify(saleRepository, org.mockito.Mockito.atLeastOnce())
                    .save(captor.capture());

            SaleEntity guardada = captor.getAllValues().get(captor.getAllValues().size() - 1);

            //Este es EL assert que faltaba cuando el bug estaba vivo.
            assertThat(guardada.getUser())
                    .as("la venta debe guardar el usuario de la sesión")
                    .isNotNull();
            assertThat(guardada.getUser().getId()).isEqualTo(7L);
            assertThat(guardada.getUser().getName()).isEqualTo("Admin");
        }

        @Test
        @DisplayName("NO consulta al usuario en la base: ya viene en el principal")
        void noConsultaLaBase(){
            autenticarComo(usuario(7L, "Admin"));

            when(cashRepository.findByActiveTrue()).thenReturn(java.util.Optional.of(cajaAbierta()));
            when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(producto(1L)));
            when(saleRepository.save(any(SaleEntity.class))).thenAnswer(i -> i.getArgument(0));

            saleService.processSale(ventaDe());

            //La JwtFilter ya cargó el usuario completo (rol y permisos). Consultarlo
            //otra vez sería un SELECT extra en cada venta, sin ganar nada.
            org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never())
                    .findByEmail(any());
        }
    }

    @Nested
    @DisplayName("Sin usuario en sesión")
    class SinUsuarioEnSesion {

        @Test
        @DisplayName("la venta se guarda SIN usuario, sin fallar")
        void guardaSinUsuarioNoFalla(){
            when(cashRepository.findByActiveTrue()).thenReturn(java.util.Optional.of(cajaAbierta()));
            when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(producto(1L)));
            when(saleRepository.save(any(SaleEntity.class))).thenAnswer(i -> i.getArgument(0));

            // Sin autenticar: es lo que pasa en los tests de integración y en una
            // eventual llamada interna. NO debe lanzar excepción.
            saleService.processSale(ventaDe());

            ArgumentCaptor<SaleEntity> captor = ArgumentCaptor.forClass(SaleEntity.class);
            org.mockito.Mockito.verify(saleRepository, org.mockito.Mockito.atLeastOnce())
                    .save(captor.capture());

            SaleEntity guardada = captor.getAllValues().get(captor.getAllValues().size() - 1);

            // NULL es aceptable y es el mismo estado que las ventas anteriores a
            // V5. Lo que NO sería aceptable es que la venta se perdiera, así que
            // se comprueba que se guardó COMPLETA.
            //
            // OJO: no se comprueba `getId()` porque con el repositorio mockeado
            // nadie asigna la clave primaria (eso solo lo hace JPA al guardar de
            // verdad). Se verifica el total, que es lo que prueba que la venta se
            // construyó y se persistió entera.
            assertThat(guardada.getUser()).isNull();
            assertThat(guardada.getTotal())
                    .as("la venta debe guardarse aunque no haya usuario en sesión")
                    .isNotNull()
                    .isEqualByComparingTo("50");
        }

        @Test
        @DisplayName("con principal anónimo (String) tampoco falla")
        void principalAnonimoNoFalla(){
            // Spring pone el String "anonymousUser" cuando no hay token. No es un
            // usuario y no debe buscarse en la base.
            UsernamePasswordAuthenticationToken anon =
                    new UsernamePasswordAuthenticationToken(
                            "anonymousUser", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(anon);

            when(cashRepository.findByActiveTrue()).thenReturn(java.util.Optional.of(cajaAbierta()));
            when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(producto(1L)));
            when(saleRepository.save(any(SaleEntity.class))).thenAnswer(i -> i.getArgument(0));

            saleService.processSale(ventaDe());

            org.mockito.Mockito.verify(saleRepository, org.mockito.Mockito.atLeastOnce())
                    .save(any(SaleEntity.class));

            org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never())
                    .findByEmail(any());
        }
    }
}