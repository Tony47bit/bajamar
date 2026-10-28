# Bajamar

App Android nativa (Kotlin + Jetpack Compose) para pesca submarina, apnea y buceo: condiciones del mar con datos
reales, mapa con puntos privados y bitácora local con estadísticas. Todo se guarda en el dispositivo.

Inspirada en el tipo de funciones públicas de «Crystal - Pesca Submarina», con identidad visual y código propios.
Análisis completo en [`docs/ANALISIS_FUNCIONAL.md`](docs/ANALISIS_FUNCIONAL.md).

> **Estado:** la versión 0.1 compiló y funcionó en GitHub Actions. **Esta actualización (hoy/mañana, visibilidad,
> sin exportación JSON) NO se ha compilado**: el entorno donde se escribió no alcanza Maven ni el Android SDK.
> Compílala con el workflow de Actions (o Android Studio) y revisa [Estado de verificación](#estado-de-verificación).

## Qué hace

| Módulo | Contenido |
|---|---|
| **Inicio** | Condiciones de la ubicación elegida: aire, agua, viento y rachas, oleaje, mar de fondo, mareas, próximas 24 h, próximos días, valoración orientativa, hora de la última actualización y fuente. Estados de carga, error, sin conexión y sin datos |
| **Previsión** | Solo **hoy y mañana** (selector de dos días, en la zona horaria de la ubicación), visibilidad submarina estimada, gráficos (viento, oleaje, periodo, temperatura, nivel del mar), tabla de mareas, comparación de dos horas, fuente y atribución |
| **Mapa** | OpenStreetMap, «Mi ubicación» (permiso contextual), búsqueda de localidades, ir a coordenadas, guardar/editar/eliminar puntos privados, ver condiciones de un punto |
| **Bitácora** | Crear, ver, editar y eliminar jornadas: fecha/hora, actividad, ubicación, duración, **visibilidad que viste** (calibra la estimación de ese punto), condiciones adjuntas (marcadas como de previsión), apnea, capturas (especie, peso, piezas), fotos (galería o cámara), notas. Filtros por actividad, ubicación, especie y fechas; orden |
| **Estadísticas** | Jornadas por mes, tiempo total, capturas y peso, evolución, capturas por especie, apnea, comparativa de 30 días. Solo con datos reales; vacío si no hay |
| **Perfil** | Eliminar todos los datos, qué sale del dispositivo, fuentes y licencias |

**Honestidad de datos:** un valor ausente se muestra como «—» o «Sin datos», nunca como 0. La **visibilidad
submarina** es una **estimación** (rango en metros con confianza baja/media) con tres capas: observación reciente de satélite, valor típico de la zona
en esa época y, si no hay nada, una señal cualitativa del mar sin metros. Solo dice «no estimable» sin ningún dato. Ver
[`docs/VISIBILIDAD_SUBMARINA.md`](docs/VISIBILIDAD_SUBMARINA.md). Las mareas se calculan a partir del nivel
del mar del modelo y **no son una tabla oficial**.

**No incluido (todavía):** alertas, noticias, suscripción/Play Billing, ajustes de unidades/idioma/tema, cuentas y
sincronización. Las funciones premium se han ignorado a petición del usuario.

## Obtener el APK

No hay claves ni cuentas que configurar (Open-Meteo y OpenStreetMap no las requieren). Elige una vía:

### A) GitHub Actions (sin instalar nada)

1. Sube esta carpeta a un repositorio tuyo (privado vale).
2. Pestaña **Actions → Android CI → Run workflow** (o haz *push* a `main`).
3. Al terminar, descarga el artefacto **bajamar-debug-apk** (`app-debug.apk`) y el de informes de tests.
4. El job `instrumented` (solo en lanzamiento manual) ejecuta las pruebas de Room y de navegación en un emulador.

### B) Android Studio

1. Instala Android Studio reciente (incluye JDK 17 y el SDK).
2. `File → Open` → esta carpeta. Espera a *Gradle Sync*; acepta instalar el SDK 35 si lo pide.
3. Selecciona un dispositivo o emulador y pulsa **Run**.

### C) Terminal

Requiere JDK 17 y Android SDK 35 (`ANDROID_HOME` definido o `local.properties` con `sdk.dir=...`).

```bash
./gradlew testDebugUnitTest          # pruebas unitarias
./gradlew assembleDebug              # APK de depuración
# → app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest  # pruebas instrumentadas (emulador/dispositivo conectado)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Las versiones de `gradle/libs.versions.toml` (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Room 2.6.1…) se
> eligieron de memoria de la serie estable de finales de 2024 y **no se pudieron resolver contra Maven** al
> escribirlas. Si Gradle se queja de alguna, actualízala con Android Studio (*Refactor → Update dependencies*).

## Estado de verificación

| Qué | Estado |
|---|---|
| Compilación Gradle / generación del APK | **No realizada** (sin acceso a SDK ni repositorios Maven) |
| Lógica de dominio, mapeo de Open-Meteo, repositorio meteorológico con caché y errores, formato, ventana hoy/mañana, visibilidad, validación de formularios, filtros y estadísticas | **188 pruebas ejecutadas y superadas**, compilando esas fuentes con `kotlinc` y JUnit 4 reales, pero con *stubs* mínimos de las anotaciones de Room/Retrofit y de `lifecycle`/`coroutines-test` (no son las librerías reales) |
| ViewModels (crear/editar/eliminar jornada, capturas, fotos, condiciones, filtros, mapa, búsqueda, errores de red) | Incluidos en esas 188 pruebas, con las mismas salvedades |
| `HttpErrorMappingTest`, `OpenMeteoJsonTest`, `ErddapKd490Test` (usan Retrofit, OkHttp y kotlinx.serialization reales) | Escritas, **no ejecutadas** (16 pruebas) |
| `RoomPersistenceTest` (persistencia real, cascadas, reapertura de la base), `MigrationTest` (migración 1→2) y `NavigationTest` (Compose) | Escritas, **no ejecutadas** (16 pruebas instrumentadas) |
| Código de interfaz Compose, osmdroid, ubicación, cámara/galería, Room/KSP | **Revisado a mano y con un verificador de imports, nunca compilado**. Es probable que la primera compilación muestre algún error menor |
| Llamadas reales a Open-Meteo y a ERDDAP/NOAA (Kd490) | **No probadas** (host bloqueado); la URL de ERDDAP y la vigencia del dataset hay que comprobarlas con una consulta real. Los nombres de parámetros salen de la documentación conocida de la API; los JSON de las pruebas son ejemplos escritos a mano |
| Diseño en pantalla, accesibilidad, rendimiento | **No comprobados** en dispositivo |

Las 188 pruebas se ejecutaron fuera de Gradle, con `kotlinc` y JUnit sueltos y esos *stubs*, así que demuestran la
lógica pero **no** sustituyen a la verificación real: `./gradlew testDebugUnitTest` (vías A, B o C) ejecuta las 203
unitarias con las librerías reales y es lo primero que conviene lanzar.

## Estructura

```
app/src/main/java/com/pescasub/app/
  domain/    modelos, reglas y contratos (Kotlin puro, sin Android): valoración, mareas, filtros,
             estadísticas, validación, visibilidad
  data/
    local/   Room: entidades, DAOs, mapeos, repositorios
    remote/  Open-Meteo: API Retrofit, proveedor, mapper, búsqueda de lugares
    repo/    repositorio meteorológico con caché, fotos, ubicación elegida, borrado de datos
  ui/        AppRoot (navegación), temas, componentes, gráficos
    home/ conditions/ map/ logbook/ profile/
  AppContainer.kt  dependencias (manual, sin Hilt)
app/src/test/        pruebas unitarias + dobles de prueba en memoria
app/src/androidTest/ pruebas instrumentadas (Room, navegación)
docs/ANALISIS_FUNCIONAL.md
.github/workflows/android.yml
```

Tecnologías: Kotlin, Jetpack Compose, Material 3, MVVM, Room, DataStore, Retrofit, kotlinx.serialization,
osmdroid, Coroutines/Flow, Coil, Play Services Location. `minSdk 26`, `targetSdk/compileSdk 35`.

## Servicios externos, licencias y límites

- **Open-Meteo** (tiempo, mar, geocodificación): sin clave. El plan gratuito es **solo para uso no comercial**; si
  publicas la app con fines comerciales necesitas su plan de pago. Atribución CC BY 4.0 (ya se muestra en la app).
- **OpenStreetMap** (teselas): la política de uso del servidor público no permite un uso intensivo. Para una app
  publicada con muchos usuarios, contrata un proveedor de teselas y cambia la fuente en `MapScreen.kt`. Atribución
  «© OpenStreetMap contributors» visible en el mapa.
- Se envía a Open-Meteo solo la posición **redondeada a ~1 km**; nada de lo guardado sale del dispositivo.

## Publicar una versión firmada (de forma segura)

1. Crea una clave **fuera del repositorio**: `keytool -genkeypair -v -keystore ~/claves/bajamar.jks -alias bajamar -keyalg RSA -keysize 2048 -validity 10000`.
2. Guarda las contraseñas en `~/.gradle/gradle.properties` (no en el proyecto): `BAJAMAR_STORE_FILE`, `BAJAMAR_STORE_PASSWORD`, `BAJAMAR_KEY_ALIAS`, `BAJAMAR_KEY_PASSWORD`.
3. Añade en `app/build.gradle.kts` un `signingConfigs { create("release") { ... } }` que lea esas propiedades y asígnalo a `buildTypes.release`.
4. Para Google Play genera un bundle: `./gradlew bundleRelease`. Activa `isMinifyEnabled = true` solo tras probar las reglas de `proguard-rules.pro`.
5. Nunca subas `.jks`, `.keystore` ni contraseñas (el `.gitignore` ya los excluye). Considera *Play App Signing*.

## Migraciones de base de datos

La base está en la versión 2 (la migración 1→2 solo añade dos columnas nulables a `sessions`; ver `docs/VISIBILIDAD_SUBMARINA.md`). Al cambiar entidades: sube `version` en
`AppDatabase`, añade la `Migration` a `AppDatabase.MIGRATIONS`, conserva el JSON de `app/schemas/` y añade una prueba
con `MigrationTestHelper`. No uses `fallbackToDestructiveMigration`: son datos personales.

## Limitaciones conocidas

- La visibilidad es una estimación con un píxel de satélite de ~4 km (poco fiable en calas); sin «actividad de peces». La valoración usa solo oleaje y viento previstos.
- Previsión limitada a hoy y mañana por diseño.
- Mapa: se conserva OpenStreetMap; Google Maps requiere clave y facturación (ver [`docs/MAPA.md`](docs/MAPA.md)).
- Las previsiones son de modelo (resolución de varios kilómetros): cerca de la costa pueden diferir de lo real.
- Sin alertas ni notificaciones, noticias, suscripción, ajustes de unidades/idioma/tema, cuentas ni sincronización.
- Solo en español.

## Próximos pasos recomendados

1. Compilar y corregir lo que aparezca; pasar las 16 pruebas instrumentadas (incluida `MigrationTest`) y guardar el `2.json` que genere Room.
2. Probar en un dispositivo real: diseño, rendimiento del mapa, permisos denegados, modo avión.
3. Alertas locales con WorkManager (respetando los límites de segundo plano de Android).
4. Ajustes de unidades y tema; módulo de noticias con una fuente autorizada.
5. Comprobar con una consulta real el dataset de Kd490 y valorar una fuente de turbidez costera de mayor resolución.
