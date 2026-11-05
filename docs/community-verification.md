# Verificación local — 26 de octubre de 2026

## Resultados confirmados

| Verificación | Resultado |
|---|---|
| `:app:assembleDebug` | Correcto; APK generado |
| `:app:testDebugUnitTest` | 65 pruebas; 0 fallos y 0 errores |
| Lint con el ajuste Bidi preexistente | 0 errores; 44 advertencias |
| `scripts/community-db-test.mjs` | 43 comprobaciones aprobadas sobre PostgreSQL embebido |
| `deno check .../deliver-push/index.ts` | Correcto |
| `deno test --allow-env scripts/push-delivery-test.ts` | 7 pruebas aprobadas; HTTP simulado, sin permiso de red |
| Dispositivos ADB conectados | Ninguno |

Las advertencias de lint incluyen versiones disponibles, estilos y mantenimiento.
No se ha creado una baseline para ocultar errores. La única exclusión del script
es `BidiSpoofing`, ya documentada por el proyecto. Un primer análisis sufrió un
fallo interno FIR al cambiar fuentes durante su ejecución; la ejecución final
terminó correctamente con las fuentes estables.
Se corrigieron los errores reales de Fragment introducidos transitivamente por
Play Services actualizando esa dependencia; no se suprimió su detector.

## Cobertura añadida

- Perfil: validación de longitud y fechas, autorización del propietario,
  bloqueo de doble envío y ausencia de éxito ficticio si falla el guardado.
- Datos personales y correos: consultas de otras cuentas rechazadas/ocultas;
  tampoco se exponen correos heredados de autores a través de posts.
- Seguimientos: petición idempotente, aceptación/rechazo/cancelación, imposibilidad
  de aprobar como tercero o insertar un seguimiento saltándose la aprobación.
- Privacidad: publicaciones y archivos no visibles sin aprobación; permisos
  revocados al dejar de seguir; políticas permisivas antiguas no anulan la barrera.
- Almacenamiento: patrones `%`/`_` no actúan como comodines; referenciar el archivo
  privado de otra cuenta en un post público no vuelve públicos sus bytes.
- Historias: fechas falsificadas sustituidas por hora del servidor, 24 h exactas,
  solo seguidos y propietario, vencimiento en consultas y acceso al archivo.
- Mensajes: aislamiento de tres cuentas, rechazo de emisor falso y lecturas
  falsificadas, contadores, confirmación de leído e historial de 62 mensajes
  paginado sin pérdidas ni duplicados.
- Reintento del envío de mensajes conserva UUID y contenido tras fallo ambiguo.
- Notificaciones: generación desde acciones reales en la base de datos; los
  clientes no pueden inventar avisos ni reclamar trabajo exclusivo del servidor.
- Push: secreto obligatorio, destinatario leído del servidor, entradas malformadas
  rechazadas, eliminación de tokens inválidos, fallos transitorios sin falso
  acuse, reserva idempotente y omisión de alertas ya leídas.
- Se mantienen las pruebas anteriores de autenticación, recuperación, temas,
  publicaciones, avatares, videos y separación MVVM.

## Qué no acreditan estas pruebas

PGlite 0.5.12 se ejecuta con roles y esquemas Supabase simulados sobre PostgreSQL;
no representa una conexión al proyecto remoto. No comprueba la configuración real
de Storage HTTP, publicación Realtime, PostgREST, Edge Functions o credenciales FCM.
Deno 2.9.6 prueba el envío con endpoints sustituidos, no una entrega a un teléfono.
No se han ejecutado pruebas de pantalla, codecs, ciclo de vida en dispositivo ni
entrega de notificaciones con la aplicación cerrada.

La matriz de aceptación y los pasos para realizar esas comprobaciones están en
`docs/community-release.md`. Hasta completar ese despliegue y la prueba con cuentas
y dispositivos reales, **no se declara la funcionalidad validada en producción**.
