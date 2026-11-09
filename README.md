# Costeo 3D

**Sistema de costeo e inventario para impresión 3D**

Costeo 3D es una aplicación de escritorio destinada a la gestión de un taller de impresión 3D. Centraliza el inventario de filamentos, el catálogo de modelos, el cálculo de costos y precios, la emisión de presupuestos y el seguimiento de pedidos y trabajos de impresión.

Su objetivo es mantener la trazabilidad entre lo presupuestado, lo producido y el material descontado de las bobinas físicas.

## Funcionalidades

- **Materiales y bobinas:** administración de materiales por marca, tipo y acabado; registro de bobinas, colores, pesos y stock disponible.
- **Modelos 3D:** catálogo con datos estimados de impresión e imágenes de referencia.
- **Costeo:** cálculo de costos de filamento, electricidad y amortización, con márgenes y parámetros comerciales configurables.
- **Clientes y presupuestos:** registro de clientes, cotizaciones con valores históricos y control de vigencia de 72 horas hábiles, de lunes a viernes.
- **Pedidos y producción:** generación de pedidos a partir de presupuestos aceptados, creación de trabajos y seguimiento de sus estados.
- **Finalización de trabajos:** recuperación del consumo aprobado en el presupuesto, selección de bobinas compatibles y descuento de stock.
- **Fallas y mermas:** estimación del material desperdiciado a partir de las capas totales y la capa de falla, con registro del consumo y actualización del inventario.
- **Reintentos:** creación automática de un nuevo trabajo pendiente para el mismo pedido cuando una impresión falla, conservando el historial y la relación con el intento anterior.
- **Panel de Inicio:** indicadores de pedidos activos, trabajos en curso, bobinas con stock bajo y mermas acumuladas.
- **Trazabilidad técnica:** manejo de errores, registros de eventos y transacciones para operaciones que modifican el inventario.

## Tecnologías y arquitectura

| Componente | Tecnología |
| --- | --- |
| Lenguaje | Java 25 |
| Interfaz gráfica | JavaFX 25, FXML y CSS |
| Base de datos | MySQL |
| Acceso a datos | JDBC (MySQL Connector/J) |
| Arquitectura | MVC, servicios de aplicación y DAO |
| Entorno de desarrollo | Eclipse IDE |

El proyecto utiliza Java estándar, con dependencias configuradas manualmente. **No emplea Maven ni Gradle**, ni requiere generar un instalador o un ejecutable propio.

## Requisitos

- JDK 25.
- JavaFX SDK 25.
- MySQL instalado y en ejecución.
- MySQL Connector/J disponible en el classpath del proyecto.
- Eclipse IDE, para compilar y ejecutar el código fuente, o las clases ya compiladas y un lanzador configurado.

## Configuración y ejecución

### 1. Preparar el proyecto

Importar el proyecto Java en Eclipse y verificar que utilice JDK 25. Agregar las bibliotecas del SDK de JavaFX y el controlador JDBC de MySQL al *Build Path*.

Para ejecutar la aplicación desde Eclipse, configurar los siguientes argumentos de la JVM, reemplazando la ruta por la ubicación real de JavaFX:

```text
--module-path "C:\ruta\javafx-sdk-25\lib" --add-modules javafx.controls,javafx.fxml
```

### 2. Preparar MySQL

En una instalación nueva, ejecutar el script `database/inicializacion.sql` para crear la base de datos `sistema_costeo_3d` y sus tablas.

**Advertencia:** el script de inicialización contiene `DROP DATABASE IF EXISTS`. **No debe ejecutarse sobre una base con información que se desea conservar.**

Para utilizar los reintentos automáticos, la tabla `trabajo_impresion` debe contar con la columna y relación `id_trabajo_origen`, incorporadas mediante la migración `sql_migracion_E11_4_4_reintentos.sql`. Aplicar esa migración una sola vez, únicamente si todavía no está incluida en el esquema instalado.

### 3. Configurar la conexión

Crear el archivo `config/database.local.properties`, tomando como referencia los siguientes valores y reemplazándolos por las credenciales de la instalación:

```properties
db.url=jdbc:mysql://localhost:3306/sistema_costeo_3d
db.user=usuario_mysql
db.password=clave_mysql
```

Este archivo contiene credenciales locales y **no debe incluirse en el repositorio**. Los parámetros comerciales se guardan por separado en `config/pricing.local.properties`.

### 4. Iniciar la aplicación

Ejecutar la clase principal:

```text
com.costeo3d.presentation.javafx.DesktopApplication
```

De manera opcional, puede utilizarse `iniciar_costeo3d.bat` desde la raíz del proyecto, siempre que estén configuradas las dependencias de JavaFX y JDBC y que las clases estén compiladas. El directorio de trabajo debe permitir el acceso a `config/` y `logs/`.

## Flujo de operación

1. Registrar los materiales y las bobinas disponibles.
2. Incorporar modelos 3D y clientes según corresponda.
3. Calcular costos y generar un presupuesto.
4. Aceptar el presupuesto y crear el pedido con sus trabajos de impresión.
5. Iniciar cada trabajo manualmente desde Producción.
6. Registrar el resultado: si finaliza correctamente, descontar el consumo presupuestado de las bobinas seleccionadas; si falla, registrar la merma y generar un trabajo de reintento pendiente.
7. Completar los trabajos necesarios y actualizar el estado del pedido hasta su entrega.

La estimación por capas es una aproximación del desperdicio; no constituye una medición física directa del filamento utilizado.

## Organización del código

```text
src/
├── main/
│   ├── java/com/costeo3d/
│   │   ├── application/       # Servicios y operaciones de aplicación
│   │   ├── bootstrap/         # Inicialización y composición de servicios
│   │   ├── domain/            # Entidades y reglas de negocio
│   │   ├── infrastructure/    # Configuración, JDBC, DAO y logs
│   │   └── presentation/      # Controladores e interfaz
│   └── resources/             # FXML, CSS e íconos
└── test/java/                 # Pruebas unitarias y de integración
```

Los scripts de base de datos se mantienen separados del código Java. La configuración local y los archivos de log no forman parte del código fuente.

## Pruebas

El proyecto incluye pruebas unitarias y de integración, además de verificaciones funcionales realizadas sobre los flujos de presupuestos, pedidos, finalización de impresiones, registro de fallas, reintentos y actualización del stock.

Las pruebas que utilizan JDBC requieren una base MySQL configurada. Se recomienda ejecutarlas sobre datos de prueba y no sobre información de producción.

## Alcance

Costeo 3D está diseñado para un **único operador** y utiliza persistencia local en MySQL. No interpreta automáticamente archivos STL o G-code, no controla directamente impresoras 3D y no incorpora facturación electrónica, procesamiento de pagos, sincronización en la nube ni integración con plataformas comerciales.

## Proyecto académico

Desarrollado por **Emiliano Guerrero** como proyecto de la asignatura **Seminario de Práctica de Informática**, de la **Licenciatura en Informática** (2026).
