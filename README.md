# Exotic Manufacture BackEnd

Backend para aplicacion que presta funcionalidades de MRP + otras funciones.
permite llevar control de inventarios, compras y produccion en una planta
que manufactura diferentes referencias de productos capilares y cosmeticos 
en general.


## Copiar archivos a remote

[documentacion de render.com](https://render.com/docs/ssh)

primero se debe generar una public key de manera local, por ejemplo como:
```
ssh-keygen -t rsa -b 1024 -C "correo" -f "file name"
```
se crean 2 archivos id_rsa, se ahi se debe copiar la key y se agrega a las public keys
en la cuenta de render.com. la public key no se agrega en los settings del proyecto
sino en los settings de la cuenta.

para connectarse a remote:
```
ssh -i "filename" "host"
```

copiar datos a remote
```
scp -i "filename" -r "source folder" "destination host":"destination folder"
```


## Scan Entidades

el script scanEntities.jsh es un script para jshell que
realiza un resumen en txt en entity-summary.txt de las
entidades del backend en el package model.
```
jshell --startup PRINTING scanEntities.jsh
```

## Carga masiva de productos

El endpoint `POST /api/bulk-upload/products` permite cargar un archivo Excel con
productos y opcionalmente un objeto `mapping` que define la posición de las
columnas. Cuando no se envía, se utilizan los índices predeterminados
`(1,3,6,7,8,9,10)`.

Ejemplo de llamada con mapeo personalizado:

```bash
curl -X POST \
  -F "file=@inventario.xlsx" \
  -F 'mapping={"descripcion":0,"unidadMedida":2,"stock":1,"productoId":3,"iva":4,"puntoReorden":5,"costoUnitario":6};type=application/json' \
  http://localhost:8080/api/bulk-upload/products
```

Campos del mapeo:

- `descripcion`: índice de la descripción del producto.
- `unidadMedida`: índice de la unidad de medida (soportadas: `KG`, `L`, `U`, `G`).
- `stock`: índice del stock inicial.
- `productoId`: índice del identificador del producto.
- `iva`: índice del IVA del producto.
- `puntoReorden`: índice del punto de reorden.
- `costoUnitario`: índice del costo unitario.

Ejemplo de fila usando gramos:

| descripcion        | unidadMedida | stock | productoId | iva | puntoReorden | costoUnitario |
|--------------------|--------------|-------|------------|-----|--------------|---------------|
| Polvo de Colágeno  | G            | 500   | MP004      | 19  | 50           | 35000         |

## Ajustes de inventario

El endpoint `POST /movimientos/ajustes` permite registrar ajustes positivos o negativos, con soporte opcional de lotes y documento de soporte.

Ejemplo de carga útil:

```json
{
  "username": "jdoe",
  "observaciones": "Ajuste mensual de stock",
  "urlDocSoporte": "https://example.com/ajuste-septiembre.pdf",
  "items": [
    {
      "productoId": "MP001",
      "cantidad": 15.5,
      "almacen": "GENERAL"
    },
    {
      "productoId": "PT-123",
      "cantidad": -3,
      "almacen": "GENERAL",
      "loteId": 45
    }
  ]
}
```

- Para este endpoint (`OAA`), las cantidades positivas se registran como `AJUSTE_POSITIVO`.
- Las cantidades negativas disminuyen el inventario y se clasifican como `BAJA` si no se envía otro motivo válido.
- Nota: la clasificación como `BAJA` ya no aplica a ajustes `OAA`.
- Para este endpoint (`OAA`), las cantidades negativas se registran como `AJUSTE_NEGATIVO`.
- En `POST /movimientos/ajustes`, el tipo del movimiento se deriva del signo de `cantidad`; no se usa `motivo` para reclasificarlo como compra o baja.
- `loteId` es opcional y permite asociar el ajuste a un lote específico.
