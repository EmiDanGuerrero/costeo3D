-- pruebas.sql
-- Pruebas simples y no destructivas para costeo3d
-- Requiere haber ejecutado inicializacion.sql y datos_prueba.sql

USE sistema_costeo_3d;

-- =====================================================
-- PRUEBA 1: insercion y rollback de un cliente temporal
-- Comprueba que una operacion pueda revertirse sin dejar datos de prueba.
-- =====================================================
START TRANSACTION;

INSERT INTO cliente (nombre, telefono, email, activo)
VALUES ('Cliente Temporal', '3874999999', 'temporal@example.com', TRUE);

SET @cliente_temporal = LAST_INSERT_ID();

SELECT
    id_cliente,
    nombre,
    telefono,
    email,
    activo
FROM cliente
WHERE id_cliente = @cliente_temporal;

ROLLBACK;

SELECT
    COUNT(*) AS registros_temporales_despues_del_rollback
FROM cliente
WHERE id_cliente = @cliente_temporal;
-- Resultado esperado: 0

-- =====================================================
-- PRUEBA 2: transaccion de registro de trabajo y descuento de stock
-- Se simula la operacion completa y luego se revierte.
-- =====================================================
SELECT
    id_bobina,
    peso_actual
INTO
    @bobina_prueba,
    @stock_anterior
FROM bobina
WHERE activo = TRUE
  AND peso_actual >= 10
ORDER BY id_bobina
LIMIT 1;

START TRANSACTION;

INSERT INTO trabajo_impresion
    (id_pedido, id_modelo, fecha_creacion, fecha_inicio, fecha_fin,
     estado, tiempo_estimado_minutos)
VALUES
    (NULL, NULL, NOW(), NOW(), NOW(), 'FINALIZADO', 30);

SET @trabajo_prueba = LAST_INSERT_ID();

INSERT INTO consumo_filamento
    (id_trabajo, id_bobina, gramos_previstos, gramos_consumidos)
VALUES
    (@trabajo_prueba, @bobina_prueba, 10.00, 10.00);

UPDATE bobina
SET peso_actual = peso_actual - 10.00
WHERE id_bobina = @bobina_prueba;

SELECT
    @stock_anterior AS stock_antes,
    peso_actual AS stock_durante_transaccion
FROM bobina
WHERE id_bobina = @bobina_prueba;
-- Resultado esperado: stock_durante_transaccion = stock_antes - 10

ROLLBACK;

SELECT
    @stock_anterior AS stock_antes,
    peso_actual AS stock_despues_rollback
FROM bobina
WHERE id_bobina = @bobina_prueba;
-- Resultado esperado: ambos valores deben ser iguales

SELECT
    COUNT(*) AS trabajo_temporal_despues_del_rollback
FROM trabajo_impresion
WHERE id_trabajo = @trabajo_prueba;
-- Resultado esperado: 0

-- =====================================================
-- PRUEBA 3: verificaciones generales de consistencia
-- =====================================================

-- Bobinas cuyo peso actual supera el peso inicial.