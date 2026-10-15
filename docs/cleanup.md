# Limpieza del proyecto

Se retiraron elementos sin referencias activas en código, recursos o configuración:

- `app/google-services.json` y el plugin raíz de Google Services. La app utiliza
  Supabase, no un plugin o SDK de Firebase.
- `SessionErrors.kt`: el manejo de sesión ya está en `ScreenViewModel`.
- El PNG antiguo `picgram_wordmark.png`. El logo negro mate actual y los iconos
  del launcher se conservan.
- Siete colores XML y once constantes del tema anterior sin usos. Se mantienen
  `Pink80` y `Purple40`, que utiliza Liquid Glass.
- Las pruebas de plantilla `ExampleUnitTest` y `ExampleInstrumentedTest`; todas las
  pruebas específicas de PicGram se conservan.
- Las dependencias de Espresso, AndroidJUnit y pruebas Compose, cuyo único test
  instrumentado era la plantilla. No quedan tests de dispositivo; para crear uno
  nuevo será necesario volver a configurar esas dependencias y el runner.
- El archivo R8 de plantilla, que solo tenía comentarios.
- Siete métodos redundantes de interfaces de repositorio y sus implementaciones
  y falsos de prueba. Se retira también la consulta REST `getUserProfile`, sin
  llamadas; `getFollowingIds` sigue siendo un helper privado del feed.

No se retiran clientes REST utilizados internamente: la renovación de tokens,
subida de archivos y borrado de publicaciones siguen activos. Los alias de modelos
se conservan porque existen usos. También permanecen las previews de Android
Studio, Gradle Wrapper, migraciones SQL, documentación, configuración de respaldo,
`local.properties` y carpetas del IDE. No se modifica el servidor ni cuentas reales.

## Recuperación

Antes de los borrados se guardaron los archivos y configuraciones principales
afectados en `.cleanup-backups/cleanup-2026-10-13.zip`, con rutas relativas al
proyecto. Para recuperarlos, extraer las versiones que se necesiten y revisar los
cambios antes de reemplazar archivos actuales. Los archivos Kotlin y Gradle
relacionados deben restaurarse de forma coherente.

La copia es local y está excluida de Git; puede contener configuración del proyecto
Firebase anterior. No debe publicarse. La documentación nueva no depende de ella.

## Verificación

Compilar el APK, ejecutar las pruebas y revisar lint. Lint se ejecuta con el ajuste
preexistente que omite el detector Bidi que se bloquea:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --no-configuration-cache
.\gradlew.bat :app:lintDebug --offline --no-configuration-cache -I scripts/lint-without-bidi.init.gradle
```

La cantidad de pruebas unitarias baja de 47 a 46 únicamente por retirar la prueba
de suma de la plantilla. Sigue pendiente la verificación funcional en dispositivo.

Resultado: compilación correcta, 46 pruebas sin fallos y lint completado sin
errores ni avisos de recursos sin uso, con la exclusión Bidi indicada arriba.
