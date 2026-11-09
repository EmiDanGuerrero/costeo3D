-- COSTEO 3D | Auditoría transversal de datos posterior a E11.4 y panel Inicio
-- SOLO CONSULTA: no contiene DELETE, UPDATE, INSERT, ALTER, TRUNCATE ni DROP.
-- Requiere la migración E11.4.4: trabajo_impresion.id_trabajo_origen.
-- Ejecutar en MySQL 8+ con una copia de seguridad vigente del proyecto.
USE sistema_costeo_3d;

-- 0. CONTEXTO: confirma base y fecha local del servidor MySQL.
SELECT DATABASE() AS base_en_uso, NOW() AS fecha_servidor;

-- 1. RESUMEN OPERATIVO PARA CONTRASTAR CON INICIO.
SELECT
  (SELECT COUNT(*) FROM pedido WHERE estado IN ('PENDIENTE','EN_PRODUCCION')) AS pedidos_activos,
  (SELECT COUNT(*) FROM trabajo_impresion WHERE estado = 'EN_PROCESO') AS trabajos_en_curso,
  (SELECT COUNT(*) FROM bobina b JOIN material m ON m.id_material = b.id_material
   WHERE b.activo=1 AND m.activo=1 AND b.peso_actual <= 200.00) AS bobinas_stock_bajo,
  (SELECT COUNT(*) FROM trabajo_impresion WHERE estado = 'FALLIDO') AS trabajos_fallidos_historicos,
  (SELECT COUNT(*) FROM trabajo_impresion WHERE id_trabajo_origen IS NOT NULL) AS reintentos_registrados,
  (SELECT COALESCE(SUM(cf.gramos_consumidos),0)
   FROM merma_estimada me
   JOIN trabajo_impresion t ON t.id_trabajo = me.id_trabajo
   LEFT JOIN consumo_filamento cf ON cf.id_trabajo = t.id_trabajo
   WHERE t.estado = 'FALLIDO') AS merma_registrada_gramos;

-- 2. CONTROL PRINCIPAL: se espera hallazgos = 0 para todos los renglones.
-- Las restricciones CHECK y FK ya cubren parte de esto; otros controles
-- corroboran reglas de negocio que no están implementadas como CHECK.
SELECT 'A01 | Bobinas con peso fuera de rango' AS control,
       COUNT(*) AS hallazgos
FROM bobina
WHERE peso_inicial <= 0 OR peso_actual < 0 OR peso_actual > peso_inicial
UNION ALL
SELECT 'A02 | Trabajos cerrados sin fecha_fin', COUNT(*)
FROM trabajo_impresion
WHERE estado IN ('FINALIZADO','FALLIDO') AND fecha_fin IS NULL
UNION ALL
SELECT 'A03 | Trabajos abiertos con fecha_fin', COUNT(*)
FROM trabajo_impresion
WHERE estado IN ('PENDIENTE','EN_PROCESO') AND fecha_fin IS NOT NULL
UNION ALL
SELECT 'A04 | Consumo asociado a trabajo abierto', COUNT(*)
FROM consumo_filamento cf
JOIN trabajo_impresion t ON t.id_trabajo=cf.id_trabajo
WHERE t.estado IN ('PENDIENTE','EN_PROCESO')
UNION ALL
SELECT 'A05 | Finalizados sin registro de consumo', COUNT(*)
FROM trabajo_impresion t
WHERE t.estado='FINALIZADO'
  AND NOT EXISTS (SELECT 1 FROM consumo_filamento cf WHERE cf.id_trabajo=t.id_trabajo)
UNION ALL
SELECT 'A06 | Merma en trabajo que no figura FALLIDO', COUNT(*)
FROM merma_estimada me
JOIN trabajo_impresion t ON t.id_trabajo=me.id_trabajo
WHERE t.estado<>'FALLIDO'
UNION ALL
SELECT 'A07 | Consumo de falla no coincide con fórmula de capas', COUNT(*)
FROM merma_estimada me
JOIN consumo_filamento cf ON cf.id_trabajo=me.id_trabajo
WHERE cf.gramos_previstos IS NULL OR
      cf.gramos_consumidos <> ROUND(
          cf.gramos_previstos * me.capa_falla / me.capas_totales, 2)
UNION ALL
SELECT 'A08 | Reintento con origen no fallido o ausente', COUNT(*)
FROM trabajo_impresion nuevo
LEFT JOIN trabajo_impresion origen ON origen.id_trabajo=nuevo.id_trabajo_origen
WHERE nuevo.id_trabajo_origen IS NOT NULL
  AND (origen.id_trabajo IS NULL OR origen.estado <> 'FALLIDO')
UNION ALL
SELECT 'A09 | Reintento cambió pedido/modelo o no es posterior', COUNT(*)
FROM trabajo_impresion nuevo
JOIN trabajo_impresion origen ON origen.id_trabajo=nuevo.id_trabajo_origen
WHERE NOT (nuevo.id_pedido <=> origen.id_pedido)
   OR NOT (nuevo.id_modelo <=> origen.id_modelo)
   OR nuevo.id_trabajo <= origen.id_trabajo
UNION ALL
SELECT 'A10 | Pedidos listos/entregados con trabajos abiertos', COUNT(*)
FROM pedido p
WHERE p.estado IN ('LISTO','ENTREGADO')
 AND EXISTS (SELECT 1 FROM trabajo_impresion t
             WHERE t.id_pedido=p.id_pedido AND t.estado IN ('PENDIENTE','EN_PROCESO'))
UNION ALL
SELECT 'A11 | Pedidos listos/entregados sin ninguna impresión finalizada', COUNT(*)
FROM pedido p
WHERE p.estado IN ('LISTO','ENTREGADO')
 AND NOT EXISTS (SELECT 1 FROM trabajo_impresion t
                 WHERE t.id_pedido=p.id_pedido AND t.estado='FINALIZADO')
UNION ALL
SELECT 'A12 | Pedidos listos/entregados con falla sin reintento', COUNT(*)
FROM pedido p
JOIN trabajo_impresion fallido ON fallido.id_pedido=p.id_pedido AND fallido.estado='FALLIDO'
WHERE p.estado IN ('LISTO','ENTREGADO')
  AND NOT EXISTS (SELECT 1 FROM trabajo_impresion r
                  WHERE r.id_trabajo_origen=fallido.id_trabajo)
UNION ALL
SELECT 'A13 | Intentos reintentados más de una vez', COUNT(*)
FROM (SELECT id_trabajo_origen FROM trabajo_impresion
      WHERE id_trabajo_origen IS NOT NULL
      GROUP BY id_trabajo_origen HAVING COUNT(*)>1) duplicados;

-- 3. ADVERTENCIAS: NO necesariamente son errores.
-- Puede haber registros antiguos previos a E11.4.4, y presupuestos cuyo
-- vencimiento aún no se marcó manualmente en la pantalla de Presupuestos.
SELECT 'W01 | Fallidos sin merma (pueden ser históricos)' AS advertencia,
       COUNT(*) AS registros
FROM trabajo_impresion t
WHERE t.estado='FALLIDO' AND NOT EXISTS
 (SELECT 1 FROM merma_estimada me WHERE me.id_trabajo=t.id_trabajo)
UNION ALL
SELECT 'W02 | Fallidos con pedido sin reintento (pueden ser históricos)', COUNT(*)
FROM trabajo_impresion t
WHERE t.estado='FALLIDO' AND t.id_pedido IS NOT NULL
 AND NOT EXISTS (SELECT 1 FROM trabajo_impresion r WHERE r.id_trabajo_origen=t.id_trabajo)
UNION ALL
SELECT 'W03 | Presupuestos VIGENTE con vencimiento cumplido', COUNT(*)
FROM presupuesto p
WHERE p.estado='VIGENTE' AND p.fecha_vencimiento <= NOW();

-- 4. DETALLE DE REINTENTOS: muestra las cadenas (una fila por intento nuevo).
SELECT t.id_trabajo AS id_reintento,
       t.id_trabajo_origen AS id_fallido_origen,
       t.id_pedido,
       t.id_modelo,
       o.estado AS estado_origen,
       t.estado AS estado_reintento,
       t.fecha_creacion
FROM trabajo_impresion t
LEFT JOIN trabajo_impresion o ON o.id_trabajo=t.id_trabajo_origen
WHERE t.id_trabajo_origen IS NOT NULL
ORDER BY t.id_pedido, t.id_trabajo;

-- 5. CONSUMOS Y MERMAS POR TRABAJO: verificación visual.
-- Un trabajo con varios materiales aparece una sola vez por agrupación.
SELECT t.id_trabajo, t.id_pedido, t.estado,
       COUNT(cf.id_consumo) AS registros_consumo,
       COALESCE(SUM(cf.gramos_consumidos),0) AS gramos_consumidos,
       MAX(me.capas_totales) AS capas_totales,
       MAX(me.capa_falla) AS capa_falla
FROM trabajo_impresion t
LEFT JOIN consumo_filamento cf ON cf.id_trabajo=t.id_trabajo
LEFT JOIN merma_estimada me ON me.id_trabajo=t.id_trabajo
GROUP BY t.id_trabajo, t.id_pedido, t.estado
ORDER BY t.id_trabajo DESC
LIMIT 50;

-- 6. BOBINAS: estado actual, SIN inferir un stock inicial a partir de movimientos.
-- El stock puede haber sido editado manualmente o reabastecido fuera de producción.
SELECT b.id_bobina, m.marca, m.tipo, b.color,
       b.peso_inicial, b.peso_actual,
       b.activo AS bobina_activa,
       m.activo AS material_activo
FROM bobina b
JOIN material m ON m.id_material=b.id_material
ORDER BY b.id_bobina;

-- Nota: 0 hallazgos en A01-A13 es una condición deseable, pero NO demuestra
-- por sí solo que se haya ejecutado la interfaz, las pruebas de concurrencia,
-- los logs o la persistencia de MySQL con la aplicación abierta.
