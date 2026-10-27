# Mapa: proveedor actual y migración a Google Maps

## Decisión
Bajamar **conserva osmdroid + OpenStreetMap**. Motivos:
- Google Maps **exige clave de API y una cuenta de facturación activa** en Google Cloud (documentación de Maps SDK
  for Android: «debes habilitar la facturación en cada proyecto e incluir una clave de API en todas las solicitudes»).
  No puedo crear esa clave por ti ni probar la integración.
- Desde este entorno no se pueden resolver las dependencias de Google ni compilar, así que una integración nueva
  podría romper el build de GitHub Actions que ya funciona. El mapa actual funciona y cubre todas las funciones
  pedidas (posición, búsqueda, coordenadas, guardar/editar/eliminar puntos, ver sus condiciones).
- La política de las teselas públicas de OSM no admite uso intensivo: para una app con muchos usuarios habría que
  contratar un proveedor de teselas (ver README).

No se ha dejado ninguna pantalla de Google Maps «simulada» ni claves en el repositorio.

## Si quieres Google Maps (pasos pendientes)
1. En Google Cloud Console: crea un proyecto, **activa la facturación** y habilita **Maps SDK for Android**. Para
   buscar lugares con Google habría que habilitar además Places API (ahora se usa la geocodificación de Open-Meteo).
2. Crea una clave y **restríngela** a «Aplicaciones Android» con el paquete `com.pescasub.app` y la huella SHA‑1 de
   tu certificado (depuración y, después, el de publicación) y a la API «Maps SDK for Android».
3. Guarda la clave **fuera del repositorio**: `MAPS_API_KEY=...` en `local.properties` (ya ignorado por git) y, en
   GitHub Actions, como *secret* del repositorio; léela con el *Secrets Gradle Plugin* o con
   `manifestPlaceholders["MAPS_API_KEY"]`, y declara en el manifiesto
   `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}"/>`.
4. Añade `com.google.android.gms:play-services-maps` y `com.google.maps.android:maps-compose` (elige las versiones
   estables actuales en Maven; no las fijo aquí porque no he podido comprobarlas) y reescribe solo
   `ui/map/MapScreen.kt`: `MapViewModel`, los modelos y las ubicaciones guardadas no cambian.
5. Mantén un respaldo: si `MAPS_API_KEY` está vacía, usa el mapa OSM actual en vez de mostrar un mapa en blanco.
6. Coste: el uso del SDK de mapas móvil se factura por cargas de mapa según los umbrales gratuitos vigentes de
   Google; revísalos en la documentación de precios antes de publicar.
