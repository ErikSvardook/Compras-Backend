package com.erikjarquin.compras.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.erikjarquin.compras.model.entity.CategoryEntity;
import com.erikjarquin.compras.model.entity.ProductEntity;

/**
 * Tests del repositorio de productos sobre H2 en memoria (no requiere Postgres).
 *
 * Cubre las consultas usadas por el modulo de ventas: barcode, filtro por
 * categoria y el buscador {q} (busqueda insensible a mayusculas sobre
 * nombre/sku/barcode utilizado por GET /api/local/products/search).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private CategoryEntity bebidas;

    @BeforeEach
    void seed() {
        bebidas = new CategoryEntity();
        bebidas.setName("BEBIDAS");
        categoryRepository.save(bebidas);

        productRepository.save(product("Coca Cola 3L", "7501055300", "CO-001", bebidas, 2000));
        productRepository.save(product("Pepsi 2L", "7501055301", "PE-001", bebidas, 1500));
    }

    private ProductEntity product(String name, String barcode, String sku,
                                  CategoryEntity category, int price) {
        ProductEntity p = new ProductEntity();
        p.setName(name);
        p.setBarcode(barcode);
        p.setSku(sku);
        p.setCategory(category);
        p.setStock(10);
        p.setPrice(BigDecimal.valueOf(price));
        return p;
    }

    @Test
    void findByBarcode_devuelveProducto() {
        Optional<ProductEntity> result = productRepository.findByBarcode("7501055300");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Coca Cola 3L");
    }

    @Test
    void findByBarcode_barcodeInexistenteDevuelveVacio() {
        assertThat(productRepository.findByBarcode("000000")).isNotPresent();
    }

    @Test
    void findByCategory_Name_devuelveProductosDeLaCategoria() {
        List<ProductEntity> result = productRepository.findByCategory_Name("BEBIDAS");

        assertThat(result).hasSize(2);
    }

    @Test
    void search_esInsensibleAMayusculasYBuscaEnVariosCampos() {
        List<ProductEntity> porNombre = productRepository.search("coca");
        List<ProductEntity> porSku = productRepository.search("pe-0");
        List<ProductEntity> porBarcode = productRepository.search("7501055301");

        assertThat(porNombre).hasSize(1);
        assertThat(porNombre.get(0).getName()).isEqualTo("Coca Cola 3L");
        assertThat(porSku).hasSize(1);
        assertThat(porBarcode).hasSize(1);
    }

    @Test
    void search_sinResultadosDevuelveListaVacia() {
        assertThat(productRepository.search("inexistente")).isEmpty();
    }
}