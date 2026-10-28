# Language Service · Especificación

> Snippet Searcher · Paso 1 (microservicios + HTTP naive)
> Diagrama: `../01-microservicios-http.excalidraw`

## 1. Contexto

```
UI ──HTTP──▶ Snippet Service ──HTTP──▶ Language Service   ◀── este documento
                     │
                     └──HTTP──▶ Permission Service
```

Snippet Searcher es una plataforma web para guardar, compartir, validar, formatear, lintear, ejecutar y testear snippets de código. Hoy solo soporta **PrintScript**, pero debe poder sumar otros lenguajes sin tocar el resto del sistema.

**Language Service** concentra todo lo que depende del lenguaje. Los demás servicios no conocen PrintScript: solo le hablan a este servicio con el id del lenguaje.

## 2. Responsabilidad

**Hace**
- Exponer qué lenguajes y versiones soporta.
- Validar sintaxis y semántica de un código fuente → errores con regla, línea y columna.
- Formatear un código fuente con un set de reglas.
- Lintear un código fuente con un set de reglas.
- Ejecutar un código fuente con una lista de inputs y devolver los outputs.
- Correr un test: ejecutar con inputs y verificar (assert) los outputs contra los esperados.

**No hace**
- No guarda nada: **sin base de datos, sin estado entre requests**.
- No conoce usuarios, snippets ni permisos. No valida identidad: lo llama solo Snippet Service.
- No decide qué reglas usar: las recibe en cada request.
- No guarda tests: los recibe de a uno en cada request (los guarda Snippet Service).

## 3. User stories que soporta (indirectamente, vía Snippet Service)

| US | Uso |
|---|---|
| 1–4 crear/actualizar snippet | `validate` |
| 5, 14, 15 validez según linteo | `lint` |
| 11, 12, 13 formateo / descarga formateada | `format` |
| 9, 16 correr tests | `test` |
| 10 ejecución (base para la versión interactiva) | `execute` |

## 4. Diseño interno: interfaz `Language`

El servicio es multi-lenguaje. Cada lenguaje es una implementación de esta interfaz, y un registro resuelve cuál usar según el `{lang}` del path.

```kotlin
interface Language {
    /** id usado en la URL, en minúsculas. Ej: "printscript" */
    val id: String

    /** Versiones soportadas. Ej: ["1.0", "1.1"] */
    fun versions(): List<String>

    /** Lista vacía = código válido. */
    fun validate(src: String, version: String): List<SourceError>

    /** rules = JSON de configuración propio del lenguaje (opaco para el resto del sistema). */
    fun format(src: String, version: String, rules: String): String

    fun lint(src: String, version: String, rules: String): List<SourceError>

    fun execute(src: String, version: String, inputs: List<String>): ExecutionResult

    /** Assert: ejecuta y compara outputs. Implementación por defecto sobre execute;
     *  un lenguaje la puede sobreescribir si necesita otra semántica de comparación. */
    fun test(src: String, version: String, inputs: List<String>, expectedOutputs: List<String>): TestResult {
        val result = execute(src, version, inputs)
        val passed = result.error == null && result.outputs == expectedOutputs
        return TestResult(passed, expectedOutputs, result.outputs, result.error)
    }
}

data class SourceError(val rule: String, val message: String, val line: Int, val column: Int)

data class ExecutionResult(val outputs: List<String>, val error: SourceError?)

data class TestResult(
    val passed: Boolean,
    val expectedOutputs: List<String>,
    val actualOutputs: List<String>,
    val error: SourceError?,
)

class LanguageRegistry(languages: List<Language>) {
    fun get(id: String): Language?       // null → 404 LANGUAGE_NOT_FOUND
    fun all(): List<Language>
}
```

Para agregar un lenguaje nuevo: implementar `Language` y registrarlo (p. ej. como bean de Spring). Los controllers no cambian.

### 4.1 Implementación PrintScript (`PrintScriptLanguage`)

Adapter sobre `LanguageFacade` del proyecto `PrintScript/` (módulo `:printscript`, contrato en `PrintScript/language/src/main/kotlin/cnc/language/Language.kt`).

| `Language` | `LanguageFacade` |
|---|---|
| `versions()` | versiones de `PrintScriptVersion` (1.0, 1.1) |
| `validate` | `validate(code)` → mapear `Failure` a `SourceError` |
| `format` | `format(code, configJson = rules)` |
| `lint` | `lint(code, configJson = rules)` → mapear cada `String` a `SourceError` |
| `execute` | `execute(code, InterpreterIO(input = …, output = …, envProvider = …))` |
| `test` | no hace falta implementarlo: usa la implementación por defecto de `Language` sobre `execute` |

Para `execute`, armar el `InterpreterIO` así:
- `input`: devuelve los `inputs` **en orden**. Si se piden más inputs que los provistos → cortar la ejecución con error `INPUTS_EXHAUSTED`.
- `output`: agrega cada `println` a la lista `outputs`, en orden.
- `envProvider`: devuelve `null` siempre. Nunca exponer las variables de entorno del servidor.

**A verificar antes de implementar:** `Failure` hoy tiene `msg` y `type`. La US 1/2 exige **regla + línea + columna**. Si `Failure` no trae la posición, hay que extender el resultado en PrintScript (o parsearla del mensaje como solución temporal). Lo mismo para la salida de `lint`, que hoy es `List<String>`.

## 5. API expuesta

Convenciones:
- JSON UTF-8. Puerto por defecto **8082**.
- `{lang}` = id del lenguaje (`printscript`). `version` = versión del lenguaje (`"1.1"`).
- Errores con el formato común:
  ```json
  { "error": "CODE", "message": "texto legible", "details": [] }
  ```

| Código HTTP | `error` | Cuándo |
|---|---|---|
| 400 | `BAD_REQUEST` | body mal formado / faltan campos |
| 400 | `UNSUPPORTED_VERSION` | `version` no está en `versions()` |
| 400 | `INVALID_RULES` | el JSON de `rules` no es válido para el lenguaje |
| 404 | `LANGUAGE_NOT_FOUND` | `{lang}` no registrado |
| 422 | `INVALID_SOURCE` | `format`/`lint` sobre código que no parsea (`details` = `SourceError[]`) |

---

### `GET /languages`
Lista lenguajes y versiones.

**200**
```json
[ { "id": "printscript", "versions": ["1.0", "1.1"] } ]
```

---

### `POST /languages/{lang}/validate`
**Request**
```json
{ "content": "let a: number = 5;\nprintln(a);", "version": "1.1" }
```
**200** — el resultado de validar siempre es 200, sea válido o no:
```json
{ "valid": false,
  "errors": [ { "rule": "UNEXPECTED_TOKEN", "message": "Expected ';'", "line": 1, "column": 17 } ] }
```
```json
{ "valid": true, "errors": [] }
```

---

### `POST /languages/{lang}/format`
**Request**
```json
{ "content": "let a:number=5;", "version": "1.1", "rules": { "spaceBeforeColon": true } }
```
`rules` es el JSON de configuración del formatter del lenguaje (para PrintScript, el mismo `configJson` que recibe `LanguageFacade.format`). Puede omitirse → defaults del lenguaje.

**200**
```json
{ "formatted": "let a : number = 5;\n" }
```
**422** `INVALID_SOURCE` si el código no es válido.

---

### `POST /languages/{lang}/lint`
**Request**
```json
{ "content": "let my_var: number = 5;", "version": "1.1", "rules": { "identifierFormat": "camel case" } }
```
**200**
```json
{ "violations": [ { "rule": "identifierFormat", "message": "my_var is not camel case", "line": 1, "column": 5 } ] }
```
Lista vacía = cumple las reglas. **422** `INVALID_SOURCE` si el código no es válido.

---

### `POST /languages/{lang}/execute`
**Request**
```json
{ "content": "let n: string = readInput(\"name\");\nprintln(n);", "version": "1.1", "inputs": ["Ana"] }
```
**200** — la ejecución siempre devuelve 200 y el error de runtime viene en el body:
```json
{ "outputs": ["Ana"], "error": null }
```
```json
{ "outputs": [], "error": { "rule": "INPUTS_EXHAUSTED", "message": "Input requested but none left", "line": 1, "column": 17 } }
```
Códigos de `error.rule` reservados por el servicio: `INPUTS_EXHAUSTED`, `TIMEOUT`, `RUNTIME_ERROR`. Para un error de parseo se usa la regla que reporte el lenguaje.

**Límite:** cortar la ejecución a los **5 s** (configurable) → `TIMEOUT`. Hace falta porque el código lo escriben los usuarios y puede tener loops.

---

### `POST /languages/{lang}/test`
Corre **un** test: ejecuta con `inputs` y compara contra `expectedOutputs`.

**Request**
```json
{ "content": "let n: string = readInput(\"name\");\nprintln(\"Hola \" + n);", "version": "1.1",
  "inputs": ["Ana"], "expectedOutputs": ["Hola Ana"] }
```
**200** — sea que pase o no:
```json
{ "passed": false, "expectedOutputs": ["Hola Ana"], "actualOutputs": ["Hola  Ana"], "error": null }
```
Semántica del assert (implementación por defecto):
- Pasa si **no hubo error** y `actualOutputs` es **igual, elemento a elemento y en orden**, a `expectedOutputs` (misma cantidad).
- Si hubo error (`TIMEOUT`, `INPUTS_EXHAUSTED`, runtime o parseo) → `passed: false`, con `error` completo y los outputs que se llegaron a imprimir.
- Mismo límite de 5 s que `execute`.

## 6. Servicios que consume

Ninguno.

## 7. Quién lo consume

Solo **Snippet Service**, con estas llamadas:

| Flujo de Snippet Service | Endpoint |
|---|---|
| crear / actualizar snippet | `validate` y después `lint` |
| cambiar reglas de linteo | `lint` por cada snippet |
| cambiar reglas de formato / descargar formateado | `format` |
| correr test (de a uno; también los automáticos de la US 16) | `test` |
| selector de lenguaje en la UI | `GET /languages` |

## 8. Requisitos no funcionales

- **Stack:** Spring Boot + **Kotlin** (recomendado, porque PrintScript es Kotlin), JVM 21. Módulo nuevo: no existe todavía en el workspace.
- **Dependencia de PrintScript:** por ahora alcanza con un composite build (`includeBuild("../PrintScript")`) o `mavenLocal`. La publicación como artefacto se define más adelante.
- **Tests:** unitarios del adapter PrintScript (puede reutilizar casos del `TCK/`), del registry y de los controllers. **Coverage ≥ 80 %** (JaCoCo).
- **CI:** build + tests + coverage en cada PR.
- Docker y deploy se especifican en el próximo paso.

## 9. Criterios de aceptación

- [ ] `GET /languages` devuelve `printscript` con `1.0` y `1.1`.
- [ ] `validate` de código inválido devuelve `valid: false` con `rule`, `line` y `column` correctos.
- [ ] `format` y `lint` respetan las reglas recibidas, y si `rules` se omite aplican los defaults.
- [ ] `execute` consume los inputs en orden, devuelve los outputs en orden de `println` y corta a los 5 s.
- [ ] `test` devuelve `passed: true` solo si no hubo error y los outputs coinciden exactamente y en orden.
- [ ] Un `{lang}` desconocido devuelve 404 `LANGUAGE_NOT_FOUND`. Una versión desconocida devuelve 400 `UNSUPPORTED_VERSION`.
- [ ] Agregar un lenguaje "fake" en un test solo requiere una implementación de `Language` nueva.

## 10. Fuera de alcance de este paso

- Ejecución interactiva / streaming de outputs (US 9 y 10, "se ven más adelante"). Hoy `execute` es request → response.
- Procesamiento asíncrono masivo (US 12, 15).
- Autenticación.
