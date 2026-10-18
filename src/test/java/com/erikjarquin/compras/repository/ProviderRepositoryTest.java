package com.erikjarquin.compras.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.erikjarquin.compras.model.entity.ProductEntity;
import com.erikjarquin.compras.model.entity.ProviderEntity;
import com.erikjarquin.compras.model.entity.PurchaseDetailEntity;
import com.erikjarquin.compras.model.entity.PurchaseEntity;

/**
 * Tests de los repositorios del m\u00f3dulo de COMPRAS sobre H2 en memoria.
 *
 * Cubre lo que usa el servicio: RFC \u00fanico (findByRfc), listado
 * alfab\u00e9tico de proveedores, compras por proveedor con detalles precargados
 * (@EntityGraph) y el conteo que bloquea borrar un proveedor con compras (409).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class ProviderRepositoryTest {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private ProductRepository productRepository;

    private ProviderEntity proveedorZulu;
    private ProviderEntity proveedorAlfa;

    @BeforeEach
    void seed(){
        proveedorAlfa = saveProvider("Proveedor Alfa", "ALF-001");
        proveedorZulu = saveProvider("Proveedor Zulu", "ZUL-001");
    }

    private ProviderEntity saveProvider(String name, String rfc){
        ProviderEntity provider = new ProviderEntity();
        provider.setName(name);
        provider.setRfc(rfc);
        provider.setPhone("555-0000");
        provider.setEmail(name.toLowerCase().replace(" ", "") + "@mail.com");
        return providerRepository.save(provider);
    }

    @Test
    void findByRfc_devuelveProveedor(){
        Optional<ProviderEntity> result = providerRepository.findByRfc("ALF-001");

        assertThat(result).isPresent();
        assertThat(result.get().getRfc()).isEqualTo("ALF-001");
    }

    @Test
    void findByRfc_rfcInexistenteDevuelveVacio(){
        assertThat(providerRepository.findByRfc("XXX-999")).isNotPresent();
    }

    @Test
    void findAllByOrderByNameAsc_ordenaAlfabeticamente(){
        List<ProviderEntity> result = providerRepository.findAllByOrderByNameAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Proveedor Alfa");
        assertThat(result.get(1).getName()).isEqualTo("Proveedor Zulu");
    }

    @Test
    void countByProvider_Id_devuelveCeroSinCompras(){
        assertThat(purchaseRepository.countByProvider_Id(proveedorAlfa.getId())).isZero();
    }

    @Test
    void countByProvider_Id_cuentaLasComprasDelProveedor(){
        ProductEntity producto = new ProductEntity();
        producto.setName("Café 1kg");
        producto.setPrice(new BigDecimal("150"));
        producto.setStock(10);
        producto.setSku("CAF-01");
        producto.setBarcode("1111");
        producto = productRepository.save(producto);

        purchaseRepository.save(purchase(proveedorAlfa, producto));

        assertThat(purchaseRepository.countByProvider_Id(proveedorAlfa.getId())).isEqualTo(1);
        assertThat(purchaseRepository.countByProvider_Id(proveedorZulu.getId())).isZero();
    }

    @Test
    void findByProviderId_precargaDetallesYProducto(){
        ProductEntity producto = new ProductEntity();
        producto.setName("Azúcar 5kg");
        producto.setPrice(new BigDecimal("95"));
        producto.setStock(20);
        producto.setSku("AZU-01");
        producto.setBarcode("2222");
        producto = productRepository.save(producto);

        purchaseRepository.save(purchase(proveedorAlfa, producto));

        List<PurchaseEntity> compras = purchaseRepository
                .findByProviderIdOrderByPurchaseDateDesc(proveedorAlfa.getId());

        //Solo las del proveedor pedido
        assertThat(compras).hasSize(1);
        assertThat(compras.get(0).getProvider().getName()).isEqualTo("Proveedor Alfa");

        //EntityGraph: los detalles vienen ya cargados (se evita N+1)
        assertThat(compras.get(0).getDetails()).isNotEmpty();
        assertThat(compras.get(0).getDetails().get(0).getProduct().getName())
                .isEqualTo("Azúcar 5kg");
    }

    //Armar una compra de un solo renglón (helper de los tests)
    private PurchaseEntity purchase(ProviderEntity provider, ProductEntity producto){
        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setProvider(provider);
        purchase.setPurchaseDate(java.time.LocalDateTime.now());

        PurchaseDetailEntity detail = new PurchaseDetailEntity();
        detail.setPurchase(purchase);
        detail.setProduct(producto);
        detail.setQuantity(3);
        detail.setUnitCost(new BigDecimal("80"));
        detail.setSubtotal(new BigDecimal("240"));

        purchase.setDetails(List.of(detail));
        purchase.setTotal(new BigDecimal("240"));
        return purchase;
    }
}