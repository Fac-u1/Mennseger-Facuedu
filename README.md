# Facuedu.uy Mennseger

Aplicación móvil Android construida con **Kotlin** y **Jetpack Compose** para mensajería cifrada de extremo a extremo, videollamadas en vivo, intercambio seguro de documentos y asistente multimodal integrado (**FacuIA**).

## Características principales

- **Autenticación con Firebase y Google Sign-In:** Inicio de sesión mediante Jetpack Credential Manager, directorio de contactos por UID y reglas de seguridad Zero-Trust en Cloud Firestore.
- **Mensajería cifrada E2EE:** Intercambio de claves **ECDH P-256** y cifrado simétrico **AES-256-GCM** antes de almacenar mensajes o adjuntos en la nube, con inspector criptográfico y verificación de *Safety Number*.
- **Documentos cifrados:** Selección segura de archivos e imágenes mediante SAF y Photo Picker, validación **SHA-256**, apertura local vía `FileProvider` y resumen inteligente con IA.
- **Videollamadas en vivo:** Vista de cámara en tiempo real con **CameraX**, controles de micrófono, cámara, cambio de lente, altavoz e historial de llamadas en Firestore.
- **Asistente FacuIA (Firebase AI Logic):**
  - Chat contextual en streaming (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`).
  - Cambiador de frases con distintos tonos desde el chat o el estudio.
  - Generación y edición de fotos (`gemini-nano-banana-2.1`).
  - Animación de imágenes a video (`gemini-omni-1.2-flash`).
  - Composición de música (`lyria-3-clip-preview`, `lyria-3-pro-preview`).
  - Dictado y transcripción de audio (`gemini-3.5-transcribe`).

## Requisitos

- **Android Studio / Gradle:** JDK 11 o superior y Android SDK (compileSdk 36, minSdk 24).
- **Node.js 20+ y Firebase CLI:** Para ejecutar las pruebas de reglas de Firestore y los emuladores locales.

## Estructura del proyecto

- `app/` — Módulo Android principal (Kotlin, Jetpack Compose, Material 3, CameraX, Crypto, Firebase).
- `desktop-client/` — Cliente de escritorio multiplataforma (Electron + WebCrypto E2EE) para compilar `.exe` (Windows), `.dmg`/`.pkg` (macOS) y `.AppImage`/`.deb` (Linux/Ubuntu).
- `iosApp/` — Proyecto SwiftUI + CryptoKit (`project.yml` para XcodeGen) para compilar `.ipa` en macOS.
- `.github/workflows/build.yml` — Pipeline de GitHub Actions para compilar los 5 sistemas operativos y publicar Releases.
- `firestore.rules` y `firestore.indexes.json` — Reglas e índices de Cloud Firestore.
- `firestore.test.js` — Pruebas automatizadas de reglas de seguridad con `@firebase/rules-unit-testing`.
- `firebase.json` y `firebase-blueprint.json` — Configuración de emuladores y esquema de datos.
- `package.json` — Scripts y dependencias de desarrollo para pruebas de reglas y Firebase CLI.

## Comandos útiles

### Android (Gradle)

```bash
# Compilar APK de desarrollo
gradle :app:assembleDebug

# Ejecutar pruebas unitarias y Robolectric
gradle :app:testDebugUnitTest
```

### Firebase & Pruebas de reglas (Node.js)

```bash
# Instalar dependencias de pruebas de reglas (si aún no están instaladas)
npm install

# Ejecutar emuladores de Auth y Firestore
npm run emulators

# Ejecutar pruebas de reglas contra un emulador activo
npm run test:rules:local

# Ejecutar pruebas levantando emuladores automáticamente
npm test
```
