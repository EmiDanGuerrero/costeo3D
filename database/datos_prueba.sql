-- PRESUPUESTO 2 - ACEPTADO, genera un pedido
-- =====================================================
INSERT INTO presupuesto
    (id_cliente, fecha_emision, fecha_vencimiento, estado, total)
VALUES
    (@cliente_juan, '2026-09-25 15:00:00', '2026-09-30 15:00:00',
     'ACEPTADO', 1700.00);
SET @presupuesto_2 = LAST_INSERT_ID();

INSERT INTO detalle_presupuesto
    (id_presupuesto, id_modelo, descripcion, cantidad,
     tiempo_estimado_minutos, costo_electricidad, costo_amortizacion,
     margen_porcentaje, precio_unitario)
VALUES
    (@presupuesto_2, @modelo_llavero, 'Llaveros personalizados', 2,
     110, 90.00, 120.00, 25.00, 850.00);
SET @detalle_2 = LAST_INSERT_ID();

INSERT INTO detalle_material_presupuesto
    (id_detalle, id_material, gramos_estimados, costo_por_gramo_aplicado)
VALUES
    (@detalle_2, @mat_pla_mate, 36.00, 20.5000);

-- =====================================================
-- PRESUPUESTO 3 - VENCIDO, item manual sin modelo asociado
-- =====================================================
INSERT INTO presupuesto
    (id_cliente, fecha_emision, fecha_vencimiento, estado, total)
VALUES
    (@cliente_maria, '2026-09-10 09:00:00', '2026-09-15 09:00:00',
     'VENCIDO', 4925.00);
SET @presupuesto_3 = LAST_INSERT_ID();

INSERT INTO detalle_presupuesto
    (id_presupuesto, id_modelo, descripcion, cantidad,
     tiempo_estimado_minutos, costo_electricidad, costo_amortizacion,
     margen_porcentaje, precio_unitario)
VALUES
    (@presupuesto_3, NULL, 'Maceta personalizada', 1,
     360, 300.00, 400.00, 25.00, 4925.00);
SET @detalle_3 = LAST_INSERT_ID();

INSERT INTO detalle_material_presupuesto
    (id_detalle, id_material, gramos_estimados, costo_por_gramo_aplicado)
VALUES
    (@detalle_3, @mat_pla_silk, 120.00, 27.0000);

-- =====================================================
-- PEDIDO
-- =====================================================
INSERT INTO pedido (id_presupuesto, fecha_creacion, estado)
VALUES (@presupuesto_2, '2026-09-26 10:00:00', 'EN_PRODUCCION');
SET @pedido_1 = LAST_INSERT_ID();

-- =====================================================
-- TRABAJO FINALIZADO ASOCIADO AL PEDIDO
-- =====================================================
INSERT INTO trabajo_impresion
    (id_pedido, id_modelo, fecha_creacion, fecha_inicio, fecha_fin,
     estado, tiempo_estimado_minutos)
VALUES
    (@pedido_1, @modelo_llavero,
     '2026-09-26 10:10:00', '2026-09-26 10:20:00', '2026-09-26 12:05:00',
     'FINALIZADO', 110);
SET @trabajo_finalizado = LAST_INSERT_ID();

INSERT INTO consumo_filamento
    (id_trabajo, id_bobina, gramos_previstos, gramos_consumidos)
VALUES
    (@trabajo_finalizado, @bobina_blanca, 36.00, 36.00);

-- =====================================================
-- TRABAJO FALLIDO SIN PEDIDO + MERMA
-- =====================================================
INSERT INTO trabajo_impresion
    (id_pedido, id_modelo, fecha_creacion, fecha_inicio, fecha_fin,
     estado, tiempo_estimado_minutos)
VALUES
    (NULL, @modelo_soporte,
     '2026-09-27 14:00:00', '2026-09-27 14:10:00', '2026-09-27 15:20:00',
     'FALLIDO', 210);
SET @trabajo_fallido = LAST_INSERT_ID();

INSERT INTO consumo_filamento
    (id_trabajo, id_bobina, gramos_previstos, gramos_consumidos)
VALUES
    (@trabajo_fallido, @bobina_roja, 65.00, 22.00);

INSERT INTO merma_estimada
    (id_trabajo, capas_totales, capa_falla)
VALUES
    (@trabajo_fallido, 300, 85);

COMMIT;

-- Verificacion rapida
SELECT 'material' AS tabla, COUNT(*) AS registros FROM material
UNION ALL
SELECT 'bobina', COUNT(*) FROM bobina
UNION ALL
SELECT 'modelo_3d', COUNT(*) FROM modelo_3d
UNION ALL
SELECT 'cliente', COUNT(*) FROM cliente
UNION ALL
SELECT 'presupuesto', COUNT(*) FROM presupuesto
UNION ALL
SELECT 'pedido', COUNT(*) FROM pedido
UNION ALL
SELECT 'trabajo_impresion', COUNT(*) FROM trabajo_impresion;
