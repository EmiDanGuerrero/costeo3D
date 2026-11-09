-- COSTEO 3D — E11.4.5 / Prueba de integración de pedido con varias unidades y reintento.
-- SOLO LECTURA: no cambia registros, stocks ni AUTO_INCREMENT.
-- Requiere la migración de E11.4.4: trabajo_impresion.id_trabajo_origen.
-- Ejecutar después de los pasos de prueba y volver a ejecutar al concluir el flujo.
-- Caso previsto: 1 concepto, 2 unidades, 250,00 g POR unidad, un material
-- y una bobina de 1000,00 g reservada exclusivamente para esta prueba.

USE sistema_costeo_3d;

-- EDITAR según los IDs mostrados en la aplicación (NO asumir que comienzan en 1).
SET @pedido_id = 1;
SET @bobina_id = 1;
SET @stock_inicial = 1000.00;
SET @consumo_esperado = 600.00; -- (250 * 2) + (250 * 200 / 500)
SET @stock_esperado = 400.00;

-- [01] Pedido y presupuesto utilizado: el pedido debe provenir de un presupuesto ACEPTADO.
SELECT p.id_pedido, p.estado AS estado_pedido, p.id_presupuesto,
       pr.estado AS estado_presupuesto
FROM pedido AS p
JOIN presupuesto AS pr ON pr.id_presupuesto = p.id_presupuesto
WHERE p.id_pedido = @pedido_id;

-- [02] Detalles aprobados: se esperan 2 unidades y 250 g por unidad en un único material.
SELECT dp.id_detalle, dp.id_modelo, dp.descripcion,
       dp.cantidad AS unidades_aprobadas, dmp.id_material,
       dmp.gramos_estimados AS gramos_por_unidad,
       dp.cantidad * dmp.gramos_estimados AS gramos_aprobados_total
FROM pedido AS p
JOIN detalle_presupuesto AS dp ON dp.id_presupuesto = p.id_presupuesto
JOIN detalle_material_presupuesto AS dmp ON dmp.id_detalle = dp.id_detalle
WHERE p.id_pedido = @pedido_id
ORDER BY dp.id_detalle, dmp.id_detalle_material;

-- [03] Historial de trabajos: deben ser 3 filas al terminar:
--      1 FALLIDO sin origen; 1 FINALIZADO sin origen; 1 FINALIZADO con origen = FALLIDO.
SELECT t.id_trabajo, t.id_trabajo_origen, t.id_pedido, t.id_modelo,
       t.estado, t.fecha_inicio, t.fecha_fin, t.tiempo_estimado_minutos
FROM trabajo_impresion AS t
WHERE t.id_pedido = @pedido_id
ORDER BY t.id_trabajo;

-- [04] Validación de cadenas de reintento (cada reintento debe apuntar a
--      un trabajo FALLIDO anterior del mismo pedido).
SELECT t.id_trabajo AS intento, t.id_trabajo_origen AS intento_origen,
       o.estado AS estado_origen,
       CASE
         WHEN o.id_trabajo IS NOT NULL AND o.id_pedido = t.id_pedido
              AND o.estado = 'FALLIDO' AND o.id_trabajo < t.id_trabajo
           THEN 'OK'
         ELSE 'REVISAR: origen inválido'
       END AS validacion
FROM trabajo_impresion AS t
LEFT JOIN trabajo_impresion AS o ON o.id_trabajo = t.id_trabajo_origen
WHERE t.id_pedido = @pedido_id
  AND t.id_trabajo_origen IS NOT NULL
ORDER BY t.id_trabajo;

-- [05] Detalle de material consumido por intento (una fila por intento
--      cuando hay un único material/bobina por impresión).
SELECT t.id_trabajo, t.id_trabajo_origen, t.estado,
       c.id_bobina, c.gramos_previstos, c.gramos_consumidos,
       m.capas_totales, m.capa_falla
FROM trabajo_impresion AS t
LEFT JOIN consumo_filamento AS c ON c.id_trabajo = t.id_trabajo
LEFT JOIN merma_estimada AS m ON m.id_trabajo = t.id_trabajo
WHERE t.id_pedido = @pedido_id
ORDER BY t.id_trabajo, c.id_consumo;

-- [06] Resumen y condiciones finales. Ejecutar después de completar el reintento.
--      Esperado: 3 trabajos, 1 fallido, 2 finalizados, 0 abiertos, 1 reintento,
--      1 merma y 600,00 g consumidos. El pedido podrá quedar LISTO al marcarlo manualmente.
SELECT
    COUNT(*) AS trabajos_totales,
    SUM(t.estado = 'FALLIDO') AS fallidos,
    SUM(t.estado = 'FINALIZADO') AS finalizados,
    SUM(t.estado IN ('PENDIENTE','EN_PROCESO')) AS abiertos,
    SUM(t.id_trabajo_origen IS NOT NULL) AS reintentos,
    (SELECT COUNT(*) FROM merma_estimada AS m
       JOIN trabajo_impresion AS x ON x.id_trabajo = m.id_trabajo
      WHERE x.id_pedido = @pedido_id) AS registros_merma,
    (SELECT COALESCE(SUM(c.gramos_consumidos), 0)
       FROM consumo_filamento AS c
       JOIN trabajo_impresion AS x ON x.id_trabajo = c.id_trabajo
      WHERE x.id_pedido = @pedido_id) AS gramos_consumidos_pedido
FROM trabajo_impresion AS t
WHERE t.id_pedido = @pedido_id;

-- [07] Stock final. SOLO es comparable con @stock_inicial si la bobina
--      empezó con ese peso y no fue usada por otros pedidos durante la prueba.
SELECT b.id_bobina, b.peso_inicial, b.peso_actual,
       @stock_inicial AS stock_inicial_prueba,
       @stock_esperado AS stock_final_esperado,
       CASE WHEN b.peso_actual = @stock_esperado THEN 'OK'
            ELSE 'REVISAR: diferencia de stock' END AS verificacion_stock
FROM bobina AS b
WHERE b.id_bobina = @bobina_id;

-- [08] Total descontado SOLO a esta bobina y SOLO por este pedido.
SELECT @bobina_id AS id_bobina, @pedido_id AS id_pedido,
       COALESCE(SUM(c.gramos_consumidos), 0) AS gramos_descontados,
       @consumo_esperado AS gramos_esperados,
       CASE WHEN COALESCE(SUM(c.gramos_consumidos), 0) = @consumo_esperado
            THEN 'OK' ELSE 'REVISAR: revisar los consumos de cada intento'
       END AS verificacion_consumo
FROM consumo_filamento AS c
JOIN trabajo_impresion AS t ON t.id_trabajo = c.id_trabajo
WHERE t.id_pedido = @pedido_id AND c.id_bobina = @bobina_id;

-- [09] Duplicación accidental: para un único material en una única bobina
--      se espera exactamente un consumo por cada intento cerrado.
SELECT t.id_trabajo, t.estado, COUNT(c.id_consumo) AS registros_consumo
FROM trabajo_impresion AS t
LEFT JOIN consumo_filamento AS c ON c.id_trabajo = t.id_trabajo
WHERE t.id_pedido = @pedido_id
GROUP BY t.id_trabajo, t.estado
ORDER BY t.id_trabajo;
