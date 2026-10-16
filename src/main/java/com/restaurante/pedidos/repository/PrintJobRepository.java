package com.restaurante.pedidos.repository;

import com.restaurante.pedidos.domain.PrintJob;
import com.restaurante.pedidos.domain.PrintJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    List<PrintJob> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<PrintJob> findTop100ByOrderByCreatedAtDesc();

    List<PrintJob> findByStatusInOrderByCreatedAtAsc(List<PrintJobStatus> statuses);

    long countByStatusIn(List<PrintJobStatus> statuses);

    /** Busca un trabajo identico reciente para no duplicar por doble clic. */
    Optional<PrintJob> findFirstByDedupeKeyAndCreatedAtAfter(String dedupeKey, LocalDateTime after);

    boolean existsByOrderIdAndSourceVersionAndStatus(Long orderId, int sourceVersion, PrintJobStatus status);
}
