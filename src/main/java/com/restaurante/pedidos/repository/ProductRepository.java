package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio de productos.
 *
 * <p>Se evita el {@code join fetch} multiple de colecciones (flavors y variants son
 * colecciones tipo "bag" y Hibernate no permite inicializarlas a la vez). Las
 * colecciones se cargan de forma perezosa dentro de la transaccion del servicio.</p>
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderBySortOrderAscIdAsc();

    List<Product> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<Product> findByCategoryIdAndActiveTrueOrderBySortOrderAscIdAsc(Long categoryId);

    long countByActiveTrue();
}
