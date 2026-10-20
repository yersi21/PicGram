# RF-06: buscar usuarios

## Despliegue pendiente

Ejecutar `supabase/migrations/003_user_names_search.sql` (si aún está pendiente)
y después `supabase/migrations/004_registration_display_name.sql` en el SQL Editor
de Supabase antes de instalar esta versión. No se han ejecutado desde el proyecto.
004 actualiza el trigger existente para guardar el nombre recibido en los metadatos
de registro, incluso cuando Auth exige confirmar el correo y no devuelve sesión.
No es necesario volver a ejecutar 001 ni cambiar identificadores o eliminar datos.
La migración usa las políticas RLS ya verificadas: lectura autenticada y edición
solo del perfil propio. La función de búsqueda respeta esas políticas.

## Uso

El registro solicita **Nombre** (2–80 caracteres), **Nombre de usuario**, correo
y contraseña. El nombre se valida antes de crear la cuenta y se guarda tanto en
los metadatos de Auth como en el perfil público. El perfil muestra el nombre y
el @usuario, sin botón ni diálogo de edición del nombre.
Se permiten nombres personales repetidos. Las cuentas existentes conservan sus
datos; si no tenían nombre, siguen siendo localizables por su @usuario. No se
inventan nombres a partir del correo ni se eliminan cuentas para completar datos.

Buscar desde la lupa del inicio por una parte del nombre o del nombre de usuario,
con o sin `@`. No distingue mayúsculas de minúsculas; sí distingue tildes.
La búsqueda espera 400 ms al escribir, cancela consultas anteriores y muestra
errores sin presentarlos como ausencia de cuentas. `%`, `_`, comillas y paréntesis
son caracteres literales, no filtros. Se muestran hasta 50 cuentas ordenadas por
usuario; para acotar resultados, escribir más caracteres.

Cada resultado muestra foto, nombre y @usuario. Al pulsarlo se abre el perfil
por su UUID, no por el nombre, y sus publicaciones se consultan por `user_id`
ordenadas por fecha descendente. Las consultas no incluyen correos electrónicos.

## Prueba de aceptación con dos cuentas

1. Registrar A con nombre `Ana López` y nombre de usuario `ana_test`.
   Probar tanto con confirmación por correo habilitada como con sesión inmediata.
2. En B, buscar `Ana`, `ana`, `López`, una parte del @usuario y `@usuario`.
   Debe aparecer A con su nombre y @usuario correctos.
3. Pulsar A y comprobar que se ven su foto, contadores y únicamente sus publicaciones.
4. Abrir una publicación y volver; volver a la búsqueda y repetir con otra cuenta.
5. Escribir consultas distintas rápidamente en B o limpiar el campo. La consulta
   anterior no debe sobrescribir resultados nuevos. Comprobar que no existe
   la opción de editar el nombre en el perfil.
6. Probar un texto sin coincidencias, `@` solo, errores de red y recuperación.
7. Verificar con sesiones autenticadas distintas que B no puede modificar el
   `display_name` de A mediante la API (RLS); la búsqueda anónima no está permitida.

Las pruebas unitarias locales no sustituyen esta comprobación con Supabase real.

Verificación local de esta implementación: `assembleDebug` correcto y 56 pruebas
unitarias aprobadas (incluyen validación de nombres, normalización, debounce,
respuesta antigua descartada, recuperación de errores y perfil seleccionado por ID).
