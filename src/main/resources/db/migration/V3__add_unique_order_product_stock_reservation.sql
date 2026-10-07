WITH active_reservations AS (
    SELECT id,
           FIRST_VALUE(id) OVER (PARTITION BY order_id, product_id ORDER BY id) AS keeper_id,
           SUM(quantity) OVER (PARTITION BY order_id, product_id) AS total_quantity
    FROM tb_stock_reservation
    WHERE status = 'RESERVED'
),
keeper_quantities AS (
    SELECT DISTINCT keeper_id, total_quantity
    FROM active_reservations
)
UPDATE tb_stock_reservation reservation
SET quantity = keeper_quantities.total_quantity::INTEGER
FROM keeper_quantities
WHERE reservation.id = keeper_quantities.keeper_id
  AND reservation.quantity <> keeper_quantities.total_quantity;

WITH active_reservations AS (
    SELECT id,
           FIRST_VALUE(id) OVER (PARTITION BY order_id, product_id ORDER BY id) AS keeper_id
    FROM tb_stock_reservation
    WHERE status = 'RESERVED'
)
UPDATE tb_stock_reservation reservation
SET status = 'RELEASED'
FROM active_reservations
WHERE reservation.id = active_reservations.id
  AND reservation.id <> active_reservations.keeper_id;

CREATE UNIQUE INDEX uk_stock_reservation_order_product_active
    ON tb_stock_reservation (order_id, product_id)
    WHERE status = 'RESERVED';
