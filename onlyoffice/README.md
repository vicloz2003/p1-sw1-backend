# Edición colaborativa de documentos Office (RF-1.10)

Integración con **OnlyOffice Document Server** para editar Word/Excel/PowerPoint de forma
colaborativa directamente en el navegador, persistiendo cada guardado como una nueva versión en S3.

## Arquitectura

```
Angular (DocsAPI.DocEditor)
   │  1. GET /api/v1/documents/{id}/onlyoffice/config   (config firmado con JWT)
   ▼
Spring Boot  ──2. presigned GET S3──►  el DS descarga el documento
   ▲
   │  3. POST /api/v1/documents/onlyoffice/callback?documentId=…  (al guardar)
OnlyOffice Document Server  ──4. el backend descarga el archivo editado y lo sube a S3 como nueva versión
```

- El backend **firma** toda la config con el secreto compartido (`onlyoffice.jwt-secret`).
- El callback es público en la capa HTTP (`SecurityConfig`) pero se autentica con el JWT del DS.
- Cada guardado (status `2` cerrado / `6` force-save) genera una nueva `s3Key` y agrega la anterior
  a `DocumentVersion` (RF-07).

## Levantar el Document Server

```bash
docker compose -f onlyoffice/docker-compose.yml up -d
```

El editor queda en <http://localhost:8082>. La primera vez la imagen pesa ~2 GB y el contenedor
tarda ~1 min en quedar `healthy`.

## Configuración del backend (`application.properties`)

| Propiedad | Default | Descripción |
|---|---|---|
| `onlyoffice.document-server-url` | `http://localhost:8082` | URL del DS que carga `api.js` el navegador |
| `onlyoffice.callback-base-url` | `http://host.docker.internal:3000` | Cómo alcanza el **contenedor** al backend |
| `onlyoffice.jwt-secret` | `ibpms-onlyoffice-secret-change-me` | Debe coincidir con `JWT_SECRET` del compose (≥ 32 bytes) |

> **Importante:** `JWT_SECRET` en `docker-compose.yml` y `onlyoffice.jwt-secret` en el backend
> deben ser idénticos, o el DS rechazará la config / el backend rechazará el callback.

> En Docker Desktop (Windows/Mac) el contenedor llega al backend del host vía
> `host.docker.internal` (ya mapeado con `extra_hosts`). En Linux usa la IP del host o
> `--network host`.

## Probar end-to-end

1. `docker compose -f onlyoffice/docker-compose.yml up -d`
2. Arrancar el backend (`./mvnw spring-boot:run`) y el frontend (`npm start`).
3. Autenticarse y navegar a `documents/{id}/edit` (con un documento `.docx`/`.xlsx` existente).
4. Editar y cerrar la pestaña → el DS llama al callback → se crea una nueva versión en S3.

## Producción

- Cambiar `JWT_SECRET` / `onlyoffice.jwt-secret` por un secreto fuerte.
- Servir el DS bajo HTTPS (los navegadores bloquean contenido mixto).
- `onlyoffice.callback-base-url` debe ser una URL que el contenedor del DS pueda resolver.
