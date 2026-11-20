# Modelo relacional de negocio de Exotic — contexto v1

Fecha de revision: 2026-11-19. Instantanea estatica revisable; no se consulta la base de datos para elaborar ni cargar este documento.
Huella de fuentes JPA (SHA-256; UTF-8; LF): `4f123bad503e91ce967cf6d439e7bc0ddf2681f477e7ffa014aac136b635867a`.
Es conocimiento de estructura, no un contrato de herramientas ni una autorizacion de acceso. La capacidad ejecutable sigue delimitada por el catalogo de consultas y las operaciones implementadas por el servidor. Conocer una entidad no permite leerla, unirla, crearla, editarla o borrarla.
No se incluyen registros empresariales, preguntas, resultados, credenciales, contenidos de archivos, expresiones de inicializacion, valores predeterminados ni semillas SQL.

## Convenciones y fidelidad

- Los atributos y relaciones usan nombres LOGICOS Java/JPA. No son nombres de columnas SQL y no deben convertirse automaticamente en SQL.
- Solo se publica una tabla fisica cuando esta declarada mediante @Table y corroborada en DDL versionado. Los subtipos comparten la tabla de su raiz de herencia cuando asi lo declara el modelo. Las tablas implicitas no se inventan mediante conversion de nombres.
- ManyToOne/OneToOne/OneToMany/ManyToMany expresan la cardinalidad de la anotacion, no garantizan que una relacion opcional siempre exista. Las colecciones pueden estar vacias.
- Los campos heredados se consultan en la entidad padre; no se duplican aqui. PK identifica el atributo anotado @Id. Los tipos enum se nombran sin inferir equivalencias de codigos o estados.
- Se omiten relaciones hacia identidades, autenticacion o entidades excluidas, y campos de control de credenciales, identificacion de actores, binarios, archivos y snapshots opacos. Se mantienen fechas, estados y referencias empresariales no identificativas.
- Las tablas de enlace y tablas sin entidad JPA se documentan por separado desde DDL. El DDL fuente es un inventario estatico, no prueba del esquema desplegado ni de su historial Flyway.

## Cobertura de fuentes

- Inventario: 146 clases @Entity en 17 dominios; cero @Embeddable y cero @MappedSuperclass en src/main/java.
- Entidades incluidas: 135; entidades excluidas con motivo: 11. Ninguna entidad del inventario queda sin clasificar.
- Atributos escalares incluidos: 978; relaciones incluidas: 299; campos o relaciones omitidos en entidades incluidas: 200.
- Tablas declaradas por entidades incluidas y corroboradas en DDL: 122. Los nombres fisicos de entidades sin @Table permanecen sin resolver.
- Fuentes de estructura: src/main/java/exotic/app/planta/model/**/*.java; src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql y DDL posterior de la linea historica V119–V128. No se ejecuta ni se copia el contenido de sentencias de datos.
- Mantenimiento: actualizar esta instantanea al cambiar entidades o DDL, revisar exclusiones y comparar cobertura antes de publicar. No expandirla automaticamente en produccion. El inventario reproducible debe leer metadatos de fuentes o mapping ORM, nunca valores de instancias ni registros.

| Dominio fuente | Entidades inventariadas | Incluidas | Excluidas |
| --- | ---: | ---: | ---: |
| activos | 10 | 10 | 0 |
| calidad | 5 | 5 | 0 |
| compras | 7 | 7 | 0 |
| contabilidad | 4 | 4 | 0 |
| controles | 13 | 12 | 1 |
| cronograma | 2 | 2 | 0 |
| empresa | 4 | 4 | 0 |
| inventarios | 3 | 3 | 0 |
| master | 3 | 1 | 2 |
| monitoreoerrores | 2 | 0 | 2 |
| notificaciones | 1 | 1 | 0 |
| organigrama | 4 | 4 | 0 |
| organizacion | 11 | 11 | 0 |
| produccion | 40 | 39 | 1 |
| producto | 26 | 26 | 0 |
| users | 5 | 0 | 5 |
| ventas | 6 | 6 | 0 |

## Entidades de negocio incluidas

## Dominio: activos

### ActivoFijo
Clase: `exotic.app.planta.model.activos.fijos.ActivoFijo`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/ActivoFijo.java`
Tabla declarada y corroborada en DDL: `activo`.
Atributos: id:String [PK]; nombre:String; brand:String; estado:int; fechaCodificacion:LocalDateTime; fechaBaja:LocalDateTime; valorAdquisicion:BigDecimal; valorResidual:BigDecimal; vidaUtilMeses:Integer; metodoDespreciacion:String; tipoActivo:TipoActivo; unidadesCapacidad:UnidadesCapacidad; capacidad:Double; ubicacion:String; estadoOpercional:int; tiempoCicloEstandar:Double; tiempoSetupEstandar:Double; eficienciaOperativa:Double; disponibilidadPorTurno:Double.
Relaciones: depreciaciones -> DepreciacionActivo [OneToMany]; tipoRecurso -> RecursoProduccion [ManyToOne]; traslados -> TrasladoActivo [OneToMany]; mantenimientos -> MantenimientoActivo [OneToMany]; lineaIncorporacion -> IncorporacionActivoLine [OneToOne]; documentosBaja -> DocumentoBajaActivo [OneToMany]; responsable -> IntegrantePersonal [ManyToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### FacturaCompraActivo
Clase: `exotic.app.planta.model.activos.fijos.compras.FacturaCompraActivo`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/compras/FacturaCompraActivo.java`
Tabla declarada y corroborada en DDL: `factura_compra_activo`.
Atributos: facturaCompraActivoId:int [PK]; numeroFacturaProveedor:String; fechaCompra:LocalDateTime; subTotal:double; iva:double; totalPagar:double; condicionPago:String; plazoPago:int; estadoPago:int.
Relaciones: proveedor -> Proveedor [ManyToOne]; ordenCompraActivo -> OrdenCompraActivo [ManyToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### ItemOrdenCompraActivo
Clase: `exotic.app.planta.model.activos.fijos.compras.ItemOrdenCompraActivo`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/compras/ItemOrdenCompraActivo.java`
Tabla declarada y corroborada en DDL: `item_orden_compra_activo`.
Atributos: itemOrdenId:int [PK]; nombre:String; cantidad:int; precioUnitario:double; ivaPercentage:double; ivaValue:double; subTotal:double.
Relaciones: ordenCompraActivo -> OrdenCompraActivo [ManyToOne].

### OrdenCompraActivo
Clase: `exotic.app.planta.model.activos.fijos.compras.OrdenCompraActivo`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/compras/OrdenCompraActivo.java`
Tabla declarada y corroborada en DDL: `orden_compra_activo`.
Atributos: ordenCompraActivoId:int [PK]; fechaEmision:LocalDateTime; fechaVencimiento:LocalDateTime; subTotal:double; iva:double; totalPagar:double; condicionPago:String; tiempoEntrega:String; plazoPago:int; estado:int; divisa:DIVISAS; trm:double; facturaCompraActivoId:Integer.
Relaciones: proveedor -> Proveedor [ManyToOne]; empresaIdentidadLegalVersion -> EmpresaIdentidadLegalVersion [ManyToOne]; empresaLogoDocumentalVersion -> EmpresaLogoDocumentalVersion [ManyToOne]; itemsOrdenCompra -> ItemOrdenCompraActivo [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### DepreciacionActivo
Clase: `exotic.app.planta.model.activos.fijos.gestion.DepreciacionActivo`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/gestion/DepreciacionActivo.java`
Tabla declarada y corroborada en DDL: `depreciacion_activo`.
Atributos: id:Long [PK]; fechaDepreciacion:LocalDate; montoDepreciado:BigDecimal; valorLibroActual:BigDecimal; metodoDepreciacion:String.
Relaciones: activoFijo -> ActivoFijo [ManyToOne]; asientoContable -> AsientoContable [OneToOne].

### DocumentoBajaActivo
Clase: `exotic.app.planta.model.activos.fijos.gestion.DocumentoBajaActivo`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/gestion/DocumentoBajaActivo.java`
Tabla declarada y corroborada en DDL: `documento_baja_activo`.
Atributos: bajaId:Long [PK]; fechaBaja:LocalDateTime; motivoBaja:int; descripcionMotivo:String; valorContableBaja:BigDecimal; metodoDisposicion:int; descripcionDisposicion:String; estado:int; observaciones:String.
Relaciones: activoFijo -> ActivoFijo [ManyToOne]; responsable -> IntegrantePersonal [ManyToOne]; asientoContable -> AsientoContable [OneToOne].

### IncorporacionActivoHeader
Clase: `exotic.app.planta.model.activos.fijos.gestion.IncorporacionActivoHeader`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/gestion/IncorporacionActivoHeader.java`
Tabla declarada y corroborada en DDL: `incorporacion_activo_header`.
Atributos: incorporacionId:Long [PK]; fechaIncorporacion:LocalDateTime; observaciones:String; estado:int; estadoContable:EstadoContable.
Relaciones: facturaCompraActivo -> FacturaCompraActivo [ManyToOne]; responsable -> IntegrantePersonal [ManyToOne]; asientoContable -> AsientoContable [OneToOne]; lineasIncorporacion -> IncorporacionActivoLine [OneToMany].

### IncorporacionActivoLine
Clase: `exotic.app.planta.model.activos.fijos.gestion.IncorporacionActivoLine`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/gestion/IncorporacionActivoLine.java`
Tabla declarada y corroborada en DDL: `incorporacion_activo_line`.
Atributos: lineaId:Long [PK]; descripcion:String; cantidad:Integer; valorUnitario:BigDecimal; valorTotal:BigDecimal; ubicacionInicial:String; vidaUtilMeses:Integer; metodoDepreciacion:String.
Relaciones: incorporacionHeader -> IncorporacionActivoHeader [ManyToOne]; activoFijo -> ActivoFijo [OneToOne].

### MantenimientoActivo
Clase: `exotic.app.planta.model.activos.fijos.gestion.MantenimientoActivo`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/gestion/MantenimientoActivo.java`
Tabla declarada y corroborada en DDL: `mantenimiento_activo`.
Atributos: id:Long [PK]; fechaMantenimiento:LocalDateTime; tipo:TipoMantenimiento; descripcion:String; costo:BigDecimal; estado:EstadoMantenimiento; observaciones:String.
Relaciones: activoFijo -> ActivoFijo [ManyToOne]; responsable -> IntegrantePersonal [ManyToOne].

### TrasladoActivo
Clase: `exotic.app.planta.model.activos.fijos.gestion.TrasladoActivo`
Fuente: `src/main/java/exotic/app/planta/model/activos/fijos/gestion/TrasladoActivo.java`
Tabla declarada y corroborada en DDL: `traslado_activo`.
Atributos: id:Long [PK]; fechaTraslado:LocalDateTime; ubicacionOrigen:String; ubicacionDestino:String; motivo:String; observaciones:String.
Relaciones: activoFijo -> ActivoFijo [ManyToOne]; responsableAnterior -> IntegrantePersonal [ManyToOne]; responsableNuevo -> IntegrantePersonal [ManyToOne].

## Dominio: calidad

### ControlProcesoCaracteristica
Clase: `exotic.app.planta.model.calidad.ControlProcesoCaracteristica`
Fuente: `src/main/java/exotic/app/planta/model/calidad/ControlProcesoCaracteristica.java`
Tabla declarada y corroborada en DDL: `calidad_control_proceso_caracteristica`.
Atributos: id:Long [PK]; nombre:String; tipo:TipoCaracteristicaControlProceso; unidad:String; orden:Integer; cantidadMuestras:Integer; unidadesPorMuestra:Integer; limiteInferior:Double; limiteSuperior:Double.
Relaciones: plantilla -> ControlProcesoPlantilla [ManyToOne].

### ControlProcesoEjecucion
Clase: `exotic.app.planta.model.calidad.ControlProcesoEjecucion`
Fuente: `src/main/java/exotic/app/planta/model/calidad/ControlProcesoEjecucion.java`
Tabla declarada y corroborada en DDL: `calidad_control_proceso_ejecucion`.
Atributos: id:Long [PK]; resultado:ResultadoControlProceso; fechaRegistro:LocalDateTime; observaciones:String.
Relaciones: plantilla -> ControlProcesoPlantilla [ManyToOne]; lote -> Lote [ManyToOne]; batchRecord -> BatchRecord [ManyToOne]; batchRecordEtapa -> BatchRecordEtapa [ManyToOne]; muestras -> ControlProcesoMuestra [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### ControlProcesoLectura
Clase: `exotic.app.planta.model.calidad.ControlProcesoLectura`
Fuente: `src/main/java/exotic/app/planta/model/calidad/ControlProcesoLectura.java`
Tabla declarada y corroborada en DDL: `calidad_control_proceso_lectura`.
Atributos: id:Long [PK]; indiceUnidad:Integer; valorNumerico:Double; valorBooleano:Boolean.
Relaciones: muestra -> ControlProcesoMuestra [ManyToOne].

### ControlProcesoMuestra
Clase: `exotic.app.planta.model.calidad.ControlProcesoMuestra`
Fuente: `src/main/java/exotic/app/planta/model/calidad/ControlProcesoMuestra.java`
Tabla declarada y corroborada en DDL: `calidad_control_proceso_muestra`.
Atributos: id:Long [PK]; numeroMuestra:Integer.
Relaciones: ejecucion -> ControlProcesoEjecucion [ManyToOne]; caracteristica -> ControlProcesoCaracteristica [ManyToOne]; lecturas -> ControlProcesoLectura [OneToMany].

### ControlProcesoPlantilla
Clase: `exotic.app.planta.model.calidad.ControlProcesoPlantilla`
Fuente: `src/main/java/exotic/app/planta/model/calidad/ControlProcesoPlantilla.java`
Tabla declarada y corroborada en DDL: `calidad_control_proceso_plantilla`.
Atributos: id:Long [PK]; version:Integer; estado:EstadoControlProcesoPlantilla.
Relaciones: areaOperativa -> AreaOperativa [ManyToOne]; caracteristicas -> ControlProcesoCaracteristica [OneToMany].

## Dominio: compras

### ContactoProveedor
Clase: `exotic.app.planta.model.compras.ContactoProveedor`
Fuente: `src/main/java/exotic/app/planta/model/compras/ContactoProveedor.java`
Tabla declarada y corroborada en DDL: `contacto_proveedor`.
Atributos: contactoId:int [PK]; fullName:String; cargo:String; cel:String; email:String.
Relaciones: proveedor -> Proveedor [ManyToOne].

### FacturaCompra
Clase: `exotic.app.planta.model.compras.FacturaCompra`
Fuente: `src/main/java/exotic/app/planta/model/compras/FacturaCompra.java`
Tabla declarada y corroborada en DDL: `facturas_compras`.
Atributos: facturaCompraId:int [PK]; fechaCompra:LocalDateTime; subTotal:int; iva19:int; totalPagar:int; condicionPago:String; plazoPago:int; estadoPago:int.
Relaciones: proveedor -> Proveedor [ManyToOne]; itemsCompra -> ItemFacturaCompra [OneToMany].

### ItemFacturaCompra
Clase: `exotic.app.planta.model.compras.ItemFacturaCompra`
Fuente: `src/main/java/exotic/app/planta/model/compras/ItemFacturaCompra.java`
Tabla declarada y corroborada en DDL: `items_factura_compra`.
Atributos: itemCompraId:int [PK]; cantidad:double; precioCompra:int.
Relaciones: facturaCompra -> FacturaCompra [ManyToOne]; material -> Material [ManyToOne].

### ItemOrdenCompra
Clase: `exotic.app.planta.model.compras.ItemOrdenCompra`
Fuente: `src/main/java/exotic/app/planta/model/compras/ItemOrdenCompra.java`
Tabla declarada y corroborada en DDL: `item_orden_compra`.
Atributos: itemOrdenId:int [PK]; cantidad:int; precioUnitario:int; ivaCOP:int; subTotal:int; cantidadCorrecta:int; precioCorrecto:int.
Relaciones: ordenCompraMateriales -> OrdenCompraMateriales [ManyToOne]; material -> Material [ManyToOne].

### OrdenCompraMateriales
Clase: `exotic.app.planta.model.compras.OrdenCompraMateriales`
Fuente: `src/main/java/exotic/app/planta/model/compras/OrdenCompraMateriales.java`
Tabla declarada y corroborada en DDL: `orden_compra`.
Atributos: ordenCompraId:int [PK]; fechaEmision:LocalDateTime; fechaLiberacion:LocalDateTime; fechaEnvioProveedor:LocalDateTime; fechaVencimiento:LocalDateTime; fechaRecepcionCompleta:LocalDateTime; fechaCierre:LocalDateTime; origenCierre:OrigenCierreOcm; subTotal:long; ivaCOP:long; totalPagar:long; condicionPago:String; tiempoEntrega:String; plazoPago:int; estado:int; facturaCompraId:Integer; divisas:DIVISAS; trm:double; observaciones:String.
Relaciones: empresaIdentidadLegalVersion -> EmpresaIdentidadLegalVersion [ManyToOne]; empresaLogoDocumentalVersion -> EmpresaLogoDocumentalVersion [ManyToOne]; proveedor -> Proveedor [ManyToOne]; itemsOrdenCompra -> ItemOrdenCompra [OneToMany].
Campos o relaciones omitidos por politica: 5; no inferir sus valores ni recorrerlos.

### LeadTimeProveedorKPI
Clase: `exotic.app.planta.model.compras.proveedor.metricas.LeadTimeProveedorKPI`
Fuente: `src/main/java/exotic/app/planta/model/compras/proveedor/metricas/LeadTimeProveedorKPI.java`
Tabla declarada y corroborada en DDL: `lead_time_proveedor_kpi`.
Atributos: id:Long [PK]; fechaCorte:LocalDate; ventanaDias:Integer; leadTimeCalculado:Double; observaciones:Integer; ordenesConsideradas:Integer; calculadoEn:LocalDateTime; estado:EstadoLeadTimeProveedorKPI; motivoEstado:String; ultimaEvaluacionEn:LocalDateTime; ultimaFechaCorteEvaluada:LocalDate.
Relaciones: proveedor -> Proveedor [ManyToOne].

### Proveedor
Clase: `exotic.app.planta.model.compras.Proveedor`
Fuente: `src/main/java/exotic/app/planta/model/compras/Proveedor.java`
Tabla declarada y corroborada en DDL: `proveedores`.
Atributos: pk:Long [PK]; id:String; tipoIdentificacion:int; nombre:String; direccion:String; regimenTributario:int; ciudad:String; departamento:String; observacion:String; fechaRegistro:LocalDateTime; condicionPago:String; leadTimeNegociado:Integer; categorias:int[]; limiteRecepcionesParcialesOcm:Integer.
Relaciones: contactos -> ContactoProveedor [OneToMany].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

## Dominio: contabilidad

### AsientoContable
Clase: `exotic.app.planta.model.contabilidad.AsientoContable`
Fuente: `src/main/java/exotic/app/planta/model/contabilidad/AsientoContable.java`
Tabla declarada y corroborada en DDL: `asiento_contable`.
Atributos: id:Long [PK]; fecha:LocalDateTime; descripcion:String; modulo:String; documentoOrigen:String; estado:EstadoAsiento.
Relaciones: periodoContable -> PeriodoContable [ManyToOne]; lineas -> LineaAsientoContable [OneToMany].

### CuentaContable
Clase: `exotic.app.planta.model.contabilidad.CuentaContable`
Fuente: `src/main/java/exotic/app/planta/model/contabilidad/CuentaContable.java`
Tabla declarada y corroborada en DDL: `cuenta`.
Atributos: codigo:String [PK]; nombre:String; tipo:TipoCuenta; saldoNormal:SaldoNormal; cuentaControl:boolean.
Relaciones: sin relaciones propias publicadas.

### LineaAsientoContable
Clase: `exotic.app.planta.model.contabilidad.LineaAsientoContable`
Fuente: `src/main/java/exotic/app/planta/model/contabilidad/LineaAsientoContable.java`
Tabla declarada y corroborada en DDL: `linea_asiento_contable`.
Atributos: id:Long [PK]; cuentaCodigo:String; debito:BigDecimal; credito:BigDecimal; descripcion:String.
Relaciones: asientoContable -> AsientoContable [ManyToOne].

### PeriodoContable
Clase: `exotic.app.planta.model.contabilidad.PeriodoContable`
Fuente: `src/main/java/exotic/app/planta/model/contabilidad/PeriodoContable.java`
Tabla declarada y corroborada en DDL: `periodo_contable`.
Atributos: id:Long [PK]; fechaInicio:LocalDate; fechaFin:LocalDate; nombre:String; estado:EstadoPeriodo.
Relaciones: asientos -> AsientoContable [OneToMany].

## Dominio: controles

### AplicabilidadPlanControl
Clase: `exotic.app.planta.model.controles.AplicabilidadPlanControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/AplicabilidadPlanControl.java`
Tabla declarada y corroborada en DDL: `control_plan_aplicabilidad`.
Atributos: id:Long [PK]; tipoOrden:TipoOrdenControl; puntoAplicacion:PuntoAplicacionControl; frontendNodeId:String; momento:MomentoControl; puntoExigencia:PuntoExigenciaControl; legadoGlobal:boolean.
Relaciones: version -> VersionPlanControl [ManyToOne]; producto -> Producto [ManyToOne]; categoria -> Categoria [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne]; proceso -> ProcesoProduccion [ManyToOne]; productosExcluidos -> Producto [ManyToMany].

### CaracteristicaPlanControl
Clase: `exotic.app.planta.model.controles.CaracteristicaPlanControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/CaracteristicaPlanControl.java`
Tabla declarada y corroborada en DDL: `control_plan_caracteristica`.
Atributos: id:Long [PK]; nombre:String; tipo:TipoCaracteristicaControl; orden:Integer; cantidadMuestras:Integer; unidadesPorMuestra:Integer; escalaVisible:Integer; objetivo:BigDecimal; limiteInferior:BigDecimal; limiteSuperior:BigDecimal; valorBooleanoEsperado:Boolean; magnitudCodigoSnapshot:String; magnitudNombreSnapshot:String; magnitudSimboloSnapshot:String; unidadCodigoSnapshot:String; unidadNombreSnapshot:String; unidadSimboloSnapshot:String; legadoSinLimites:boolean; requiereDepuracion:boolean.
Relaciones: version -> VersionPlanControl [ManyToOne]; magnitud -> MagnitudControl [ManyToOne]; unidad -> UnidadControl [ManyToOne]; legacyCaracteristica -> ControlProcesoCaracteristica [OneToOne].

### ControlRequerido
Clase: `exotic.app.planta.model.controles.ControlRequerido`
Fuente: `src/main/java/exotic/app/planta/model/controles/ControlRequerido.java`
Tabla declarada y corroborada en DDL: `control_requerido`.
Atributos: id:Long [PK]; origen:OrigenControlRequerido; estado:EstadoControlRequerido; cicloRevisionNumero:Integer; requiereRepeticion:boolean; requiereRevalidacion:boolean; creadoEn:LocalDateTime; planCodigoSnapshot:String; planNombreSnapshot:String; ambitoSnapshot:AmbitoControl; versionNumeroSnapshot:Integer; productoIdSnapshot:String; productoNombreSnapshot:String; categoriaIdSnapshot:Integer; categoriaNombreSnapshot:String; tipoOrdenSnapshot:TipoOrdenControl; puntoAplicacionSnapshot:PuntoAplicacionControl; areaOperativaIdSnapshot:Integer; areaOperativaNombreSnapshot:String; procesoIdSnapshot:Integer; procesoNombreSnapshot:String; manufacturingVersionIdSnapshot:Long; rutaVersionIdSnapshot:Long; rutaNodoIdSnapshot:Long; ordenFabricacionOperacionIdSnapshot:Long; frontendNodeIdSnapshot:String; nodoNombreSnapshot:String; momentoSnapshot:MomentoControl; puntoExigenciaSnapshot:PuntoExigenciaControl.
Relaciones: versionPlan -> VersionPlanControl [ManyToOne]; aplicabilidad -> AplicabilidadPlanControl [ManyToOne]; lote -> Lote [ManyToOne]; batchRecord -> BatchRecord [ManyToOne]; batchRecordEtapa -> BatchRecordEtapa [ManyToOne]; legacyEjecucion -> ControlProcesoEjecucion [OneToOne]; ejecuciones -> EjecucionControl [OneToMany].

### DesviacionControl
Clase: `exotic.app.planta.model.controles.DesviacionControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/DesviacionControl.java`
Tabla declarada y corroborada en DDL: `control_desviacion`.
Atributos: id:Long [PK]; ambito:AmbitoControl; estado:EstadoDesviacionControl; disposicion:DisposicionDesviacionControl; investigacion:String; resolucion:String; justificacionDisposicion:String; abiertaEn:LocalDateTime; resueltaEn:LocalDateTime; cerradaEn:LocalDateTime.
Relaciones: controlRequerido -> ControlRequerido [ManyToOne]; ejecucionOrigen -> EjecucionControl [OneToOne].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

### EjecucionControl
Clase: `exotic.app.planta.model.controles.EjecucionControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/EjecucionControl.java`
Tabla declarada y corroborada en DDL: `control_ejecucion`.
Atributos: id:Long [PK]; fechaRegistro:LocalDateTime; resultado:ResultadoEjecucionControl; observaciones:String; motivoRepeticion:String.
Relaciones: controlRequerido -> ControlRequerido [ManyToOne]; repeticionDe -> EjecucionControl [ManyToOne]; legacyEjecucion -> ControlProcesoEjecucion [OneToOne]; muestras -> MuestraEjecucionControl [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### LecturaEjecucionControl
Clase: `exotic.app.planta.model.controles.LecturaEjecucionControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/LecturaEjecucionControl.java`
Tabla declarada y corroborada en DDL: `control_ejecucion_lectura`.
Atributos: id:Long [PK]; indiceUnidad:Integer; valorNumerico:BigDecimal; valorBooleano:Boolean.
Relaciones: muestra -> MuestraEjecucionControl [ManyToOne].

### MagnitudControl
Clase: `exotic.app.planta.model.controles.MagnitudControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/MagnitudControl.java`
Tabla declarada y corroborada en DDL: `control_magnitud`.
Atributos: id:Long [PK]; codigo:String; nombre:String; simbolo:String; dimension:String; activo:boolean; creadoEn:LocalDateTime.
Relaciones: sin relaciones propias publicadas.

### MuestraEjecucionControl
Clase: `exotic.app.planta.model.controles.MuestraEjecucionControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/MuestraEjecucionControl.java`
Tabla declarada y corroborada en DDL: `control_ejecucion_muestra`.
Atributos: id:Long [PK]; numeroMuestra:Integer.
Relaciones: ejecucion -> EjecucionControl [ManyToOne]; caracteristica -> CaracteristicaPlanControl [ManyToOne]; lecturas -> LecturaEjecucionControl [OneToMany].

### PlanControl
Clase: `exotic.app.planta.model.controles.PlanControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/PlanControl.java`
Tabla declarada y corroborada en DDL: `control_plan`.
Atributos: id:Long [PK]; codigo:String; nombre:String; ambito:AmbitoControl; creadoEn:LocalDateTime.
Relaciones: versiones -> VersionPlanControl [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### RevalidacionControl
Clase: `exotic.app.planta.model.controles.RevalidacionControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/RevalidacionControl.java`
Tabla declarada y corroborada en DDL: `control_revalidacion`.
Atributos: id:Long [PK]; cicloRevisionNumero:Integer; justificacion:String; confirmadaEn:LocalDateTime.
Relaciones: controlRequerido -> ControlRequerido [ManyToOne]; ejecucionRevalidada -> EjecucionControl [ManyToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### UnidadControl
Clase: `exotic.app.planta.model.controles.UnidadControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/UnidadControl.java`
Tabla declarada y corroborada en DDL: `control_unidad`.
Atributos: id:Long [PK]; codigo:String; nombre:String; simbolo:String; dimension:String; activo:boolean; creadoEn:LocalDateTime.
Relaciones: sin relaciones propias publicadas.

### VersionPlanControl
Clase: `exotic.app.planta.model.controles.VersionPlanControl`
Fuente: `src/main/java/exotic/app/planta/model/controles/VersionPlanControl.java`
Tabla declarada y corroborada en DDL: `control_plan_version`.
Atributos: id:Long [PK]; numero:Integer; estado:EstadoVersionPlanControl; proposito:String; motivoCambio:String; responsableEjecucion:String; responsableRevision:String; responsableDisposicion:String; creadaEn:LocalDateTime; publicadaEn:LocalDateTime; retiradaEn:LocalDateTime.
Relaciones: plan -> PlanControl [ManyToOne]; legacyPlantilla -> ControlProcesoPlantilla [OneToOne]; aplicabilidades -> AplicabilidadPlanControl [OneToMany]; caracteristicas -> CaracteristicaPlanControl [OneToMany].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

## Dominio: cronograma

### AvanceTarea
Clase: `exotic.app.planta.model.cronograma.AvanceTarea`
Fuente: `src/main/java/exotic/app/planta/model/cronograma/AvanceTarea.java`
Tabla fisica no resuelta; referencia logica de entidad.
Atributos: id:Long [PK]; descripcionAvance:String.
Relaciones: tarea -> Tarea [ManyToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### Tarea
Clase: `exotic.app.planta.model.cronograma.Tarea`
Fuente: `src/main/java/exotic/app/planta/model/cronograma/Tarea.java`
Tabla fisica no resuelta; referencia logica de entidad.
Atributos: id:Long [PK]; fechaAsignacion:LocalDateTime; fechaReporteCumplimiento:LocalDateTime; fechaCierre:LocalDateTime; descripcionTarea:String.
Relaciones: avances -> AvanceTarea [OneToMany].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

## Dominio: empresa

### EmpresaIdentidadLegalVersion
Clase: `exotic.app.planta.model.empresa.EmpresaIdentidadLegalVersion`
Fuente: `src/main/java/exotic/app/planta/model/empresa/EmpresaIdentidadLegalVersion.java`
Tabla declarada y corroborada en DDL: `empresa_identidad_legal_version`.
Atributos: id:Long [PK]; version:Integer; estado:Estado; razonSocial:String; nombreComercial:String; tipoIdentificacion:String; numeroIdentificacion:String; digitoVerificacion:String; telefonoPrincipal:String; emailPrincipal:String; vigenteDesde:LocalDateTime; vigenteHasta:LocalDateTime; creadoEn:LocalDateTime; motivoCambio:String.
Relaciones: sin relaciones propias publicadas.
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### EmpresaLogoDocumentalVersion
Clase: `exotic.app.planta.model.empresa.EmpresaLogoDocumentalVersion`
Fuente: `src/main/java/exotic/app/planta/model/empresa/EmpresaLogoDocumentalVersion.java`
Tabla declarada y corroborada en DDL: `empresa_logo_documental_version`.
Atributos: id:Long [PK]; version:Integer; estado:Estado; contentType:String; tamanoBytes:Long; anchoPx:Integer; altoPx:Integer; vigenteDesde:LocalDateTime; vigenteHasta:LocalDateTime; creadoEn:LocalDateTime; motivoCambio:String.
Relaciones: sin relaciones propias publicadas.
Campos o relaciones omitidos por politica: 4; no inferir sus valores ni recorrerlos.

### JornadaLaboralBloque
Clase: `exotic.app.planta.model.empresa.JornadaLaboralBloque`
Fuente: `src/main/java/exotic/app/planta/model/empresa/JornadaLaboralBloque.java`
Tabla declarada y corroborada en DDL: `jornada_laboral_bloque`.
Atributos: id:Long [PK]; diaSemana:Integer; orden:Integer; horaInicio:LocalTime; horaFin:LocalTime.
Relaciones: jornadaLaboralVersion -> JornadaLaboralVersion [ManyToOne].

### JornadaLaboralVersion
Clase: `exotic.app.planta.model.empresa.JornadaLaboralVersion`
Fuente: `src/main/java/exotic/app/planta/model/empresa/JornadaLaboralVersion.java`
Tabla declarada y corroborada en DDL: `jornada_laboral_version`.
Atributos: id:Long [PK]; version:Integer; estado:Estado; vigenteDesde:LocalDateTime; vigenteHasta:LocalDateTime; creadoEn:LocalDateTime; motivoCambio:String.
Relaciones: bloques -> JornadaLaboralBloque [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

## Dominio: inventarios

### Lote
Clase: `exotic.app.planta.model.inventarios.Lote`
Fuente: `src/main/java/exotic/app/planta/model/inventarios/Lote.java`
Tabla declarada y corroborada en DDL: `lote`.
Atributos: id:Long [PK]; batchNumber:String; productionDate:LocalDate; expirationDate:LocalDate; vidaUtilCantidadAplicada:Integer; vidaUtilUnidadAplicada:UnidadTiempoVencimiento; estadoCalidad:EstadoCalidadLote.
Relaciones: producto -> Producto [ManyToOne]; ordenCompraMateriales -> OrdenCompraMateriales [ManyToOne]; ordenProduccion -> OrdenProduccion [ManyToOne]; ordenFabricacion -> OrdenFabricacion [ManyToOne].

### Movimiento
Clase: `exotic.app.planta.model.inventarios.Movimiento`
Fuente: `src/main/java/exotic/app/planta/model/inventarios/Movimiento.java`
Tabla declarada y corroborada en DDL: `movimientos`.
Atributos: movimientoId:int [PK]; cantidad:double; tipoMovimiento:TipoMovimiento; afectaInventario:boolean; almacen:Almacen; fechaMovimiento:LocalDateTime.
Relaciones: transaccionAlmacen -> TransaccionAlmacen [ManyToOne]; producto -> Producto [ManyToOne]; lote -> Lote [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne].

### TransaccionAlmacen
Clase: `exotic.app.planta.model.inventarios.TransaccionAlmacen`
Fuente: `src/main/java/exotic/app/planta/model/inventarios/TransaccionAlmacen.java`
Tabla declarada y corroborada en DDL: `transaccion_almacen`.
Atributos: transaccionId:int [PK]; fechaTransaccion:LocalDateTime; estadoContable:EstadoContable; tipoEntidadCausante:TipoEntidadCausante; idEntidadCausante:int; observaciones:String; causaAjuste:CausaAjusteInventario.
Relaciones: movimientosTransaccion -> Movimiento [OneToMany]; asientoContable -> AsientoContable [OneToOne].
Campos o relaciones omitidos por politica: 5; no inferir sus valores ni recorrerlos.

## Dominio: master

### MasterDirective
Clase: `exotic.app.planta.model.master.configs.MasterDirective`
Fuente: `src/main/java/exotic/app/planta/model/master/configs/MasterDirective.java`
Tabla fisica no resuelta; referencia logica de entidad.
Atributos: id:Long [PK]; nombre:String; resumen:String; valor:String; tipoDato:TipoDato; grupo:GRUPO; ayuda:String.
Relaciones: sin relaciones propias publicadas.

## Dominio: notificaciones

### MaestraNotificacion
Clase: `exotic.app.planta.model.notificaciones.MaestraNotificacion`
Fuente: `src/main/java/exotic/app/planta/model/notificaciones/MaestraNotificacion.java`
Tabla fisica no resuelta; referencia logica de entidad.
Atributos: id:int [PK]; nombre:String; descripcion:String.
Relaciones: sin relaciones propias publicadas.
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

## Dominio: organigrama

### Cargo
Clase: `exotic.app.planta.model.organigrama.Cargo`
Fuente: `src/main/java/exotic/app/planta/model/organigrama/Cargo.java`
Tabla fisica no resuelta; referencia logica de entidad.
Atributos: idCargo:String [PK]; tituloCargo:String; descripcionCargo:String; departamento:String; posicionX:double; posicionY:double; nivel:int; jefeInmediato:String.
Relaciones: sin relaciones propias publicadas.
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### MisionVisionValor
Clase: `exotic.app.planta.model.organigrama.MisionVisionValor`
Fuente: `src/main/java/exotic/app/planta/model/organigrama/MisionVisionValor.java`
Tabla declarada y corroborada en DDL: `mision_vision_valor`.
Atributos: id:Long [PK]; orden:Integer; titulo:String; descripcionHtml:String.
Relaciones: misionVisionVersion -> MisionVisionVersion [ManyToOne].

### MisionVisionVersion
Clase: `exotic.app.planta.model.organigrama.MisionVisionVersion`
Fuente: `src/main/java/exotic/app/planta/model/organigrama/MisionVisionVersion.java`
Tabla declarada y corroborada en DDL: `mision_vision_version`.
Atributos: id:Long [PK]; version:Integer; estado:Estado; misionHtml:String; visionHtml:String; vigenteDesde:LocalDateTime; vigenteHasta:LocalDateTime; creadoEn:LocalDateTime; motivoCambio:String.
Relaciones: origenVersion -> MisionVisionVersion [ManyToOne]; valores -> MisionVisionValor [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### OrganigramaEstado
Clase: `exotic.app.planta.model.organigrama.OrganigramaEstado`
Fuente: `src/main/java/exotic/app/planta/model/organigrama/OrganigramaEstado.java`
Tabla declarada y corroborada en DDL: `organigrama_estado`.
Atributos: id:Short [PK]; revision:Long; actualizadoEn:LocalDateTime.
Relaciones: sin relaciones propias publicadas.
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

## Dominio: organizacion

### AreaOperativa
Clase: `exotic.app.planta.model.organizacion.AreaOperativa`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/AreaOperativa.java`
Tabla declarada y corroborada en DDL: `area_operativa`.
Atributos: areaId:int [PK]; nombre:String; descripcion:String; requiereDespeje:boolean; requiereRevisionDespeje:boolean; despejeLineaExclusivaConfirmada:boolean; despejeRevisionContexto:long; despejeAntecedenteConocido:boolean; despejeUltimoInicioEn:java.time.LocalDateTime; despejeVersion:long [version]; visibilidadMps:VisibilidadMps; alcanceMps:AlcanceMps; alcanceEjecucion:AlcanceEjecucion.
Relaciones: despejeUltimoLote -> Lote [ManyToOne]; despejeOcupanteSeguimiento -> SeguimientoOrdenArea [ManyToOne]; despejeOcupanteOperacion -> OrdenFabricacionOperacion [ManyToOne]; categoriasHabilitadas -> Categoria [ManyToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### AreaOperativaCategoriaUnidadMedida
Clase: `exotic.app.planta.model.organizacion.AreaOperativaCategoriaUnidadMedida`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/AreaOperativaCategoriaUnidadMedida.java`
Tabla declarada y corroborada en DDL: `area_operativa_categoria_unidad_medida`.
Atributos: id:Long [PK]; factorLote:BigDecimal.
Relaciones: areaOperativa -> AreaOperativa [ManyToOne]; categoria -> Categoria [ManyToOne]; unidadMedida -> UnidadMedidaAreaOperativa [ManyToOne].

### DocTranDePersonal
Clase: `exotic.app.planta.model.organizacion.personal.DocTranDePersonal`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/personal/DocTranDePersonal.java`
Tabla declarada y corroborada en DDL: `doc_tran_de_personal`.
Atributos: id:Long [PK]; tipoDocTran:TipoDocTran; fechaHora:LocalDateTime; descripcion:String.
Relaciones: idIntegrante -> IntegrantePersonal [ManyToOne].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

### IntegrantePersonal
Clase: `exotic.app.planta.model.organizacion.personal.IntegrantePersonal`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/personal/IntegrantePersonal.java`
Tabla declarada y corroborada en DDL: `integrante_personal`.
Atributos: id:long [PK]; fechaIngreso:LocalDate; cargo:String; departamento:Departamento; centroDeCosto:String; centroDeProduccion:String; estado:Estado.
Relaciones: documentos -> DocTranDePersonal [OneToMany].
Campos o relaciones omitidos por politica: 13; no inferir sus valores ni recorrerlos.

### ConfiguracionPermisoPersonal
Clase: `exotic.app.planta.model.organizacion.personal.permiso.ConfiguracionPermisoPersonal`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/personal/permiso/ConfiguracionPermisoPersonal.java`
Tabla declarada y corroborada en DDL: `configuracion_permiso_personal`.
Atributos: empleadoId:Long [PK]; version:Long [version]; actualizadoEn:LocalDateTime.
Relaciones: empleado -> IntegrantePersonal [OneToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### EventoPermisoPersonal
Clase: `exotic.app.planta.model.organizacion.personal.permiso.EventoPermisoPersonal`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/personal/permiso/EventoPermisoPersonal.java`
Tabla declarada y corroborada en DDL: `evento_permiso_personal`.
Atributos: id:Long [PK]; empleadoId:Long; tipo:String; creadoEn:LocalDateTime; detalle:String.
Relaciones: solicitud -> SolicitudPermisoPersonal [ManyToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### FirmaPermisoPersonal
Clase: `exotic.app.planta.model.organizacion.personal.permiso.FirmaPermisoPersonal`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/personal/permiso/FirmaPermisoPersonal.java`
Tabla declarada y corroborada en DDL: `firma_permiso_personal`.
Atributos: id:Long [PK]; rol:String; firmadoEn:LocalDateTime.
Relaciones: solicitud -> SolicitudPermisoPersonal [ManyToOne].
Campos o relaciones omitidos por politica: 8; no inferir sus valores ni recorrerlos.

### SolicitudPermisoPersonal
Clase: `exotic.app.planta.model.organizacion.personal.permiso.SolicitudPermisoPersonal`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/personal/permiso/SolicitudPermisoPersonal.java`
Tabla declarada y corroborada en DDL: `solicitud_permiso_personal`.
Atributos: id:Long [PK]; version:Long [version]; estado:Estado; empleadoCargo:String; fechaSolicitud:LocalDate; proceso:String; motivo:Motivo; descripcion:String; mecanismoRecuperacion:String; fechaDesde:LocalDate; fechaHasta:LocalDate; cantidad:BigDecimal; unidad:Unidad; creadoEn:LocalDateTime; actualizadoEn:LocalDateTime; presentadoEn:LocalDateTime; resueltoEn:LocalDateTime; motivoDecision:String; motivoAnulacion:String.
Relaciones: empleado -> IntegrantePersonal [ManyToOne].
Campos o relaciones omitidos por politica: 12; no inferir sus valores ni recorrerlos.

### RegistroHoraExtra
Clase: `exotic.app.planta.model.organizacion.personal.RegistroHoraExtra`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/personal/RegistroHoraExtra.java`
Tabla declarada y corroborada en DDL: `registro_hora_extra`.
Atributos: id:Long [PK]; fecha:LocalDate; horaInicio:LocalTime; horaFin:LocalTime; minutos:Integer; motivo:String; observaciones:String; estado:Estado; fechaRegistro:LocalDateTime; fechaDecision:LocalDateTime; motivoRechazoOAnulacion:String.
Relaciones: integrante -> IntegrantePersonal [ManyToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### RegistroVisitante
Clase: `exotic.app.planta.model.organizacion.personal.RegistroVisitante`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/personal/RegistroVisitante.java`
Tabla declarada y corroborada en DDL: `registro_visitante`.
Atributos: id:Long [PK]; fechaRegistro:Instant.
Relaciones: sin relaciones propias publicadas.
Campos o relaciones omitidos por politica: 4; no inferir sus valores ni recorrerlos.

### UnidadMedidaAreaOperativa
Clase: `exotic.app.planta.model.organizacion.UnidadMedidaAreaOperativa`
Fuente: `src/main/java/exotic/app/planta/model/organizacion/UnidadMedidaAreaOperativa.java`
Tabla declarada y corroborada en DDL: `unidad_medida_area_operativa`.
Atributos: id:Long [PK]; nombre:String; unidadRelacion:UnidadRelacionAreaOperativa; relacionEstandar:BigDecimal.
Relaciones: areaOperativa -> AreaOperativa [ManyToOne].

## Dominio: produccion

### AreaOperativaRuidoMuestra
Clase: `exotic.app.planta.model.produccion.AreaOperativaRuidoMuestra`
Fuente: `src/main/java/exotic/app/planta/model/produccion/AreaOperativaRuidoMuestra.java`
Tabla declarada y corroborada en DDL: `area_operativa_ruido_muestra`.
Atributos: id:Long [PK]; fechaMuestra:LocalDateTime; ruidoDb:Double; rms:Double; duracionMs:Integer; sampleRate:Integer; createdAt:LocalDateTime.
Relaciones: areaOperativa -> AreaOperativa [ManyToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### BatchRecord
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecord`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecord.java`
Tabla declarada y corroborada en DDL: `batch_record`.
Atributos: id:Long [PK]; codigo:String; estado:EstadoBatchRecord; revisionDocumental:int; cantidadPlanificada:BigDecimal; cantidadObtenida:BigDecimal; unidadMedida:String; creadoEn:LocalDateTime; iniciadoEn:LocalDateTime; enviadoRevisionEn:LocalDateTime; cicloRevisionActual:long; cerradoEn:LocalDateTime; observaciones:String; estadoSincronizacion:EstadoSincronizacionBatchRecord; sincronizadoEn:LocalDateTime; advertenciasDocumentales:String; version:long [version].
Relaciones: ordenProduccion -> OrdenProduccion [OneToOne]; ordenFabricacion -> OrdenFabricacion [OneToOne]; loteResultado -> Lote [OneToOne]; productoResultado -> Producto [ManyToOne]; manufacturingVersion -> ManufacturingVersions [ManyToOne]; consumos -> BatchRecordConsumo [OneToMany]; etapas -> BatchRecordEtapa [OneToMany]; controlesProceso -> ControlProcesoEjecucion [OneToMany]; desviaciones -> BatchRecordDesviacion [OneToMany]; firmas -> BatchRecordFirma [OneToMany]; revisiones -> BatchRecordRevision [OneToMany]; correcciones -> BatchRecordCorreccion [OneToMany]; decisionesCalidad -> BatchRecordDecisionCalidad [OneToMany].
Campos o relaciones omitidos por politica: 4; no inferir sus valores ni recorrerlos.

### BatchRecordConsumo
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecordConsumo`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecordConsumo.java`
Tabla declarada y corroborada en DDL: `batch_record_consumo`.
Atributos: id:Long [PK]; tipo:TipoRegistroConsumoBatchRecord; cantidad:BigDecimal; unidadMedida:String; registradoEn:LocalDateTime; observaciones:String.
Relaciones: batchRecord -> BatchRecord [ManyToOne]; producto -> Producto [ManyToOne]; loteOrigen -> Lote [ManyToOne]; movimiento -> Movimiento [OneToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### BatchRecordCorreccion
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecordCorreccion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecordCorreccion.java`
Tabla declarada y corroborada en DDL: `batch_record_correccion`.
Atributos: id:Long [PK]; motivo:String; corregidaEn:LocalDateTime.
Relaciones: batchRecord -> BatchRecord [ManyToOne]; etapa -> BatchRecordEtapa [ManyToOne]; eventoCorreccion -> SeguimientoOrdenAreaEvento [OneToOne]; eventoRevertido -> SeguimientoOrdenAreaEvento [ManyToOne]; ordenFabricacionEventoCorreccion -> OrdenFabricacionOperacionEvento [OneToOne]; ordenFabricacionEventoRevertido -> OrdenFabricacionOperacionEvento [ManyToOne]; revision -> BatchRecordRevision [ManyToOne]; firma -> BatchRecordFirma [OneToOne].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

### BatchRecordDecisionCalidad
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecordDecisionCalidad`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecordDecisionCalidad.java`
Tabla declarada y corroborada en DDL: `batch_record_decision_calidad`.
Atributos: id:Long [PK]; decision:DecisionCalidadBatchRecord; motivo:String; decididaEn:LocalDateTime.
Relaciones: batchRecord -> BatchRecord [ManyToOne]; revision -> BatchRecordRevision [ManyToOne]; cicloRevision -> CicloRevisionBatchRecord [ManyToOne]; firma -> BatchRecordFirma [OneToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### BatchRecordDesviacion
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecordDesviacion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecordDesviacion.java`
Tabla declarada y corroborada en DDL: `batch_record_desviacion`.
Atributos: id:Long [PK]; codigo:String; descripcion:String; estado:EstadoBatchRecordDesviacion; detectadaEn:LocalDateTime; ocurridaEn:LocalDateTime; origen:OrigenDesviacionBatchRecord; accionInmediata:String; evaluacionImpacto:String; causaRaiz:String; accionesCorrectivasPreventivas:String; resolucion:String; resueltaEn:LocalDateTime; version:long [version].
Relaciones: batchRecord -> BatchRecord [ManyToOne]; etapa -> BatchRecordEtapa [ManyToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### BatchRecordEtapa
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecordEtapa`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecordEtapa.java`
Tabla declarada y corroborada en DDL: `batch_record_etapa`.
Atributos: id:Long [PK]; nombre:String; secuencia:int; estado:EstadoBatchRecordEtapa; cicloCorreccionHabilitado:Long; iniciadaEn:LocalDateTime; completadaEn:LocalDateTime; observaciones:String; version:long [version].
Relaciones: batchRecord -> BatchRecord [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne]; seguimientoOrdenArea -> SeguimientoOrdenArea [OneToOne]; ordenFabricacionOperacion -> OrdenFabricacionOperacion [OneToOne]; controlProcesoPlantilla -> ControlProcesoPlantilla [ManyToOne]; seguimientoEventoOrigen -> SeguimientoOrdenAreaEvento [OneToOne]; ordenFabricacionEventoOrigen -> OrdenFabricacionOperacionEvento [OneToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### BatchRecordFirma
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecordFirma`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecordFirma.java`
Tabla declarada y corroborada en DDL: `batch_record_firma`.
Atributos: id:Long [PK]; alcance:AlcanceFirmaBatchRecord; decision:DecisionFirmaBatchRecord; metodo:MetodoFirmaElectronica; firmadoEn:LocalDateTime; autenticadoEn:LocalDateTime; manifestacion:String; comentario:String.
Relaciones: batchRecord -> BatchRecord [ManyToOne]; etapa -> BatchRecordEtapa [ManyToOne]; seguimientoEvento -> SeguimientoOrdenAreaEvento [OneToOne]; ordenFabricacionEvento -> OrdenFabricacionOperacionEvento [OneToOne]; revision -> BatchRecordRevision [ManyToOne].
Campos o relaciones omitidos por politica: 10; no inferir sus valores ni recorrerlos.

### BatchRecordRevision
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecordRevision`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecordRevision.java`
Tabla declarada y corroborada en DDL: `batch_record_revision`.
Atributos: id:Long [PK]; numero:int; tipo:TipoRevisionBatchRecord; esquemaVersion:String; plantillaPdfVersion:String; creadaEn:LocalDateTime; motivo:String.
Relaciones: batchRecord -> BatchRecord [ManyToOne].
Campos o relaciones omitidos por politica: 6; no inferir sus valores ni recorrerlos.

### BatchRecordSeccionCorreccion
Clase: `exotic.app.planta.model.produccion.batchrecord.BatchRecordSeccionCorreccion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/BatchRecordSeccionCorreccion.java`
Tabla declarada y corroborada en DDL: `batch_record_seccion_correccion`.
Atributos: id:Long [PK]; cicloRevisionNumero:long; seccion:String; estado:EstadoSeccionCorreccionBatchRecord; solicitadaEn:LocalDateTime; atendidaEn:LocalDateTime; justificacion:String; version:long [version].
Relaciones: batchRecord -> BatchRecord [ManyToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### CicloRevisionBatchRecord
Clase: `exotic.app.planta.model.produccion.batchrecord.CicloRevisionBatchRecord`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/CicloRevisionBatchRecord.java`
Tabla declarada y corroborada en DDL: `batch_record_ciclo_revision`.
Atributos: id:Long [PK]; numero:long; origen:OrigenCicloRevisionBatchRecord; estado:EstadoCicloRevisionBatchRecord; enviadoEn:LocalDateTime; motivoEnvio:String; cerradoEn:LocalDateTime; version:long [version].
Relaciones: batchRecord -> BatchRecord [ManyToOne]; revisionEnvio -> BatchRecordRevision [ManyToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### SolicitudReaperturaRechazo
Clase: `exotic.app.planta.model.produccion.batchrecord.SolicitudReaperturaRechazo`
Fuente: `src/main/java/exotic/app/planta/model/produccion/batchrecord/SolicitudReaperturaRechazo.java`
Tabla declarada y corroborada en DDL: `batch_record_solicitud_reapertura_rechazo`.
Atributos: id:Long [PK]; cicloRevisionNumero:long; estado:EstadoSolicitudReaperturaRechazo; solicitadaEn:LocalDateTime; motivo:String; evidencia:String; alcance:String; aprobadaEn:LocalDateTime; motivoAprobacion:String; version:long [version].
Relaciones: batchRecord -> BatchRecord [ManyToOne]; revisionSolicitud -> BatchRecordRevision [ManyToOne]; firmaSolicitud -> BatchRecordFirma [OneToOne]; revisionAprobacion -> BatchRecordRevision [ManyToOne]; firmaAprobacion -> BatchRecordFirma [OneToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### CierreProduccion
Clase: `exotic.app.planta.model.produccion.CierreProduccion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/CierreProduccion.java`
Tabla declarada y corroborada en DDL: `cierre_produccion`.
Atributos: id:Long [PK]; fechaProduccion:LocalDate; cerradoEn:LocalDateTime.
Relaciones: reportes -> ReporteProduccionLote [OneToMany].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

### DespejeLineaEjecucion
Clase: `exotic.app.planta.model.produccion.despeje.DespejeLineaEjecucion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/despeje/DespejeLineaEjecucion.java`
Tabla declarada y corroborada en DDL: `despeje_linea_ejecucion`.
Atributos: id:Long [PK]; areaNombre:String; intento:int; revisionContexto:long; estado:Estado; requiereRevision:boolean; loteActualNumero:String; loteAnteriorNumero:String; antecedenteConocido:boolean; confirmaAntecedenteDesconocido:boolean; observacionAntecedente:String; observaciones:String; motivoRevision:String; motivoInvalidacion:String; creadaEn:LocalDateTime; enviadaEn:LocalDateTime; revisadaEn:LocalDateTime; utilizadaEn:LocalDateTime; version:long [version].
Relaciones: familia -> PlantillaDespeje [ManyToOne]; plantilla -> PlantillaDespejeVersion [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne]; seguimiento -> SeguimientoOrdenArea [ManyToOne]; operacion -> OrdenFabricacionOperacion [ManyToOne]; loteActual -> Lote [ManyToOne]; loteAnterior -> Lote [ManyToOne]; respuestas -> RespuestaDespeje [OneToMany].
Campos o relaciones omitidos por politica: 11; no inferir sus valores ni recorrerlos.

### ItemPlantillaDespeje
Clase: `exotic.app.planta.model.produccion.despeje.ItemPlantillaDespeje`
Fuente: `src/main/java/exotic/app/planta/model/produccion/despeje/ItemPlantillaDespeje.java`
Tabla declarada y corroborada en DDL: `despeje_plantilla_item`.
Atributos: id:Long [PK]; orden:int; texto:String; tipo:Tipo; obligatorio:boolean; permiteNa:boolean; respuestaConforme:Valor; minimo:BigDecimal; maximo:BigDecimal.
Relaciones: plantilla -> PlantillaDespejeVersion [ManyToOne].

### PlantillaDespeje
Clase: `exotic.app.planta.model.produccion.despeje.PlantillaDespeje`
Fuente: `src/main/java/exotic/app/planta/model/produccion/despeje/PlantillaDespeje.java`
Tabla declarada y corroborada en DDL: `despeje_plantilla`.
Atributos: id:Long [PK]; nombre:String; version:long [version].
Relaciones: areaOperativa -> AreaOperativa [ManyToOne].

### PlantillaDespejeVersion
Clase: `exotic.app.planta.model.produccion.despeje.PlantillaDespejeVersion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/despeje/PlantillaDespejeVersion.java`
Tabla declarada y corroborada en DDL: `despeje_plantilla_version`.
Atributos: id:Long [PK]; numero:int; nombre:String; estado:Estado; motivoCambio:String; creadaEn:LocalDateTime; vigenteDesde:LocalDateTime; vigenteHasta:LocalDateTime; version:long [version].
Relaciones: familia -> PlantillaDespeje [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne]; items -> ItemPlantillaDespeje [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### RespuestaDespeje
Clase: `exotic.app.planta.model.produccion.despeje.RespuestaDespeje`
Fuente: `src/main/java/exotic/app/planta/model/produccion/despeje/RespuestaDespeje.java`
Tabla declarada y corroborada en DDL: `despeje_respuesta`.
Atributos: id:Long [PK]; valor:ItemPlantillaDespeje.Valor; numero:BigDecimal; observacion:String.
Relaciones: ejecucion -> DespejeLineaEjecucion [ManyToOne]; item -> ItemPlantillaDespeje [ManyToOne]; plantilla -> PlantillaDespejeVersion [ManyToOne].

### MpsFabricacionDetalle
Clase: `exotic.app.planta.model.produccion.fabricacion.MpsFabricacionDetalle`
Fuente: `src/main/java/exotic/app/planta/model/produccion/fabricacion/MpsFabricacionDetalle.java`
Tabla declarada y corroborada en DDL: `mps_fabricacion_detalle`.
Atributos: id:Long [PK]; cantidad:BigDecimal; unidadMedida:String; fechaInicio:LocalDateTime; fechaFinal:LocalDateTime; observaciones:String; posicion:int; estado:EstadoMpsSemanalItem.
Relaciones: mps -> MpsFabricacionSemanal [ManyToOne]; semiTerminado -> SemiTerminado [ManyToOne]; ordenFabricacion -> OrdenFabricacion [OneToOne].

### MpsFabricacionObservacion
Clase: `exotic.app.planta.model.produccion.fabricacion.MpsFabricacionObservacion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/fabricacion/MpsFabricacionObservacion.java`
Tabla declarada y corroborada en DDL: `mps_fabricacion_observacion`.
Atributos: observacionId:Long [PK]; revisionMps:int; mensaje:String; tipo:TipoMpsSemanalObservacion; estado:EstadoMpsSemanalObservacion; respuestaCorreccion:String; fechaAtencion:LocalDateTime; fechaCierre:LocalDateTime; fechaCreacion:LocalDateTime.
Relaciones: mps -> MpsFabricacionSemanal [ManyToOne].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

### MpsFabricacionSemanal
Clase: `exotic.app.planta.model.produccion.fabricacion.MpsFabricacionSemanal`
Fuente: `src/main/java/exotic/app/planta/model/produccion/fabricacion/MpsFabricacionSemanal.java`
Tabla declarada y corroborada en DDL: `mps_fabricacion_semanal`.
Atributos: id:Long [PK]; weekStartDate:LocalDate; version:long [version]; creadoEn:LocalDateTime; actualizadoEn:LocalDateTime; estado:EstadoMpsSemanal; revisionNumero:int; fechaAprobacion:LocalDateTime; fechaEmision:LocalDateTime.
Relaciones: detalles -> MpsFabricacionDetalle [OneToMany].
Campos o relaciones omitidos por politica: 4; no inferir sus valores ni recorrerlos.

### OrdenFabricacion
Clase: `exotic.app.planta.model.produccion.fabricacion.OrdenFabricacion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/fabricacion/OrdenFabricacion.java`
Tabla declarada y corroborada en DDL: `orden_fabricacion`.
Atributos: ordenFabricacionId:Long [PK]; estado:EstadoOrdenFabricacion; cantidadPlanificada:BigDecimal; unidadMedida:String; fechaCreacion:LocalDateTime; fechaLanzamiento:LocalDateTime; liberadaEn:LocalDateTime; fechaFinalPlanificada:LocalDateTime; fechaInicio:LocalDateTime; fechaFinal:LocalDateTime; politicaDispensacionInicio:PoliticaDispensacionInicio; fechaAplicacionPoliticaDispensacion:LocalDateTime; estadoDispensacionMateriales:EstadoDispensacionMateriales; observaciones:String; version:long [version].
Relaciones: semiTerminado -> SemiTerminado [ManyToOne]; manufacturingVersion -> ManufacturingVersions [ManyToOne]; ordenProduccionOrigen -> OrdenProduccion [ManyToOne]; mpsFabricacionDetalle -> MpsFabricacionDetalle [OneToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### OrdenFabricacionOperacion
Clase: `exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/fabricacion/OrdenFabricacionOperacion.java`
Tabla declarada y corroborada en DDL: `orden_fabricacion_operacion`.
Atributos: id:Long [PK]; frontendNodeId:String; procesoProduccionId:Integer; procesoNombre:String; posicionSecuencia:int; estado:int; fechaEstadoActual:LocalDateTime; fechaVisible:LocalDateTime; fechaCompletado:LocalDateTime; observaciones:String; version:long [version].
Relaciones: ordenFabricacion -> OrdenFabricacion [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne]; poeDocumentoVersion -> ProcesoProduccionDocumentoVersion [ManyToOne]; batchRecordEtapa -> BatchRecordEtapa [OneToOne]; eventos -> OrdenFabricacionOperacionEvento [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### OrdenFabricacionOperacionDependencia
Clase: `exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionDependencia`
Fuente: `src/main/java/exotic/app/planta/model/produccion/fabricacion/OrdenFabricacionOperacionDependencia.java`
Tabla declarada y corroborada en DDL: `orden_fabricacion_operacion_dependencia`.
Atributos: id:Long [PK].
Relaciones: predecesora -> OrdenFabricacionOperacion [ManyToOne]; sucesora -> OrdenFabricacionOperacion [ManyToOne].

### OrdenFabricacionOperacionEvento
Clase: `exotic.app.planta.model.produccion.fabricacion.OrdenFabricacionOperacionEvento`
Fuente: `src/main/java/exotic/app/planta/model/produccion/fabricacion/OrdenFabricacionOperacionEvento.java`
Tabla declarada y corroborada en DDL: `orden_fabricacion_operacion_evento`.
Atributos: id:Long [PK]; estadoOrigen:Integer; estadoDestino:int; fechaEvento:LocalDateTime; actorTipo:ActorTipoEventoSeguimiento; tipoEvento:TipoEventoSeguimiento; nota:String.
Relaciones: operacion -> OrdenFabricacionOperacion [ManyToOne]; eventoRevertido -> OrdenFabricacionOperacionEvento [ManyToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### MasterProductionScheduleSemanal
Clase: `exotic.app.planta.model.produccion.MasterProductionScheduleSemanal`
Fuente: `src/main/java/exotic/app/planta/model/produccion/MasterProductionScheduleSemanal.java`
Tabla declarada y corroborada en DDL: `master_production_schedule_semanal`.
Atributos: mpsId:Integer [PK]; weekStartDate:LocalDate; weekEndDate:LocalDate; revisionNumero:Integer; fechaCreacion:LocalDateTime; fechaActualizacion:LocalDateTime; estado:EstadoMpsSemanal; fechaAprobacion:LocalDateTime; fechaGeneracionOdps:LocalDateTime.
Relaciones: semanaMps -> SemanaMPS [OneToOne]; ordenesProduccion -> OrdenProduccion [OneToMany]; observaciones -> MpsSemanalObservacion [OneToMany]; dias -> MpsSemanalDia [OneToMany].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### MpsSemanalDia
Clase: `exotic.app.planta.model.produccion.MpsSemanalDia`
Fuente: `src/main/java/exotic/app/planta/model/produccion/MpsSemanalDia.java`
Tabla declarada y corroborada en DDL: `mps_semanal_dia`.
Atributos: id:Long [PK]; fecha:LocalDate; dayIndex:int; displayOrder:int.
Relaciones: mpsSemanal -> MasterProductionScheduleSemanal [ManyToOne]; items -> MpsSemanalItem [OneToMany].

### MpsSemanalItem
Clase: `exotic.app.planta.model.produccion.MpsSemanalItem`
Fuente: `src/main/java/exotic/app/planta/model/produccion/MpsSemanalItem.java`
Tabla declarada y corroborada en DDL: `mps_semanal_item`.
Atributos: id:Long [PK]; terminadoNombre:String; categoriaId:Integer; categoriaNombre:String; loteSize:int; tiempoDiasFabricacion:int; numeroLotes:int; estado:EstadoMpsSemanalItem; cantidadTotal:double; fechaLanzamiento:LocalDate; fechaFinalPlanificada:LocalDate; observacion:String; warning:String; displayOrder:int.
Relaciones: mpsSemanal -> MasterProductionScheduleSemanal [ManyToOne]; mpsDia -> MpsSemanalDia [ManyToOne]; terminado -> Terminado [ManyToOne]; lotesPlanificados -> MpsSemanalLotePlanificado [OneToMany].

### MpsSemanalLotePlanificado
Clase: `exotic.app.planta.model.produccion.MpsSemanalLotePlanificado`
Fuente: `src/main/java/exotic/app/planta/model/produccion/MpsSemanalLotePlanificado.java`
Tabla declarada y corroborada en DDL: `mps_semanal_lote_planificado`.
Atributos: id:Long [PK]; loteOrdinal:int; cantidadPlanificada:double; estado:EstadoMpsSemanalLotePlanificado.
Relaciones: mpsItem -> MpsSemanalItem [ManyToOne]; ordenProduccion -> OrdenProduccion [OneToOne].

### MpsSemanalObservacion
Clase: `exotic.app.planta.model.produccion.MpsSemanalObservacion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/MpsSemanalObservacion.java`
Tabla declarada y corroborada en DDL: `mps_semanal_observacion`.
Atributos: observacionId:Long [PK]; revisionMps:Integer; mensaje:String; tipo:TipoMpsSemanalObservacion; estado:EstadoMpsSemanalObservacion; respuestaCorreccion:String; fechaAtencion:LocalDateTime; fechaCierre:LocalDateTime; fechaCreacion:LocalDateTime.
Relaciones: mpsSemanal -> MasterProductionScheduleSemanal [ManyToOne].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

### OrdenProduccion
Clase: `exotic.app.planta.model.produccion.OrdenProduccion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/OrdenProduccion.java`
Tabla declarada y corroborada en DDL: `ordenes_produccion`.
Atributos: ordenId:int [PK]; loteAsignado:String; estadoOrden:int; politicaDispensacionInicio:PoliticaDispensacionInicio; fechaAplicacionPoliticaDispensacion:LocalDateTime; estadoDispensacionMateriales:EstadoDispensacionMateriales; observaciones:String; cantidadProducir:double; fechaCreacion:LocalDateTime; fechaLanzamiento:LocalDateTime; fechaFinalPlanificada:LocalDateTime; fechaInicio:LocalDateTime; fechaFinal:LocalDateTime; canceladaEn:LocalDateTime; numeroPedidoComercial:String; areaOperativa:String; departamentoOperativo:String.
Relaciones: producto -> Producto [ManyToOne]; manufacturingVersion -> ManufacturingVersions [ManyToOne]; rutaProcesoCatVersion -> RutaProcesoCatVersion [ManyToOne]; jornadaLaboralVersion -> JornadaLaboralVersion [ManyToOne]; mpsSemanal -> MasterProductionScheduleSemanal [ManyToOne]; mpsLotePlanificado -> MpsSemanalLotePlanificado [OneToOne]; vendedorResponsable -> Vendedor [ManyToOne].
Campos o relaciones omitidos por politica: 4; no inferir sus valores ni recorrerlos.

### ReporteProduccionLote
Clase: `exotic.app.planta.model.produccion.ReporteProduccionLote`
Fuente: `src/main/java/exotic/app/planta/model/produccion/ReporteProduccionLote.java`
Tabla declarada y corroborada en DDL: `reporte_produccion_lote`.
Atributos: id:Long [PK]; cantidadReportada:BigDecimal; cantidadConfirmada:BigDecimal; fechaProduccion:LocalDate; reportadoEn:LocalDateTime; estado:Estado; motivoCorreccion:String; estadoOrdenAnterior:int; anuladoEn:LocalDateTime; motivoAnulacion:String; version:long [version].
Relaciones: ordenProduccion -> OrdenProduccion [ManyToOne]; lote -> Lote [ManyToOne]; seguimientoOrdenArea -> SeguimientoOrdenArea [ManyToOne]; cierreProduccion -> CierreProduccion [ManyToOne]; transaccionAlmacen -> TransaccionAlmacen [OneToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### RutaProcesoCat
Clase: `exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCat`
Fuente: `src/main/java/exotic/app/planta/model/produccion/ruprocatdesigner/RutaProcesoCat.java`
Tabla declarada y corroborada en DDL: `ruta_proceso_cat`.
Atributos: id:Long [PK]; fechaCreacion:LocalDateTime; fechaModificacion:LocalDateTime.
Relaciones: categoria -> Categoria [OneToOne]; versions -> RutaProcesoCatVersion [OneToMany].

### RutaProcesoCatVersion
Clase: `exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoCatVersion`
Fuente: `src/main/java/exotic/app/planta/model/produccion/ruprocatdesigner/RutaProcesoCatVersion.java`
Tabla declarada y corroborada en DDL: `ruta_proceso_cat_version`.
Atributos: id:Long [PK]; versionNumber:Integer; estado:Estado; vigenteDesde:LocalDateTime; vigenteHasta:LocalDateTime; creadoEn:LocalDateTime; motivoCambio:String; layoutRevision:int; layoutActualizadoEn:LocalDateTime.
Relaciones: rutaProcesoCat -> RutaProcesoCat [ManyToOne]; nodes -> RutaProcesoNode [OneToMany]; edges -> RutaProcesoEdge [OneToMany].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### RutaProcesoEdge
Clase: `exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoEdge`
Fuente: `src/main/java/exotic/app/planta/model/produccion/ruprocatdesigner/RutaProcesoEdge.java`
Tabla declarada y corroborada en DDL: `ruta_proceso_edge`.
Atributos: id:Long [PK]; frontendId:String.
Relaciones: rutaProcesoCatVersion -> RutaProcesoCatVersion [ManyToOne]; sourceNode -> RutaProcesoNode [ManyToOne]; targetNode -> RutaProcesoNode [ManyToOne].

### RutaProcesoNode
Clase: `exotic.app.planta.model.produccion.ruprocatdesigner.RutaProcesoNode`
Fuente: `src/main/java/exotic/app/planta/model/produccion/ruprocatdesigner/RutaProcesoNode.java`
Tabla declarada y corroborada en DDL: `ruta_proceso_node`.
Atributos: id:Long [PK]; frontendId:String; posicionX:double; posicionY:double; label:String; hasLeftHandle:boolean; hasRightHandle:boolean; duracionEstimadaMinutos:int; requiereJornadaLaboral:boolean.
Relaciones: rutaProcesoCatVersion -> RutaProcesoCatVersion [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne]; procesoProduccion -> ProcesoProduccion [ManyToOne]; semiterminadosConsumidos -> SemiTerminado [ManyToMany].

### SeguimientoOrdenArea
Clase: `exotic.app.planta.model.produccion.SeguimientoOrdenArea`
Fuente: `src/main/java/exotic/app/planta/model/produccion/SeguimientoOrdenArea.java`
Tabla declarada y corroborada en DDL: `seguimiento_orden_area`.
Atributos: id:Long [PK]; aplicaEjecucionOp:Boolean; estado:int; fechaEstadoActual:LocalDateTime; posicionSecuencia:Integer; fechaCreacion:LocalDateTime; fechaVisible:LocalDateTime; fechaCompletado:LocalDateTime; duracionEstimadaMinutos:int; requiereJornadaLaboral:boolean; observaciones:String.
Relaciones: ordenProduccion -> OrdenProduccion [ManyToOne]; rutaProcesoNode -> RutaProcesoNode [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne]; poeDocumentoVersion -> ProcesoProduccionDocumentoVersion [ManyToOne]; eventos -> SeguimientoOrdenAreaEvento [OneToMany].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### SeguimientoOrdenAreaEvento
Clase: `exotic.app.planta.model.produccion.SeguimientoOrdenAreaEvento`
Fuente: `src/main/java/exotic/app/planta/model/produccion/SeguimientoOrdenAreaEvento.java`
Tabla declarada y corroborada en DDL: `seguimiento_orden_area_evento`.
Atributos: id:Long [PK]; estadoOrigen:Integer; estadoDestino:int; fechaEvento:LocalDateTime; actorTipo:ActorTipoEventoSeguimiento; tipoEvento:TipoEventoSeguimiento; nota:String.
Relaciones: seguimientoOrdenArea -> SeguimientoOrdenArea [ManyToOne]; eventoRevertido -> SeguimientoOrdenAreaEvento [ManyToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### SemanaMPS
Clase: `exotic.app.planta.model.produccion.SemanaMPS`
Fuente: `src/main/java/exotic/app/planta/model/produccion/SemanaMPS.java`
Tabla declarada y corroborada en DDL: `semana_mps`.
Atributos: id:Long [PK]; codigo:String; anioSemana:int; numeroSemana:int; startDate:LocalDate; endDate:LocalDate; standard:String.
Relaciones: mpsSemanal -> MasterProductionScheduleSemanal [OneToOne].

## Dominio: producto

### Categoria
Clase: `exotic.app.planta.model.producto.Categoria`
Fuente: `src/main/java/exotic/app/planta/model/producto/Categoria.java`
Tabla declarada y corroborada en DDL: `categoria`.
Atributos: categoriaId:int [PK]; categoriaNombre:String; categoriaDescripcion:String; loteSize:Integer; tiempoDiasFabricacion:Integer; capacidadProductivaDiaria:Integer; vidaUtilCantidad:Integer; vidaUtilUnidad:UnidadTiempoVencimiento.
Relaciones: sin relaciones propias publicadas.

### CargaCostosItem
Clase: `exotic.app.planta.model.producto.costos.CargaCostosItem`
Fuente: `src/main/java/exotic/app/planta/model/producto/costos/CargaCostosItem.java`
Tabla declarada y corroborada en DDL: `carga_costos_item`.
Atributos: id:Long [PK]; filaExcel:int; productoId:String; productoNombre:String; tipoProducto:String; descripcionExcel:String; descripcionCoincide:boolean; costoAnterior:BigDecimal; costoNuevo:BigDecimal; costoVersionAnterior:long.
Relaciones: lote -> CargaCostosLote [ManyToOne].

### CargaCostosLote
Clase: `exotic.app.planta.model.producto.costos.CargaCostosLote`
Fuente: `src/main/java/exotic/app/planta/model/producto/costos/CargaCostosLote.java`
Tabla declarada y corroborada en DDL: `carga_costos_lote`.
Atributos: id:UUID [PK]; motivo:String; estado:Estado; creadoEn:LocalDateTime; expiraEn:LocalDateTime; ejecutadoEn:LocalDateTime; totalFilas:int; totalCandidatas:int; totalActualizadas:int; totalSinCambio:int; totalOmitidas:int; recetaRevision:Long; algoritmoVersion:String; invalidadoEn:LocalDateTime; invalidacionCodigo:String; totalDependencias:int; totalSemiterminados:int; totalTerminados:int; totalDependenciasActualizadas:int; totalDependenciasSinCambio:int.
Relaciones: items -> CargaCostosItem [OneToMany]; propagacionItems -> CargaCostosPropagacionItem [OneToMany].
Campos o relaciones omitidos por politica: 10; no inferir sus valores ni recorrerlos.

### CargaCostosPropagacionItem
Clase: `exotic.app.planta.model.producto.costos.CargaCostosPropagacionItem`
Fuente: `src/main/java/exotic/app/planta/model/producto/costos/CargaCostosPropagacionItem.java`
Tabla declarada y corroborada en DDL: `carga_costos_propagacion_item`.
Atributos: id:Long [PK]; productoId:String; productoNombre:String; tipoProducto:String; nivel:int; costoAnterior:BigDecimal; costoNuevo:BigDecimal; costoVersionAnterior:long.
Relaciones: lote -> CargaCostosLote [ManyToOne].

### ProductoCostoHistorial
Clase: `exotic.app.planta.model.producto.costos.ProductoCostoHistorial`
Fuente: `src/main/java/exotic/app/planta/model/producto/costos/ProductoCostoHistorial.java`
Tabla declarada y corroborada en DDL: `producto_costo_historial`.
Atributos: id:Long [PK]; productoId:String; productoNombre:String; tipoProducto:String; version:long; costoAnterior:BigDecimal; costoNuevo:BigDecimal; cambiadoEn:LocalDateTime; origen:ProductoCostoOrigen; motivo:String; referencia:String.
Relaciones: cargaCostosLote -> CargaCostosLote [ManyToOne].
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### RecetaCostosRevision
Clase: `exotic.app.planta.model.producto.costos.RecetaCostosRevision`
Fuente: `src/main/java/exotic/app/planta/model/producto/costos/RecetaCostosRevision.java`
Tabla declarada y corroborada en DDL: `receta_costos_revision`.
Atributos: id:Short [PK]; version:long; actualizadoEn:LocalDateTime.
Relaciones: sin relaciones propias publicadas.

### MaterialFichaTecnicaVersion
Clase: `exotic.app.planta.model.producto.fichatecnica.MaterialFichaTecnicaVersion`
Fuente: `src/main/java/exotic/app/planta/model/producto/fichatecnica/MaterialFichaTecnicaVersion.java`
Tabla declarada y corroborada en DDL: `material_ficha_tecnica_version`.
Atributos: id:Long [PK]; version:Integer; estado:Estado; contentType:String; tamanoBytes:Long; vigenteDesde:LocalDateTime; vigenteHasta:LocalDateTime; creadoEn:LocalDateTime; motivoCambio:String.
Relaciones: material -> Material [ManyToOne].
Campos o relaciones omitidos por politica: 4; no inferir sus valores ni recorrerlos.

### CasePack
Clase: `exotic.app.planta.model.producto.manufacturing.packaging.CasePack`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/packaging/CasePack.java`
Tabla fisica no resuelta; referencia logica de entidad.
Atributos: id:Long [PK]; unitsPerCase:Integer; ean14:String; largoCm:Double; anchoCm:Double; altoCm:Double; grossWeightKg:Double; defaultForShipping:Boolean.
Relaciones: insumosEmpaque -> InsumoEmpaque [OneToMany].

### InsumoEmpaque
Clase: `exotic.app.planta.model.producto.manufacturing.packaging.InsumoEmpaque`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/packaging/InsumoEmpaque.java`
Tabla declarada y corroborada en DDL: `insumos_empaque`.
Atributos: id:Long [PK]; nivel:NivelEmpaque; cantidad:double; uom:String.
Relaciones: material -> Material [ManyToOne].

### NodoInsumo
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.nodo.NodoInsumo`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/nodo/NodoInsumo.java`
Hereda de `ProcesoFabricacionNodo` y comparte su tabla `proceso_fabricacion_nodo` segun herencia SINGLE_TABLE.
Atributos: sin atributos escalares propios publicados.
Relaciones: insumo -> Insumo [ManyToOne].

### NodoProceso
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.nodo.NodoProceso`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/nodo/NodoProceso.java`
Hereda de `ProcesoFabricacionNodo` y comparte su tabla `proceso_fabricacion_nodo` segun herencia SINGLE_TABLE.
Atributos: sin atributos escalares propios publicados.
Relaciones: procesoProduccion -> ProcesoProduccion [ManyToOne]; areaOperativa -> AreaOperativa [ManyToOne].

### NodoTarget
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.nodo.NodoTarget`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/nodo/NodoTarget.java`
Hereda de `ProcesoFabricacionNodo` y comparte su tabla `proceso_fabricacion_nodo` segun herencia SINGLE_TABLE.
Atributos: sin atributos escalares propios publicados.
Relaciones: sin relaciones propias publicadas.

### ProcesoFabricacionEdge
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.nodo.ProcesoFabricacionEdge`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/nodo/ProcesoFabricacionEdge.java`
Tabla declarada y corroborada en DDL: `proceso_fabricacion_edge`.
Atributos: id:Long [PK]; frontendId:String.
Relaciones: procesoProduccionCompleto -> ProcesoProduccionCompleto [ManyToOne]; sourceNode -> ProcesoFabricacionNodo [ManyToOne]; targetNode -> ProcesoFabricacionNodo [ManyToOne].

### ProcesoFabricacionNodo
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.nodo.ProcesoFabricacionNodo`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/nodo/ProcesoFabricacionNodo.java`
Tabla declarada y corroborada en DDL: `proceso_fabricacion_nodo`.
Raiz de herencia SINGLE_TABLE; consultar tambien sus subtipos documentados.
Atributos: id:Long [PK]; frontendId:String; posicionX:double; posicionY:double; label:String.
Relaciones: procesoProduccionCompleto -> ProcesoProduccionCompleto [ManyToOne].

### ProcesoProduccion
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccion`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/ProcesoProduccion.java`
Tabla declarada y corroborada en DDL: `proceso_produccion`.
Atributos: procesoId:int [PK]; nombre:String; nivelAcceso:int; model:TimeModelType; setUpTime:Double; constantSeconds:Double; throughputUnitsPerSec:Double; secondsPerUnit:Double; secondsPerBatch:Double; batchSize:Double.
Relaciones: recursosRequeridos -> ProcesoRecurso [OneToMany].

### ProcesoProduccionCompleto
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionCompleto`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/ProcesoProduccionCompleto.java`
Tabla declarada y corroborada en DDL: `procesos_produccion`.
Atributos: procesoCompletoId:int [PK]; rendimientoTeorico:double; cantidadSalidaReceta:java.math.BigDecimal.
Relaciones: producto -> Producto [OneToOne]; nodes -> ProcesoFabricacionNodo [OneToMany]; edges -> ProcesoFabricacionEdge [OneToMany].

### ProcesoProduccionDocumentoVersion
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.ProcesoProduccionDocumentoVersion`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/ProcesoProduccionDocumentoVersion.java`
Tabla declarada y corroborada en DDL: `proceso_produccion_documento_version`.
Atributos: id:Long [PK]; version:Integer; estado:Estado; contentType:String; tamanoBytes:Long; vigenteDesde:LocalDateTime; vigenteHasta:LocalDateTime; creadoEn:LocalDateTime; motivoCambio:String.
Relaciones: proceso -> ProcesoProduccion [ManyToOne].
Campos o relaciones omitidos por politica: 5; no inferir sus valores ni recorrerlos.

### ProcesoRecurso
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.ProcesoRecurso`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/ProcesoRecurso.java`
Tabla declarada y corroborada en DDL: `proceso_recurso`.
Atributos: id:Long [PK]; cantidad:Integer; notas:String.
Relaciones: proceso -> ProcesoProduccion [ManyToOne]; recurso -> RecursoProduccion [ManyToOne].

### RecursoProduccion
Clase: `exotic.app.planta.model.producto.manufacturing.procesos.RecursoProduccion`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/procesos/RecursoProduccion.java`
Tabla fisica no resuelta; referencia logica de entidad.
Atributos: id:Long [PK]; nombre:String; descripcion:String; capacidadTotal:Double; cantidadDisponible:Integer; capacidadPorHora:Double; turnos:Integer; horasPorTurno:Double.
Relaciones: activosFijos -> ActivoFijo [OneToMany]; procesos -> ProcesoRecurso [OneToMany].

### Insumo
Clase: `exotic.app.planta.model.producto.manufacturing.receta.Insumo`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/receta/Insumo.java`
Tabla declarada y corroborada en DDL: `insumos`.
Atributos: insumoId:int [PK]; cantidadRequerida:double; cantidadReceta:java.math.BigDecimal; unidadReceta:String.
Relaciones: producto -> Producto [ManyToOne].

### ManufacturingVersions
Clase: `exotic.app.planta.model.producto.manufacturing.snapshots.ManufacturingVersions`
Fuente: `src/main/java/exotic/app/planta/model/producto/manufacturing/snapshots/ManufacturingVersions.java`
Tabla declarada y corroborada en DDL: `manufacturing_versions`.
Atributos: id:Long [PK]; versionNumber:int.
Relaciones: producto -> Producto [ManyToOne].
Campos o relaciones omitidos por politica: 3; no inferir sus valores ni recorrerlos.

### Material
Clase: `exotic.app.planta.model.producto.Material`
Fuente: `src/main/java/exotic/app/planta/model/producto/Material.java`
Hereda de `Producto` y comparte su tabla `productos` segun herencia SINGLE_TABLE.
Atributos: consumoDirecto:boolean; tipoMaterial:int; puntoReorden:double.
Relaciones: sin relaciones propias publicadas.

### Producto
Clase: `exotic.app.planta.model.producto.Producto`
Fuente: `src/main/java/exotic/app/planta/model/producto/Producto.java`
Tabla declarada y corroborada en DDL: `productos`.
Raiz de herencia SINGLE_TABLE; consultar tambien sus subtipos documentados.
Atributos: productoId:String [PK]; nombre:String; observaciones:String; costo:BigDecimal; costoVersion:long; ivaPercentual:double; fechaCreacion:LocalDateTime; tipoUnidades:String; cantidadUnidad:double; unidadContenidoPorEnvase:String; stockMinimo:double; inventareable:boolean; prefijoLote:String.
Relaciones: sin relaciones propias publicadas.

### SemiTerminado
Clase: `exotic.app.planta.model.producto.SemiTerminado`
Fuente: `src/main/java/exotic/app/planta/model/producto/SemiTerminado.java`
Hereda de `Producto` y comparte su tabla `productos` segun herencia SINGLE_TABLE.
Atributos: requiereOrdenFabricacion:boolean.
Relaciones: insumos -> Insumo [OneToMany]; procesoProduccionCompleto -> ProcesoProduccionCompleto [OneToOne].

### Terminado
Clase: `exotic.app.planta.model.producto.Terminado`
Fuente: `src/main/java/exotic/app/planta/model/producto/Terminado.java`
Hereda de `Producto` y comparte su tabla `productos` segun herencia SINGLE_TABLE.
Atributos: status:int.
Relaciones: insumos -> Insumo [OneToMany]; procesoProduccionCompleto -> ProcesoProduccionCompleto [OneToOne]; categoria -> Categoria [ManyToOne]; casePack -> CasePack [OneToOne].
Campos o relaciones omitidos por politica: 1; no inferir sus valores ni recorrerlos.

### UnidadMedidaProducto
Clase: `exotic.app.planta.model.producto.UnidadMedidaProducto`
Fuente: `src/main/java/exotic/app/planta/model/producto/UnidadMedidaProducto.java`
Tabla declarada y corroborada en DDL: `unidad_medida_producto`.
Atributos: codigo:String [PK]; simbolo:String; magnitud:Magnitud; factorAReferencia:BigDecimal.
Relaciones: sin relaciones propias publicadas.

## Dominio: ventas

### Cliente
Clase: `exotic.app.planta.model.ventas.Cliente`
Fuente: `src/main/java/exotic/app/planta/model/ventas/Cliente.java`
Tabla declarada y corroborada en DDL: `clientes`.
Atributos: clienteId:int [PK]; nombre:String; email:String; telefono:String; direccion:String; condicionesPago:String; limiteCredito:Integer; fechaRegistro:LocalDateTime.
Relaciones: sin relaciones propias publicadas.
Campos o relaciones omitidos por politica: 2; no inferir sus valores ni recorrerlos.

### FacturaVenta
Clase: `exotic.app.planta.model.ventas.FacturaVenta`
Fuente: `src/main/java/exotic/app/planta/model/ventas/FacturaVenta.java`
Tabla declarada y corroborada en DDL: `facturas_venta`.
Atributos: facturaVentaId:int [PK]; fechaFactura:LocalDateTime; subTotal:int; impuestos:int; totalPagar:int; estadoPago:String.
Relaciones: ordenVenta -> OrdenVenta [ManyToOne]; cliente -> Cliente [ManyToOne]; itemsFacturaVenta -> ItemFacturaVenta [OneToMany].

### ItemFacturaVenta
Clase: `exotic.app.planta.model.ventas.ItemFacturaVenta`
Fuente: `src/main/java/exotic/app/planta/model/ventas/ItemFacturaVenta.java`
Tabla declarada y corroborada en DDL: `items_factura_venta`.
Atributos: itemFacturaId:int [PK]; cantidad:double; precioUnitario:int.
Relaciones: facturaVenta -> FacturaVenta [ManyToOne]; producto -> Producto [ManyToOne].

### ItemOrdenVenta
Clase: `exotic.app.planta.model.ventas.ItemOrdenVenta`
Fuente: `src/main/java/exotic/app/planta/model/ventas/ItemOrdenVenta.java`
Tabla declarada y corroborada en DDL: `items_orden_venta`.
Atributos: itemId:int [PK]; cantidad:double; precioUnitario:int; impuestos:int.
Relaciones: ordenVenta -> OrdenVenta [ManyToOne]; producto -> Producto [ManyToOne].

### OrdenVenta
Clase: `exotic.app.planta.model.ventas.OrdenVenta`
Fuente: `src/main/java/exotic/app/planta/model/ventas/OrdenVenta.java`
Tabla declarada y corroborada en DDL: `ordenes_venta`.
Atributos: ordenVentaId:int [PK]; fechaCreacion:LocalDateTime; estado:String; condicionesPago:String; fechaEntregaPrevista:LocalDate.
Relaciones: cliente -> Cliente [ManyToOne]; itemsOrdenVenta -> ItemOrdenVenta [OneToMany].

### Vendedor
Clase: `exotic.app.planta.model.ventas.Vendedor`
Fuente: `src/main/java/exotic/app/planta/model/ventas/Vendedor.java`
Tabla declarada y corroborada en DDL: `vendedores`.
Atributos: sin atributos escalares propios publicados.
Relaciones: sin relaciones propias publicadas.
Campos o relaciones omitidos por politica: 8; no inferir sus valores ni recorrerlos.

## Tablas de enlace de negocio verificadas

Estas tablas no son entidades adicionales. Sus nombres estan declarados en @JoinTable y corroborados en DDL; solo se describen enlaces entre entidades incluidas. Dos enlaces a identidades de usuario se excluyen de este inventario de negocio.

- `control_plan_aplicabilidad_exclusion`: `AplicabilidadPlanControl.productosExcluidos` [ManyToMany]. Fuente: `src/main/java/exotic/app/planta/model/controles/AplicabilidadPlanControl.java` y `src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql`.
- `area_operativa_categoria_habilitada`: `AreaOperativa.categoriasHabilitadas` [ManyToMany]. Fuente: `src/main/java/exotic/app/planta/model/organizacion/AreaOperativa.java` y `src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql`.
- `ruta_proceso_node_semiterminado`: `RutaProcesoNode.semiterminadosConsumidos` [ManyToMany]. Fuente: `src/main/java/exotic/app/planta/model/produccion/ruprocatdesigner/RutaProcesoNode.java` y `src/main/resources/db/migration/V121__consumo_semiterminado_por_area.sql`.

## Tablas adicionales de negocio sin entidad JPA actual

Fuentes DDL, sin promesa de herramientas ni de uso funcional vigente. Las reservas y planificaciones siguientes estan presentes en el DDL consolidado, pero no tienen clase @Entity actual; no deben confundirse automaticamente con el flujo MPS/OP/OF vigente.

### Tabla sin entidad: batch_record_documento
Fuente DDL: `src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql`.
Columnas fisicas publicadas: id:bigint; batch_record_id:bigint; tipo:varchar; version_documento:integer; content_type:varchar; tamano_bytes:bigint; generado_en:timestamp.
Referencias DDL publicadas: batch_record_id -> batch_record.id.

### Tabla sin entidad: movimiento_reserva
Fuente DDL: `src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql`.
Columnas fisicas publicadas: id:bigint; cantidad:integer; fecha_creacion:timestamp; tipo:smallint; producto_id:varchar.
Referencias DDL publicadas: producto_id -> productos.producto_id.

### Tabla sin entidad: planificacion_produccion
Fuente DDL: `src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql`.
Columnas fisicas publicadas: id:bigint; cantidad_producir:double precision; estado:integer; fecha_planificada:timestamp; prioridad:integer; producto_id:varchar.
Referencias DDL publicadas: producto_id -> productos.producto_id.

### Tabla sin entidad: planificacion_recurso
Fuente DDL: `src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql`.
Columnas fisicas publicadas: planificacion_id:bigint; recurso_id:bigint.
Referencias DDL publicadas: planificacion_id -> planificacion_produccion.id; recurso_id -> recurso_produccion.id.

### Tabla sin entidad: proveedor_material_lead_time_metric
Fuente DDL: `src/main/resources/db/migration-v1/V001__esquema_actual_consolidado.sql`.
Columnas fisicas publicadas: id:bigint; proveedor_pk:bigint; producto_id:varchar; fecha_corte:date; ventana_dias:integer; lead_time_mediano_dias:double precision; observaciones:integer; ordenes_consideradas:integer; calculado_en:timestamp.
Referencias DDL publicadas: proveedor_pk -> proveedores.pk; producto_id -> productos.producto_id.

## Entidades excluidas

La exclusion afecta a la clase completa y a las relaciones que la tendrian como destino. No se publican sus atributos.

- Clase excluida: `exotic.app.planta.model.controles.RegistroIdempotenciaControl`; motivo: registro tecnico con respuestas y control de repeticion.
- Clase excluida: `exotic.app.planta.model.master.configs.SuperMasterConfig`; motivo: configuracion administrativa y controles de seguridad.
- Clase excluida: `exotic.app.planta.model.master.configs.SuperMasterVerificationCode`; motivo: verificacion administrativa.
- Clase excluida: `exotic.app.planta.model.monitoreoerrores.EventoError`; motivo: observabilidad y payloads tecnicos.
- Clase excluida: `exotic.app.planta.model.monitoreoerrores.GrupoError`; motivo: observabilidad tecnica.
- Clase excluida: `exotic.app.planta.model.produccion.batchrecord.BatchRecordProjectionTask`; motivo: cola tecnica de proyeccion.
- Clase excluida: `exotic.app.planta.model.users.auth.PasswordResetToken`; motivo: identidad, autenticacion, autorizacion o firmas de usuario.
- Clase excluida: `exotic.app.planta.model.users.firma.FirmaVisualUsuarioVersion`; motivo: identidad, autenticacion, autorizacion o firmas de usuario.
- Clase excluida: `exotic.app.planta.model.users.ModuloAcceso`; motivo: identidad, autenticacion, autorizacion o firmas de usuario.
- Clase excluida: `exotic.app.planta.model.users.TabAcceso`; motivo: identidad, autenticacion, autorizacion o firmas de usuario.
- Clase excluida: `exotic.app.planta.model.users.User`; motivo: identidad, autenticacion, autorizacion o firmas de usuario.

## Exclusiones de otros metadatos

- El ledger tecnico del asistente, historial Flyway, credenciales, configuracion privada y tablas puente hacia identidades no pertenecen al conocimiento empresarial publicado.
- Se documenta estructura, no valores personales, bancarios, salariales, fiscales ni comerciales. Que exista un atributo empresarial no autoriza acceder a su contenido.
- No se publican columnas privadas omitidas ni una lista de nombres alternativos para intentar acceder a ellas.
- Los modelos de empresa, permisos de personal, costos, calidad y expedientes conservan solamente sus atributos empresariales permitidos; las omisiones no deben rellenarse mediante suposiciones.

## Limites de interpretacion

- Producto es una raiz de herencia en productos. Terminado, Material y SemiTerminado son subtipos, no tres tablas independientes.
- Una OP representa una orden de produccion; su producto vigente debe ser Terminado segun ProduccionService. Lote identifica un batch y puede tener origen en compra, OP u OF. No igualar OP, lote, cantidad planificada y existencias.
- Para historicos, Lote.producto puede faltar; el origen y sus movimientos pueden ser necesarios para identificar el producto. No inventar asociaciones ausentes.
- Stock se calcula desde movimientos que afectan inventario, con producto, almacen, lote y unidad pertinentes. stockMinimo y puntoReorden son parametros, no existencias actuales.
- No sumar cantidades con unidades incompatibles ni duplicar movimientos al unir relaciones uno a muchos.
- Los nombres de estados/codigos y las reglas de operaciones deben obtenerse de servicios y contratos vigentes. Este inventario no convierte comentarios o tipos en reglas ejecutables.
- Campos y tablas aqui descritos no habilitan lecturas generales, joins arbitrarios ni escrituras. Cuando no existe herramienta autorizada para una pregunta, explicar la limitacion sin inventar resultados.
