-- consultas.sql
-- Consultas utiles del sistema costeo3d

USE sistema_costeo_3d;

-- =====================================================
-- 1. Inventario actual de bobinas
-- =====================================================
SELECT
    b.id_bobina,
    m.marca,
    m.tipo,
    m.acabado,
    b.color,
    b.peso_inicial,
    b.peso_actual
FROM bobina b
INNER JOIN material m ON b.id_material = m.id_material
WHERE b.activo = TRUE
ORDER BY m.tipo, b.color;

-- =====================================================
-- 2. Bobinas con bajo stock
-- Cambiar el limite segun la necesidad del operador
-- =====================================================
SET @stock_minimo = 100;

SELECT
    b.id_bobina,
    m.marca,
    m.tipo,
    b.color,
    b.peso_actual
FROM bobina b
INNER JOIN material m ON b.id_material = m.id_material
WHERE b.activo = TRUE
  AND b.peso_actual < @stock_minimo
ORDER BY b.peso_actual ASC;

-- =====================================================
-- 3. Materiales y costos vigentes
-- =====================================================
SELECT
    id_material,
    marca,
    tipo,
    acabado,
    costo_por_gramo
FROM material
WHERE activo = TRUE
ORDER BY tipo, marca, acabado;

-- =====================================================
-- 4. Presupuestos de un cliente
-- Usa como ejemplo el primer cliente registrado
-- =====================================================
SET @cliente_consulta = (SELECT MIN(id_cliente) FROM cliente);

SELECT
    p.id_presupuesto,
    c.nombre AS cliente,
    p.fecha_emision,
    p.fecha_vencimiento,
    p.estado,
    p.total
FROM presupuesto p
INNER JOIN cliente c ON p.id_cliente = c.id_cliente
WHERE p.id_cliente = @cliente_consulta
ORDER BY p.fecha_emision DESC;

-- =====================================================
-- 5. Presupuestos vencidos
-- La determinacion de las 72 horas habiles se realiza en la aplicacion.
-- Esta consulta recupera los que ya poseen estado VENCIDO.
-- =====================================================
SELECT
    p.id_presupuesto,
    c.nombre AS cliente,
    p.fecha_emision,
    p.fecha_vencimiento,
    p.total
FROM presupuesto p
INNER JOIN cliente c ON p.id_cliente = c.id_cliente
WHERE p.estado = 'VENCIDO'
ORDER BY p.fecha_vencimiento DESC;

-- =====================================================
-- 6. Detalle historico de un presupuesto
-- Conserva el costo por gramo aplicado al momento de cotizar.
-- =====================================================
SET @presupuesto_consulta = (SELECT MIN(id_presupuesto) FROM presupuesto);

SELECT
    p.id_presupuesto,
    c.nombre AS cliente,
    dp.descripcion,