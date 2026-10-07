package io.wlailson.github.e_commerce_catalog_service.repository;

import io.wlailson.github.e_commerce_catalog_service.domain.ReservationStatus;
import io.wlailson.github.e_commerce_catalog_service.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    @Query("""
    SELECT r
    FROM StockReservation r
    WHERE r.status = :status
      AND r.expiresAt <= :now
""")
    List<StockReservation> findExpiredReservations(
            @Param("now") Instant now,
            @Param("status") ReservationStatus status
    );

    List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    boolean existsByOrderId(Long orderId);
}
