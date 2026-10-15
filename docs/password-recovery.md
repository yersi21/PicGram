# Recuperacion de contraseña

La app incluye “¿Olvidaste tu contraseña?” en Login y permite solicitar un correo,
abrir el enlace recibido y definir una contraseña nueva con confirmacion.
Mantiene el estilo Liquid Glass y el logotipo. No hace falta cambiar tablas ni RLS.

## Configuracion necesaria en Supabase

1. En **Authentication > URL Configuration > Redirect URLs**, agregar exactamente:

   `com.example.picgram://auth/reset-password`

   Conservar las URLs existentes; no reemplazar Site URL por esta ruta.

2. En la plantilla **Reset Password**, mantener un enlace que use
   `{{ .ConfirmationURL }}`. La app solicita `/auth/v1/recover` indicando
   `redirect_to` con la URL anterior. Supabase valida el enlace y devuelve la
   sesion de recuperacion a la app con `type=recovery`.

3. Verificar el servicio de envio de correo/SMTP del proyecto. Para enviar a
   usuarios reales configurar SMTP propio y comprobar los limites de envio del
   proveedor y Supabase. La app muestra un mensaje generico de envio para no
   revelar si existe una cuenta con el correo indicado.

Estas opciones del dashboard no se modificaron desde el proyecto y no se enviaron
correos de prueba a usuarios durante esta implementacion.

Referencia: [redirecciones](https://supabase.com/docs/guides/auth/redirect-urls),
[recuperacion por correo](https://supabase.com/docs/guides/auth/passwords),
[SMTP](https://supabase.com/docs/guides/auth/auth-smtp).

## Plantilla alternativa con token hash

Si se personaliza el correo, la app tambien acepta este enlace directo:

```html
<a href="com.example.picgram://auth/reset-password?token_hash={{ .TokenHash }}&amp;type=recovery">Restablecer contraseña</a>
```

En ese caso valida el hash mediante `POST /auth/v1/verify` antes de mostrar el
formulario. Este cliente REST no utiliza PKCE ni admite enlaces `?code=...` que
requieran un code_verifier generado por otro cliente.

## Manejo de la sesion

La recuperacion usa sus propias credenciales en memoria y no reemplaza la sesion
normal de otra cuenta. El cambio se realiza con `PUT /auth/v1/user` y el JWT
verificado del correo. Solo un rechazo HTTP 401 puede provocar una renovacion,
utilizando el refresh token de recuperacion y comprobando que el usuario sea el
mismo. Nunca usa el token guardado de otra cuenta ni reintenta automaticamente
una escritura cuya respuesta se perdio por un fallo de red.

Al finalizar cierra la sesion temporal de recuperacion con `scope=local`. Si se
recupero la cuenta que estaba abierta, borra esa sesion local para volver a entrar
con la contraseña nueva. El boton “Iniciar sesion” lleva al login y termina la
sesion local actual. Las contraseñas no se guardan en preferencias ni bundles.
La verificacion sobrevive a la rotacion mediante ViewModel. Si se termina el
proceso despues de consumir un token hash, puede ser necesario otro enlace.

## Prueba completa despues de configurar el servidor

1. Solicitar un enlace para una cuenta de prueba desde el login.
2. Abrir el correo en el telefono con PicGram instalado, con app cerrada y abierta.
3. Verificar que aparece el correo correcto en “Nueva contraseña”.
4. Confirmar errores con un enlace vencido/usado, contraseñas distintas y contraseña corta.
5. Cambiar la contraseña, volver al login y entrar con la nueva.
6. Verificar que la contraseña anterior ya no permite iniciar sesion.
7. Rotar el telefono durante la solicitud/verificacion y comprobar que no duplica solicitudes.
8. Con una cuenta A abierta, recuperar B: el formulario debe actualizar B y no A.

Las pruebas unitarias cubren rutas, tipos, parametros repetidos, errores de enlaces,
validacion de contraseñas y aislamiento/renovacion de la sesion de recuperacion.
El envio de correo real y el cambio con una cuenta del servidor requieren la prueba
anterior; no quedan confirmados por la compilacion ni por las pruebas unitarias.
