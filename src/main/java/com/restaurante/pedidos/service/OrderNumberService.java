package com.restaurante.pedidos.service;

import com.restaurante.pedidos.domain.OrderSequence;
import com.restaurante.pedidos.repository.OrderSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera identificadores de pedido secuenciales y persistentes con formato PED-001.
 *
 * <p>La numeracion no depende de un contador JavaScript ni de memoria del servidor:
 * se guarda en la tabla {@code order_sequence} y se incrementa dentro de una
 * transaccion con bloqueo pesimista de la fila, de modo que varios dispositivos no
 * obtienen el mismo numero. Los numeros no se reutilizan aunque el pedido se cancele.</p>
 */
@Service
public class OrderNumberService {

    private final OrderSequenceRepository repository;

    public OrderNumberService(OrderSequenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Reserva el siguiente numero de pedido. Debe ejecutarse dentro de la misma
     * transaccion que crea el pedido para que, si falla la creacion, el numero no se
     * consuma (ROLLBACK) o, si se confirma, quede persistido.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextOrderCode() {
        OrderSequence sequence = repository.findByIdForUpdate(OrderSequence.SINGLETON_ID)
                .orElseGet(() -> repository.save(new OrderSequence(OrderSequence.SINGLETON_ID)));
        long next = sequence.getLastNumber() + 1;
        sequence.setLastNumber(next);
        repository.save(sequence);
        return format(next);
    }

    public static String format(long number) {
        return String.format("PED-%03d", number);
    }

    @Transactional(readOnly = true)
    public long currentLastNumber() {
        return repository.findById(OrderSequence.SINGLETON_ID)
                .map(OrderSequence::getLastNumber)
                .orElse(0L);
    }
}
