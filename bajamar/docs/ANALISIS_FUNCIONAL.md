# Análisis funcional — Bajamar

App Android propia, inspirada en el **tipo de funciones públicas** de «Crystal - Pesca Submarina». No copia código,
recursos, marca ni algoritmos de Crystal: solo se usó su ficha pública para entender el tipo de producto.

Fuentes consultadas el 20-oct-2026:

- Ficha de Google Play (`es.crystalapp.app`, última actualización indicada: 25-sep-2026).
- Web oficial `https://crystalapp.es/`.

> No se ha podido instalar ni ejecutar la app de referencia, ni consultar sus capturas. Todo lo que dependa de ver
> pantallas reales queda en la sección 3.

## 1. Funciones verificadas en fuentes públicas

| Función | Fuente |
|---|---|
| Pronóstico de tiempo y mar para pesca submarina, apnea y buceo: olas, viento, mareas, mar de fondo, periodo, visibilidad del agua y actividad de los peces | Descripción en Google Play |
| Tablas de mareas de consulta rápida | Google Play |
| Cuaderno de bitácora: jornadas, capturas con especie, peso y fotos, tiempos de apnea | Google Play y web |
| Estadísticas de apnea para seguir la progresión | Google Play |
| Historial de jornadas para revisar qué días hubo mejores pescas | Google Play |
| Alertas configurables cuando las condiciones son favorables en la zona; en la web, «aviso inmediato» cuando un spot favorito alcanza un buen *score* | Google Play y web |
| Puntos GPS guardados, con consulta del tiempo y las olas en ellos; descritos como privados y cifrados | Google Play |
| Predicciones a 7 días (visibilidad, mar de fondo, viento, olas…) | Web |
| Bitácora que «aprende de tus patrones» | Web |
| Modelo de negocio: la web dice «Todo gratis»; Google Play indica compras integradas y anuncios, y las novedades mencionan «Crystal PRO» | Web y Google Play (**contradicción aparente**, sin precios ni contenido del plan PRO) |

## 2. Funciones deducidas razonablemente

- Una pantalla de **inicio/resumen** con las condiciones de la zona (es la forma natural de mostrar los datos anteriores).
- Un **mapa** donde se ven y se crean los puntos guardados (la ficha habla de «puntos GPS guardados»).
- Un **listado de jornadas** con filtros (se habla de «historial»).
- Ajustes y perfil básicos.

Son suposiciones de producto, no hechos confirmados sobre Crystal.

## 3. No verificable sin la app instalada

- Pantallas concretas, navegación y flujos reales.
- Cómo se calcula la visibilidad del agua, la «actividad de los peces» y el *score*; de qué fuentes salen los datos.
- Qué incluye exactamente Crystal PRO.
- Cómo se cifran o protegen los puntos GPS.
- Si hay sincronización entre dispositivos o cuenta de usuario.

Bajamar **estima** la visibilidad con un modelo documentado y honesto (`docs/VISIBILIDAD_SUBMARINA.md`) y **no** replica la actividad de los peces (ver §6).

## 4. Alcance de esta primera versión

Prioridades indicadas por el usuario: **inicio, previsión real, mapa y bitácora local**. Funciones premium
ignoradas hasta nuevo aviso.

| Módulo (brief original) | Estado |
|---|---|
| 2.1 Inicio y panel de condiciones | Implementado |
| 2.2 Mapa de ubicaciones | Implementado |
| 2.3 Cuaderno de bitácora (CRUD, filtros, fotos) | Implementado |
| 2.4 Estadísticas personales | Implementado (pantalla dentro de Bitácora) |
| 2.5 Previsión horaria y mareas | Implementado |
| 2.6 Sistema de alertas | **No implementado** (fuera de las prioridades) |
| 2.7 Noticias | **No implementado** (no hay fuente autorizada integrada) |
| 2.8 Perfil, ajustes y suscripción | **Parcial**: borrar, privacidad, fuentes y acerca de. Sin ajustes de unidades/idioma/tema (sigue el tema del sistema) ni suscripción |
| Backend / sincronización / cuentas | **No hay** (decisión de privacidad, ver §7) |

## 5. Pantallas y flujos

Navegación inferior: **Inicio · Mapa · Bitácora · Previsión · Perfil**.

```
Inicio ──(Ver previsión detallada)──▶ Previsión
   ▲  └─ elegir ubicación (buscar / mi ubicación / mis puntos)
   │
Mapa ──toca un punto──▶ tarjeta ──Ver condiciones──▶ Inicio
   │                       ├─ Guardar / Editar / Eliminar
   │                       └─ Coordenadas, Mis puntos, Mi ubicación
Bitácora ─▶ Detalle de jornada ─▶ Editar ─▶ (guardar) ─▶ Detalle
   │  ├─ Nueva jornada (fecha, hora, actividad, ubicación, condiciones, apnea, capturas, fotos, notas)
   │  └─ Estadísticas
Perfil ─▶ Eliminar todos los datos · Fuentes y licencias · Acerca de
```

### Ubicación y permisos

- El permiso de ubicación se pide **solo al pulsar «Mi ubicación»**. Si se rechaza, la app sigue siendo usable
  buscando una localidad, escribiendo coordenadas o tocando el mapa.
- No se declara el permiso de cámara: la foto la toma la app de cámara del sistema.
- Las fotos se copian al almacenamiento privado, **sin metadatos EXIF** (se pierde la posición GPS de la foto) y se
  limita su tamaño.

## 6. Datos externos y decisiones técnicas

| Necesidad | Decisión | Motivo / límites |
|---|---|---|
| Tiempo (aire, viento, rachas) | Open-Meteo `v1/forecast` | Sin clave. Uso gratuito **solo no comercial**; atribución CC BY 4.0 |
| Oleaje, mar de fondo, temperatura del agua | Open-Meteo `v1/marine` | Cobertura variable cerca de la costa; si falta, se muestra «Sin datos» |
| Mareas | Nivel del mar del modelo (`sea_level_height_msl`) → pleamares/bajamares calculadas | **No es una tabla oficial de mareas**; la pantalla lo avisa |
| Búsqueda de localidades | Open-Meteo Geocoding | Solo se envía el texto escrito |
| Mapa | OpenStreetMap con osmdroid | Sin clave ni cuenta; atribución visible. Los puntos guardados nunca se envían |
| **Visibilidad submarina** | **Estimación** (rango, confianza baja/media) | Kd490 de satélite NOAA + contexto de oleaje/lluvia; «no estimable» si faltan datos |
| Valoración «favorable/aceptable/desfavorable» | Heurística propia (oleaje y viento) | Orientativa; si falta oleaje o viento nunca da «favorable» |

Arquitectura: Kotlin, Jetpack Compose + Material 3, MVVM, capas `domain` (puro, sin Android) / `data` / `ui`,
Room, DataStore, Retrofit + kotlinx.serialization, osmdroid, Coroutines/Flow. Inyección de dependencias manual
(`AppContainer`) en lugar de Hilt, para reducir piezas en la primera versión; migrar a Hilt es mecánico.
El proveedor meteorológico está detrás de la interfaz `WeatherProvider`, así que se puede cambiar de fuente sin
tocar la lógica.

## 7. Privacidad (requisito prioritario)

- Todo se guarda **solo en el dispositivo**. Sin cuenta, sin servidor propio, sin analítica.
- A Open-Meteo solo se envían coordenadas **redondeadas a ~1 km** (nunca el punto exacto ni su nombre).
- Copia de seguridad en la nube y transferencia entre dispositivos **desactivadas** (`allowBackup=false` y reglas de
  extracción vacías).
- No hay registros de coordenadas en logs ni interceptores de red.
- Eliminar todos los datos desde Perfil (la exportación JSON se retiró).

## 8. Modelo de datos (Room, versión 1)

`locations` · `sessions` (FK `locationId` → `SET NULL`) · `catches` (FK `sessionId` → `CASCADE`) ·
`photos` (FK `sessionId` → `CASCADE`) · `forecast_cache`.

Fechas en UTC (milisegundos) + zona horaria de la jornada; las condiciones adjuntadas a una jornada se marcan con
su fuente para distinguirlas de lo escrito a mano. Sin `fallbackToDestructiveMigration`: son datos personales. El
esquema se exporta a `app/schemas/` en cada compilación; al subir de versión hay que añadir la migración y su prueba.
