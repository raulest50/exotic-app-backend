# Credenciales locales: JWT, cliente HTTP y Compose

La configuración pública descrita aquí no contiene contraseñas ni tokens. Los
valores privados se suministran desde la configuración local de cada herramienta.

## Backend JWT

El backend genera automáticamente una clave HMAC de 512 bits mediante JJWT al
crear su proveedor JWT. La misma clave firma y verifica todos los tokens de ese
contexto de Spring. Se conserva solo en memoria: no se guarda ni se imprime y no
forma parte del build, del JAR o de la imagen Docker.

No se requiere `jwt.secret` ni `JWT_SECRET`; los valores anteriores de esas
propiedades ya no se utilizan para firmar o verificar las sesiones de Exotic.
Los archivos privados `application*.properties` no se modifican con este cambio.

Cada arranque de Spring obtiene una clave nueva, tanto en local como en producción
y staging. También ocurre ante reinicios automáticos o recargas de DevTools.
Los JWT del contexto anterior dejan de ser válidos y los usuarios deben iniciar
sesión nuevamente. La expiración configurada mediante `jwt.expiration` se conserva.
Un despliegue exclusivo del frontend no renueva la clave del backend.

Este mecanismo requiere una sola instancia del backend atendiendo peticiones por
entorno. Varias réplicas simultáneas necesitarían compartir una clave mediante
otro mecanismo. La clave JWT es independiente de la autorización privada del
proveedor de IA: reiniciar Spring no obliga por sí solo a autorizar esa cuenta.

## Cliente HTTP de IntelliJ

El archivo público `src/test/resources/http/http-client.env.json` conserva solo el
host local y el tipo de contenido. Para usar las peticiones con el entorno `dev`,
crear localmente `src/test/resources/http/http-client.private.env.json` con:

```json
{
  "dev": {
    "user": "",
    "password": ""
  }
}
```

Completar esos campos solo en el archivo privado, que está ignorado por Git. No
guardar el JWT allí. Ejecutar el login de `users/auth.http`: elimina el token global
anterior antes de enviar la petición y conserva el nuevo solo si recibe HTTP 200
y un token no vacío. Las demás peticiones usan `{{auth_token}}`. Los mensajes del
script no imprimen el token ni un prefijo.

La exclusión de Git no constituye una exclusión del contexto Docker o de otros
artefactos. Los archivos privados también deben mantenerse fuera de cualquier
artefacto que se publique; el hallazgo de empaquetado anterior requiere su ajuste
independiente.

El archivo privado no se crea ni se rellena automáticamente y no se copian las
credenciales previamente versionadas. El manejo de entornos privados está descrito
en la [documentación de JetBrains](https://www.jetbrains.com/help/idea/http-client-variables.html).

## PostgreSQL de desarrollo con Compose

`docker-compose-only-dev.yml` requiere `EXOTIC_DEV_POSTGRES_PASSWORD` y no incluye
contraseña predeterminada. Definirla en el entorno del proceso o en un archivo
`.env` local en la raíz del backend, ignorado por Git, antes de ejecutar Compose.
Si falta o está vacía, Compose rechaza la configuración.

Para una base ya creada, suministrar su contraseña actual: cambiar esta variable
no cambia la contraseña almacenada en PostgreSQL. Este ajuste no recrea la base,
no arranca contenedores ni modifica datos o migraciones.

## Sandbox del frontend

El archivo `exotic-app-frontend/sandbox.tsx` es un script local independiente de la
app. Requiere `EXOTIC_SANDBOX_TOKEN` en el entorno del proceso al ejecutarlo con Bun.
No utiliza una variable `VITE_*`, para que la credencial no se incorpore al frontend.
Si falta el token, termina antes de hacer peticiones. Sus errores no imprimen la
configuración Axios que contiene la cabecera de autorización.

## Alcance de esta limpieza

Se eliminan los valores literales de los archivos actuales. Esto no elimina sus
versiones del historial ni acredita que las contraseñas antiguas estén revocadas.
Si se utilizaron como credenciales reales, su rotación debe coordinarse en el
servicio correspondiente; no se realiza automáticamente con este cambio.
