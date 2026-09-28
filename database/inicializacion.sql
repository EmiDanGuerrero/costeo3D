DROP DATABASE IF EXISTS sistema_costeo_3d;

CREATE DATABASE sistema_costeo_3d
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE sistema_costeo_3d;


-- =========================================================
-- MATERIAL
-- =========================================================

CREATE TABLE material (
    id_material INT AUTO_INCREMENT,
    marca VARCHAR(100) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    acabado VARCHAR(50) NOT NULL,
    costo_por_gramo DECIMAL(12,4) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_material
        PRIMARY KEY (id_material),

    CONSTRAINT chk_material_costo
        CHECK (costo_por_gramo >= 0)
);


-- =========================================================
-- BOBINA
-- =========================================================

CREATE TABLE bobina (
    id_bobina INT AUTO_INCREMENT,
    id_material INT NOT NULL,
    color VARCHAR(60) NOT NULL,
    peso_inicial DECIMAL(10,2) NOT NULL,
    peso_actual DECIMAL(10,2) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_bobina
        PRIMARY KEY (id_bobina),

    CONSTRAINT fk_bobina_material
        FOREIGN KEY (id_material)
        REFERENCES material(id_material)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_bobina_peso_inicial
        CHECK (peso_inicial > 0),

    CONSTRAINT chk_bobina_peso_actual
        CHECK (
            peso_actual >= 0
            AND peso_actual <= peso_inicial
        )
);


-- =========================================================
-- MODELO 3D
-- =========================================================

CREATE TABLE modelo_3d (
    id_modelo INT AUTO_INCREMENT,
    nombre VARCHAR(150) NOT NULL,
    imagen_referencia VARCHAR(500),
    plataforma_origen VARCHAR(150),
    gramos_estimados DECIMAL(10,2) NOT NULL,
    tiempo_estimado_minutos INT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_modelo_3d
        PRIMARY KEY (id_modelo),

    CONSTRAINT chk_modelo_gramos
        CHECK (gramos_estimados > 0),

    CONSTRAINT chk_modelo_tiempo
        CHECK (tiempo_estimado_minutos > 0)
);


-- =========================================================
-- CLIENTE
-- =========================================================

CREATE TABLE cliente (
    id_cliente INT AUTO_INCREMENT,
    nombre VARCHAR(150) NOT NULL,
    telefono VARCHAR(50),
    email VARCHAR(150),
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_cliente
        PRIMARY KEY (id_cliente)
);


-- =========================================================
-- PRESUPUESTO
-- =========================================================

CREATE TABLE presupuesto (
    id_presupuesto INT AUTO_INCREMENT,
    id_cliente INT NOT NULL,
    fecha_emision DATETIME NOT NULL,
    fecha_vencimiento DATETIME NOT NULL,
    estado ENUM(
        'VIGENTE',
        'VENCIDO',
        'ACEPTADO',
        'CANCELADO'
    ) NOT NULL DEFAULT 'VIGENTE',
    total DECIMAL(12,2) NOT NULL,

    CONSTRAINT pk_presupuesto
        PRIMARY KEY (id_presupuesto),

    CONSTRAINT fk_presupuesto_cliente
        FOREIGN KEY (id_cliente)
        REFERENCES cliente(id_cliente)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_presupuesto_total
        CHECK (total >= 0),

    CONSTRAINT chk_presupuesto_fechas
        CHECK (fecha_vencimiento > fecha_emision)
);


-- =========================================================
-- DETALLE DE PRESUPUESTO
-- =========================================================

CREATE TABLE detalle_presupuesto (
    id_detalle INT AUTO_INCREMENT,
    id_presupuesto INT NOT NULL,
    id_modelo INT NULL,
    descripcion VARCHAR(255) NOT NULL,
    cantidad INT NOT NULL DEFAULT 1,
    tiempo_estimado_minutos INT NOT NULL,
    costo_electricidad DECIMAL(12,2) NOT NULL DEFAULT 0,
    costo_amortizacion DECIMAL(12,2) NOT NULL DEFAULT 0,
    margen_porcentaje DECIMAL(6,2) NOT NULL DEFAULT 0,
    precio_unitario DECIMAL(12,2) NOT NULL,

    CONSTRAINT pk_detalle_presupuesto
        PRIMARY KEY (id_detalle),

    CONSTRAINT fk_detalle_presupuesto
        FOREIGN KEY (id_presupuesto)
        REFERENCES presupuesto(id_presupuesto)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_detalle_modelo
        FOREIGN KEY (id_modelo)
        REFERENCES modelo_3d(id_modelo)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT chk_detalle_cantidad
        CHECK (cantidad > 0),

    CONSTRAINT chk_detalle_tiempo
        CHECK (tiempo_estimado_minutos > 0),

    CONSTRAINT chk_detalle_electricidad
        CHECK (costo_electricidad >= 0),

    CONSTRAINT chk_detalle_amortizacion
        CHECK (costo_amortizacion >= 0),

    CONSTRAINT chk_detalle_margen
        CHECK (margen_porcentaje >= 0),

    CONSTRAINT chk_detalle_precio
        CHECK (precio_unitario >= 0)
);


-- =========================================================
-- MATERIALES UTILIZADOS EN EL PRESUPUESTO
-- =========================================================

CREATE TABLE detalle_material_presupuesto (
    id_detalle_material INT AUTO_INCREMENT,
    id_detalle INT NOT NULL,
    id_material INT NOT NULL,
    gramos_estimados DECIMAL(10,2) NOT NULL,
    costo_por_gramo_aplicado DECIMAL(12,4) NOT NULL,

    CONSTRAINT pk_detalle_material_presupuesto
        PRIMARY KEY (id_detalle_material),

    CONSTRAINT fk_detalle_material_detalle
        FOREIGN KEY (id_detalle)
        REFERENCES detalle_presupuesto(id_detalle)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_detalle_material_material
        FOREIGN KEY (id_material)
        REFERENCES material(id_material)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_detalle_material_gramos
        CHECK (gramos_estimados > 0),

    CONSTRAINT chk_detalle_material_costo
        CHECK (costo_por_gramo_aplicado >= 0)
);


-- =========================================================
-- PEDIDO
-- =========================================================

CREATE TABLE pedido (
    id_pedido INT AUTO_INCREMENT,
    id_presupuesto INT NOT NULL,
    fecha_creacion DATETIME NOT NULL,
    estado ENUM(
        'PENDIENTE',
        'EN_PRODUCCION',
        'LISTO',
        'ENTREGADO',
        'CANCELADO'
    ) NOT NULL DEFAULT 'PENDIENTE',

    CONSTRAINT pk_pedido
        PRIMARY KEY (id_pedido),

    CONSTRAINT uq_pedido_presupuesto
        UNIQUE (id_presupuesto),

    CONSTRAINT fk_pedido_presupuesto
        FOREIGN KEY (id_presupuesto)
        REFERENCES presupuesto(id_presupuesto)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);


-- =========================================================
-- TRABAJO DE IMPRESIÓN
-- =========================================================

CREATE TABLE trabajo_impresion (
    id_trabajo INT AUTO_INCREMENT,
    id_pedido INT NULL,
    id_modelo INT NULL,
    fecha_creacion DATETIME NOT NULL,
    fecha_inicio DATETIME NULL,
    fecha_fin DATETIME NULL,
    estado ENUM(
        'PENDIENTE',
        'EN_PROCESO',
        'FINALIZADO',
        'FALLIDO'
    ) NOT NULL DEFAULT 'PENDIENTE',
    tiempo_estimado_minutos INT NOT NULL,

    CONSTRAINT pk_trabajo_impresion
        PRIMARY KEY (id_trabajo),

    CONSTRAINT fk_trabajo_pedido
        FOREIGN KEY (id_pedido)
        REFERENCES pedido(id_pedido)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_trabajo_modelo
        FOREIGN KEY (id_modelo)
        REFERENCES modelo_3d(id_modelo)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT chk_trabajo_tiempo
        CHECK (tiempo_estimado_minutos > 0),

    CONSTRAINT chk_trabajo_fechas
        CHECK (
            fecha_inicio IS NULL
            OR fecha_fin IS NULL
            OR fecha_fin >= fecha_inicio
        )
);


-- =========================================================
-- CONSUMO DE FILAMENTO
-- =========================================================

CREATE TABLE consumo_filamento (
    id_consumo INT AUTO_INCREMENT,
    id_trabajo INT NOT NULL,
    id_bobina INT NOT NULL,
    gramos_previstos DECIMAL(10,2),
    gramos_consumidos DECIMAL(10,2) NOT NULL,

    CONSTRAINT pk_consumo_filamento
        PRIMARY KEY (id_consumo),

    CONSTRAINT fk_consumo_trabajo
        FOREIGN KEY (id_trabajo)
        REFERENCES trabajo_impresion(id_trabajo)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_consumo_bobina
        FOREIGN KEY (id_bobina)
        REFERENCES bobina(id_bobina)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_consumo_previsto
        CHECK (
            gramos_previstos IS NULL
            OR gramos_previstos >= 0
        ),

    CONSTRAINT chk_consumo_real
        CHECK (gramos_consumidos >= 0)
);


-- =========================================================
-- MERMA ESTIMADA
-- =========================================================

CREATE TABLE merma_estimada (
    id_merma INT AUTO_INCREMENT,
    id_trabajo INT NOT NULL,
    capas_totales INT NOT NULL,
    capa_falla INT NOT NULL,

    CONSTRAINT pk_merma_estimada
        PRIMARY KEY (id_merma),

    CONSTRAINT uq_merma_trabajo
        UNIQUE (id_trabajo),

    CONSTRAINT fk_merma_trabajo
        FOREIGN KEY (id_trabajo)
        REFERENCES trabajo_impresion(id_trabajo)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT chk_merma_capas_totales
        CHECK (capas_totales > 0),

    CONSTRAINT chk_merma_capa_falla
        CHECK (
            capa_falla >= 0
            AND capa_falla <= capas_totales
        )
);