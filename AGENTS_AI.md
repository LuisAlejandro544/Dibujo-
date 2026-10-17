# AGENTS_AI — Los agentes IA del repo Dibujo

Segundo documento de agentes: acá se describe **quién es quién** entre las
IAs que trabajan/observan este repo y cómo se usan. El rol y las reglas de
trabajo del agente de código están en `AGENTS.md`; el mapa técnico está en
`AI_CONTEXT.md`.

## 1. De IA en el repo

| Agente | Dónde vive | Qué hace | Cómo se activa |
|---|---|---|---|
| **opencode (agente freelance)** | CLI local + `.opencode/`, `opencode.json` | Es el desarrollador del repo: implementas issues, sigue las reglas de `AGENTS.md`. Modelo: `opencode/glm-5.3-flash-free`. | El usuario abre issues y le da órdenes directas (`/oc ...`). |
| **CodeRabbit** | App de GitHub (comments en el PR) | Sumarios y review de PRs. En repos <10 estrellas no revisa automático: se le pide con el checkbox "Trigger review" del sumario. | Comment en el PR: `@coderabbitai review` o el checkbox del panel. |
| **Workflows de CI** | `.github/workflows/` | Compilan, testean y generan artefactos. No son "IA" pero automatizan la verificación que pide `AGENTS.md`. | Manual: pestaña **Actions → Run workflow**. Ver tabla abajo. |
| **Autofix / panel de PR** | Comentarios de PR (`autopilot:start`) | Checklist que opencode usa para saber si debe seguir iterando (fix CodeRabbit, CI verde, resolver conflictos). | Lo maneja la infra de opencode al procesar el PR. |

## 2. Workflows disponibles

| Workflow | Archivo | Trigger | Artefacto |
|---|---|---|---|
| APK Android (assembleDebug) | `android-apk.yml` | `workflow_dispatch` (manual) | APK debug firmado de la app Android real (`:app`, con motor C++ via NDK). |
| APK Debug (manual, legacy) | `build-debug-apk.yml` | `workflow_dispatch` (manual) | APK empaquetado a mano (aapt2/d8/apksigner) con las clases DEX de `:core/:engine/:ui` + consola Linux con el motor C++. |

Ambos son **100% manuales**: ninguno corre en push ni PR. Regla de
`opencode.json`: si el entorno no permite push de workflows
(GH_TOKEN sin PAT), mover el archivo por el PR y avisar en la descripción que
requiere merge para activarse.

## 3. Cómo trabaja el agente de código (resumen operativo)

1. Lee el issue + `git status` antes de tocar.
2. Cambio mínimo que resuelve; cualquier otra cosa se avisa, no se arregla.
3. Verificá siempre: `./gradlew build` (todo) y `:app:assembleDebug` si tocaste Android.
4. Documentá en el PR: qué, cómo se verificó y qué queda pendiente.
5. Actualizá `AI_CONTEXT.md` / `ROADMAP.md` cuando agregues módulos, closes tareas del roadmap.

Reglas completas: `AGENTS.md` (reglas duras 1–10 y sección Prohibido).

## 4. Convención de ramas

- Issues → rama `opencode/issue<N>-<fecha>` creada por la infraestructura.
- Base: `main`. PR en español, cuerpo con qué/cómo/pendiente.
- El merge los hace el usuario (o el autofix CI si el dueño pide mashup verde).
