# Mis Préstamos (versión nativa Android)

Segunda versión de la app, hecha como app Android nativa (Kotlin + Jetpack Compose + Material 3).
Vive aparte de `../mis-prestamos` (la versión web/PWA) y no comparte código con ella.
Se puede tener las dos instaladas a la vez: esta usa el paquete `pe.reizo.prestamos`.

## Generar el APK

```bash
./gradlew assembleRelease
```

Sale en `app/build/outputs/apk/release/app-release.apk` (unos 2 MB), firmado con la llave de
`../firma-android` si esa carpeta existe. Si no existe, se firma con la llave de pruebas.

Otros comandos:

```bash
./gradlew testDebugUnitTest   # pruebas de las reglas de negocio (core)
./gradlew assembleDebug       # versión de pruebas, con el botón "Cargar datos de ejemplo" en Ajustes
```

Requisitos: JDK 17 o superior y el SDK de Android (plataforma 36). La ruta del SDK va en `local.properties`.

## Modo desarrollo (activo ahora)

En `app/build.gradle.kts` está `MODO_DESARROLLO = true`. Mientras siga así:

- La app **no guarda nada**: la base vive en memoria, arranca con 12 clientes de ejemplo y se reinicia al cerrar la app.
- No se conecta a la nube aunque exista `nube.properties`.
- Ajustes muestra el aviso "Modo de prueba" y el botón "Volver a los datos de ejemplo".

Para pasar a producción: poner `MODO_DESARROLLO` en `false`, configurar la nube (más abajo) y generar el APK.
La base definitiva empieza vacía.

## Instalar en el celular

1. Pasar `app-release.apk` al celular (WhatsApp, Drive o cable).
2. Abrirlo y aceptar "Instalar apps de esta fuente" si lo pide.
3. La primera vez, activar en el celular el permiso "Instalar apps desconocidas" para Mis Préstamos
   (Android lo pide solo al tocar Actualizar por primera vez). Así las siguientes actualizaciones son un toque.

## Publicar una actualización

La app revisa sola la última versión publicada en GitHub Releases del repositorio indicado en
`gradle.properties` (`actualizaciones.repo`). Si es más nueva que la instalada, muestra
"Hay una versión nueva" con el botón Actualizar, descarga el APK y abre el instalador de Android.

1. Subir `versionCode` (de uno en uno) y `versionName` en `app/build.gradle.kts`.
2. Generar el APK: `./gradlew assembleRelease`
3. Publicarlo (la etiqueta debe ser `v` + el `versionName`):

```bash
gh release create v1.0.1 app/build/outputs/apk/release/app-release.apk#MisPrestamos-1.0.1.apk --title "1.0.1" --notes "Qué cambió"
```

Notas:
- El repositorio debe ser público para que la app lea las versiones sin contraseña.
- Android solo instala la actualización si el APK está firmado con la misma llave de `../firma-android`. Guardar copia de esa carpeta.
- Play Protect puede mostrar "Analizar app" al instalar un APK que no viene de Play Store; es un toque más.
- Los datos se conservan al actualizar.

## Qué cambia respecto a la especificación

La especificación se tomó como guía. Lo que se decidió distinto y por qué:

| Tema | Especificación | Aquí |
|---|---|---|
| Tecnología | PWA con React | App nativa: Kotlin, Compose, Material 3, Room (SQLite) |
| Bloqueo | PIN propio de 4 dígitos | El bloqueo del celular (huella, rostro, patrón o clave). No hay PIN que olvidar ni pantalla de "Olvidé mi PIN". Se puede apagar en Ajustes. |
| Navegación | Barra inferior siempre visible | Barra inferior en las tres pestañas; las pantallas internas la ocultan y usan flecha de volver y el gesto atrás de Android |
| Nuevo préstamo | Botón a lo ancho sobre la barra | Botón flotante "Nuevo préstamo" |
| Avisos | Arriba, 2.6 s | Abajo (snackbar de Android) |
| Periodos del resumen | Grilla de botones y dos campos de fecha | Botones segmentados y el calendario de rango de Android |
| Menú de un pago | Menú al tocar | Hoja inferior con "Corregir monto" y "Borrar pago" |
| Fuente | Atkinson Hyperlegible | Atkinson Hyperlegible, incluida en el APK |
| Tema | Claro | Se elige en Ajustes: Claro (de fábrica), Oscuro o Como el celular |
| Tamaño de letra | Fijo | Se elige en Ajustes: Normal, Grande o Muy grande, encima del tamaño del celular. Los botones crecen con la letra |
| "Sin pagar" | 15 días fijos | Se elige en Ajustes: 7, 15 o 30 días (de fábrica 15) |
| Montos | Número con decimales | Céntimos enteros en la base; se convierten a soles al exportar o sincronizar |
| Actualizaciones | Aviso "Hay una versión nueva" | Igual: la app revisa GitHub Releases, muestra el aviso y descarga el APK nuevo |
| Eliminar cliente | No existía | En el menú "Más" de la ficha, con confirmación y "Deshacer". Se recupera desde Ajustes > Clientes eliminados |
| Respaldo | Solo guardar | Guardar y también **recuperar** un respaldo. El archivo tiene el mismo formato que el de la versión web, así que sirve para pasar los datos de una a otra. |
| Copia automática | No | Además, Android incluye los datos en la copia de seguridad de Google del celular |

Todo lo demás (reglas de cálculo, filtros, "sin pagar" a los 15 días, ganancia solo al terminar de pagar, textos) sigue la especificación.

## Nube (Supabase), opcional

Sin configurar, la app trabaja solo en el celular. Para activar la nube:

1. Crear el proyecto y ejecutar `../mis-prestamos/supabase/schema.sql` (son las mismas tablas que usa la versión web).
2. Crear la cuenta de la usuaria en Authentication > Users y desactivar el registro público.
3. Copiar `nube.properties.example` como `nube.properties` y llenar `SUPABASE_URL` y `SUPABASE_ANON_KEY`.
4. Generar el APK de nuevo. Al abrir pedirá correo y contraseña una sola vez.

La sincronización sube los cambios pendientes y baja los de la nube al abrir, al volver el internet,
cada 2 minutos y después de cada cambio.

**Pendiente de probar:** la sincronización está escrita pero no se ha probado contra un proyecto Supabase real.

## Cambio en la nube desde la versión 1.0.1

Los préstamos ahora tienen una nota. Antes de activar la nube, ejecutar en el editor SQL de Supabase:

```sql
alter table public.prestamos add column if not exists nota text not null default '';
```

La tipografía es Atkinson Hyperlegible (Braille Institute, licencia SIL Open Font License), incluida en `app/src/main/res/font`.

## Estructura

```
app/src/main/java/pe/reizo/prestamos/
  core/   reglas de negocio puras (cálculos, filtros, periodos, validación, formato) + datos de ejemplo
  data/   Room (BaseDatos), Repo (todos los cambios), Json (respaldo y nube), Nube (Supabase)
  ui/     pantallas Compose: Listas, Ficha, NuevoPrestamo, Resumen, Ajustes, App (navegación), Tema
app/src/test/   pruebas de core
```

Las pantallas solo usan `core` y `Repo`. Para cambiar el esquema de la base: subir `version` en
`BaseDatos` y agregar una migración; nunca borrar la base.
