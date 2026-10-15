# Arquitectura MVVM de PicGram

La app mantiene sus Activities y navegación existentes. La migración separa
renderizado, estado de pantalla y acceso a datos, sin cambiar el esquema de
Supabase, los enlaces de recuperación ni los formatos de borradores.

```text
com.example.picgram/
├── *Activity.kt           Puentes Android: navegación, intents y selector de fotos
├── ui/
│   ├── screens/           Composables de las 10 pantallas, sin consultas de datos
│   ├── theme/             Liquid Glass y tema existente
│   └── ViewModelBindings.kt   Factory y resultados UI pendientes
├── presentation/
│   ├── *ViewModel.kt      Acciones, validaciones y coordinación de repositorios
│   └── *UiState           Estado inmutable expuesto mediante StateFlow
└── data/
    ├── model/Models.kt    Post, Comment, UserProfile, UserSession y AuthResult
    ├── AuthRepository.kt Autenticación y recuperación
    ├── SocialRepository.kt   Publicaciones, likes, comentarios, perfiles y follows
    └── AppContainer.kt    Inyección manual, sesión, multimedia y borradores
```

## Flujo

```text
Interacción → ViewModel → Repository → REST / Storage / preferencias
                 ↓
          UiState (StateFlow) → composable
```

Las Activities obtienen su ViewModel con `ViewModelProvider`. No crean uno nuevo
en cada recomposición ni al rotar. Las operaciones usan `viewModelScope`, y el
estado se observa con `collectAsStateWithLifecycle`. Los adaptadores de repositorio
ejecutan la red y el trabajo de archivos en `Dispatchers.IO`; el ViewModel no
retiene una Activity ni un Context.

Los clientes REST ya existentes (`SupabaseAuth`, `SupabaseDatabase`,
`SupabaseStorage`) y las utilidades `SessionManager`, `MediaFiles`, `PostOperations`
se conservan como fuentes de datos de bajo nivel. Solo la capa de repositorio los
utiliza desde las pantallas migradas. Los alias del paquete original conservan la
compatibilidad de los modelos, cuya definición está en `data/model`.

Los mensajes y solicitudes de navegación son estado pendiente (`UiNotice`), con
identificador y confirmación de consumo. Así pueden sobrevivir a una rotación sin
capturar funciones de la Activity. Navegar y mostrar el mensaje sigue siendo
responsabilidad de la vista.

## Pantallas migradas

| Pantalla | ViewModel |
|---|---|
| Inicio de sesión | LoginViewModel |
| Registro | RegisterViewModel |
| Feed | FeedViewModel |
| Nueva publicación | CreatePostViewModel |
| Detalle y comentarios | PostDetailViewModel |
| Perfil y avatar | ProfileViewModel |
| Búsqueda | SearchViewModel |
| Seguidores / seguidos | ConnectionsViewModel |
| Solicitar recuperación | ForgotPasswordViewModel |
| Restablecer contraseña | ResetPasswordViewModel |

La selección de medios sigue en `ActivityResultContracts.PickVisualMedia`. La
vista pasa el URI al ViewModel; el repositorio procesa el archivo y limpia el
temporal. Los reproductores y sus controles conservan su ciclo de vida como
componentes UI: no pertenecen al ViewModel de pantalla.

## Estado y compatibilidad

- Los borradores de publicación siguen guardados por usuario en preferencias.
  Los reintentos reutilizan el UUID original.
- El texto y el comentario pendiente conservan el guardado existente en Bundle.
- Las contraseñas y sesiones temporales de recuperación no se persisten.
- `ViewModel` sobrevive a cambios de configuración, no a la muerte del proceso.
  Al restaurar el proceso se recargan datos del servidor y los borradores guardados;
  un enlace de recuperación puede estar caducado y requerir solicitar otro.
- Los estados locales de menús, foco, controles del video y visibilidad de contraseña
  siguen en los composables. No son estado de negocio.
- No se agrega Hilt ni una navegación nueva: `PicGramApplication` crea `AppContainer`
  y la factory inyecta las interfaces de repositorio.

## Verificación

`ViewModelTest` usa repositorios falsos estrictos y tiempo virtual. Cubre doble toque
sin duplicación, rollback de like, expiración de sesión, inicialización única,
búsqueda con debounce, reintento de listas y borradores, rechazo de texto sin medios
y aislamiento de cuentas durante recuperación. También prueba inicio de sesión,
errores de registro, envío de recuperación, reintento de comentarios y rollback
de seguimiento. `ArchitectureBoundaryTest` impide
consultas directas a fuentes de datos desde vistas y dependencias UI en ViewModels.
No se realizan llamadas reales a Supabase en esas pruebas.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --no-configuration-cache
```

Resultado local de la migración: compilación debug correcta y 47 pruebas sin
fallos. La revisión de lint también terminó correctamente usando el ajuste
existente `scripts/lint-without-bidi.init.gradle`; esa ejecución omite únicamente
`BidirectionalTextDetector` por el bloqueo documentado anteriormente. No equivale
a haber ejecutado ese detector.

Todavía se debe comprobar en dispositivo: rotación durante una subida/like,
recuperación por enlace, volver de un perfil, selector de fotos, reproducción,
login/registro y restauración tras cierre del proceso. Las pruebas locales no
confirman la configuración del servidor ni sustituyen estas comprobaciones.

Referencia: [recomendaciones oficiales de arquitectura Android](https://developer.android.com/topic/architecture/recommendations).
