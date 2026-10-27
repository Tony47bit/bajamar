# Visibilidad submarina estimada: fuentes, modelo y limitaciones

> **Es una estimación, no una medición.** Ningún dato de la app mide la transparencia del agua en tu punto de
> pesca. El resultado es un **rango aproximado** que puede equivocarse mucho en costa somera.

## 1. Qué datos hay (y cuáles no)

| Fuente | Qué aporta | ¿Sirve para la visibilidad? |
|---|---|---|
| Open-Meteo `forecast` | Viento, temperatura del aire, **precipitación** (horaria, incluye 2 días pasados) | Solo como contexto (lluvia reciente) |
| Open-Meteo `marine` | Altura/periodo/dirección de oleaje y mar de fondo, temperatura del mar, nivel del mar | Solo como contexto (estado del mar) |
| **NOAA CoastWatch (ERDDAP), VIIRS S-NPP, producto `nesdisVHNSQkd490Daily`** | **Kd490**: coeficiente de atenuación difusa de la luz a 490 nm (1/m), diario, malla de 4 km, sin clave | **Sí: es el único indicador real de transparencia del agua** |

Open-Meteo **no** ofrece turbidez, clorofila, materia en suspensión ni profundidad de Secchi. No se ha añadido ninguna
otra API «por tener muchas variables». No se usan corrientes (no hay dato fiable y gratuito para la costa).

Fuentes descartadas: productos Copernicus Marine (requieren cuenta y credenciales; no se pueden distribuir en una app
sin servidor propio) y cualquier modelo que no tenga relación física con la transparencia.

### Limitaciones del producto de satélite
- **Resolución ~4 km**: un píxel mezcla agua somera, rocas, arena y aguas abiertas. En calas y costa poco profunda
  puede no representar el agua donde buceas (el fondo y la espuma de rompiente contaminan la señal).
- **Nubes y sol**: sin observación los días nublados; por eso se usa la última observación válida de los **últimos
  7 días**. Pasado ese plazo la app dice «no estimable».
- **Es una observación pasada**, no una previsión. Para «mañana» se muestra la estimación actual, avisándolo.
- **No validado por nosotros** en aguas costeras. Kd490 describe la atenuación a 490 nm; la relación con la
  visibilidad horizontal de un buceador es aproximada.
- El metadato del dataset visto al diseñarlo indicaba datos hasta el 2026‑07‑09. **No lo he podido comprobar contra el
  servidor desde el entorno de desarrollo (host bloqueado).** Si el producto está detenido o cambia de nombre, la app
  mostrará «Visibilidad no estimable» y nada más; hay que comprobar el identificador (`ErddapClarityRepository.DATASET_ID`)
  y la URL con una consulta real antes de fiarse de la función.

## 2. Modelo (`domain/visibility`)

1. **Ancla**: `Zsd ≈ 1,7 / Kd490` (profundidad de disco de Secchi; relación de Poole y Atkins, 1929, definida
   con la atenuación de la luz visible; usar Kd490 es una aproximación).
2. **Banda mostrada**: visibilidad horizontal ≈ `0,85 × Zsd` a `1,15 × Zsd` (±15 %, p. ej. «6–8 m»). Es una **banda
   de presentación estrecha, decisión de diseño pedida para que el dato sea legible**; **no es un intervalo
   estadístico**: la incertidumbre real es bastante mayor (el aviso de la tarjeta lo dice). Hasta la versión 0.3 la
   banda era 0,5–1,5× (p. ej. «5–15 m»), que reflejaba mejor esa incertidumbre pero resultaba poco útil.
3. **Estado del mar y lluvia** (oleaje > 1,5 m en la ventana observación→+24 h; ≥ 20 mm de lluvia en 48 h): **no se
   convierten en metros** (no hay calibración que lo respalde). Solo **desplazan la banda hacia abajo** (`0,65–0,95 × Zsd`)
   y bajan la confianza. **Nunca mejoran** la estimación.
4. **Redondeo**: metros enteros hasta 20 m y múltiplos de 5 m por encima. Sin decimales.
5. **Confianza**: solo **baja** o **media** (nunca «alta»; el producto no está validado en costa somera). Es una
   etiqueta cualitativa, **no una probabilidad**. Es media solo si: observación de ≤ 2 días, actualizada ahora,
   mar y lluvia sin recorte y con datos de ambos. Cualquier otra cosa → baja.
6. **Capas** (`VisibilityResolver`): siempre se muestra la **mejor capa disponible** y la interfaz dice cuál es:
   1. *Observación reciente* de satélite (≤ 7 días): rango en metros, confianza baja o media.
   2. *Valor típico de la zona* en este mes (mediana de los compuestos mensuales `nesdisVHNSQkd490Monthly` de años
      anteriores, mínimo 1; se muestra el nº de años en el modelo): rango en metros **punteado**, confianza **siempre
      baja**, con el texto «no es el valor de hoy». El mar agitado o la lluvia siguen pudiendo recortarlo.
   3. *Señal cualitativa del mar* (sin metros): «Posible visibilidad reducida» / «Sin señales de empeoramiento».
      Solo valora si el oleaje o la lluvia recientes suelen enturbiar; no es una cifra.
   4. **No estimable** solo si no hay ni observación, ni valor típico, ni datos de oleaje o lluvia; también si el
      dato es inválido (fuera de 0,01–6,0 1/m, NaN, fecha futura) y no hay nada mejor.
   Una capa inferior nunca se presenta como la superior.
7. **Datos viejos**: si la red falla se usa la última observación guardada **marcándola explícitamente** como no
   actualizada, y la fecha de la observación se muestra siempre.

Los umbrales de oleaje reutilizan los de la valoración de condiciones; el de lluvia (20 mm/48 h) y la banda ±15 %
son reglas prácticas **sin calibración**. Si más adelante se comparan con medidas reales (p. ej. un registro propio de
visibilidad en la bitácora), podrán ajustarse.

## 3. Arquitectura
- `domain/visibility/VisibilityModel.kt`: tipos (observación, estimación, factores, motivos).
- `domain/visibility/VisibilityEstimator.kt`: validación (`VisibilityValidator`) y cálculo (`VisibilityEstimator`),
  Kotlin puro.
- `data/remote/ErddapKd490.kt`: obtención (`ErddapClarityRepository`) y lectura (`ErddapKd490Parser`) de ERDDAP.
  Envía solo la posición redondeada a ~1 km. Caché en memoria 6 h.
- `ui/conditions/VisibilityCard.kt` + textos en `ui/UiText.kt`: solo presentación.
- `ConditionsViewModel` une las piezas y nunca bloquea la previsión si la visibilidad falla.

## 3b. Presentación
La tarjeta muestra un gráfico grande (escala de turbia a clara con el rango resaltado) y el rango en metros; los
factores y el aviso quedan plegados bajo «¿Cómo se calcula?». Los colores de la escala son solo visuales: no
equivalen a categorías calibradas, y las cifras van siempre junto al gráfico.

## 3c. Calibración con tus anotaciones (versión mínima)

En «Nueva jornada» hay un campo opcional **«Visibilidad que viste» (1–60 m, enteros)**. Al guardar, si la jornada tiene
**ubicación guardada** y es de las **últimas 24 h**, la app guarda también la cifra central que el modelo daba en ese
momento, **sin ajustar** (`estimatedVisibilityM`). Con ese par (visto / estimado):

- Para cada anotación se calcula `visto ÷ estimado`; el **factor del punto es la mediana** de esos cocientes.
- Hacen falta **al menos 3 anotaciones del mismo punto guardado** de los últimos 365 días.
- El factor se limita a **0,4–2,5** para que datos raros no desvirtúen el resultado.
- Se aplica a la profundidad de Secchi de las capas basadas en satélite (observación y valor típico), antes de formar la
  banda. La tarjeta indica «Ajustada con tus N anotaciones de este punto».
- No se aplica a puntos temporales (sin ubicación guardada) ni a la capa cualitativa.

Limitaciones: es un ajuste empírico simple, no un modelo estadístico; una jornada anotada días después o sin ubicación
guardada se conserva en la bitácora pero **no cuenta** para calibrar (no hay estimación de ese momento con la que
compararla); y ajusta el nivel medio del punto, no los coeficientes del modelo. La estimación guardada es siempre la
no calibrada, para que el ajuste no se retroalimente.

**Base de datos**: versión 2 de Room. La migración `MIGRATION_1_2` solo **añade dos columnas nulables** a `sessions`
(`observedVisibilityM`, `estimatedVisibilityM`); no borra ni modifica nada. `app/schemas/.../1.json` está escrito a mano
(identityHash ficticio) para poder probar la migración; Room generará el `2.json` real al compilar y conviene guardarlo
en el repositorio.

## 4. Qué datos mejorarían la estimación
- Una fuente de turbidez/Kd con mayor resolución costera (p. ej. Sentinel‑2/3 procesado, o Copernicus Marine con
  cuenta propia y un pequeño servidor intermedio).
- Más registros reales de visibilidad (la bitácora ya permite anotarlos) para calibrar con más puntos.
- Datos de caudal de ríos/escorrentía y de corrientes para la zona.

## 5. Pruebas
`VisibilityEstimatorTest` (datos completos, incompletos, ausentes, antiguos, inválidos, redondeo, recorte),
`ErddapKd490Test` (lectura de JSON y construcción de la URL) y el cableado en `ConditionsViewModelTest`.
