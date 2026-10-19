# .opencode/skills

OpenCode descubre estos skills automáticamente en cada run (local o GitHub
Action). Formato estándar: una carpeta por skill con `SKILL.md` en mayúsculas
y frontmatter `name` + `description`; el `description` es lo que el agente lee
para decidir si cargar el skill.

## Skills instalados

| Skill | Cubre | Fuente | Licencia |
|---|---|---|---|
| `compose-kotlin-agent-skills/` | UI Android de diseño: Kotlin 2.x/K2, MVI estricto, Material 3, navegación type-safe, arquitectura y testing (router + 3 sub-skills + 20 referencias) | [haidrrrry/compose-kotlin-agent-skills](https://github.com/haidrrrry/compose-kotlin-agent-skills) | MIT (copia incluida) |
| `modern-jetpack-compose/` | Buenas prácticas Compose: recomposición, estabilidad (`@Stable`/`@Immutable`), side effects, navegación, accesibilidad | [anhvt52/jetpack-compose-skills](https://github.com/anhvt52/jetpack-compose-skills) | MIT (copia incluida) |
| `cpp-expert-skill/` | Buenas prácticas C++ moderno (17/20/23) alineadas a Core Guidelines: memoria/RAII, concurrencia, errores, rendimiento, API, testing | [PetrGuan/Cpp-Agent-Skill](https://github.com/PetrGuan/Cpp-Agent-Skill) | MIT (copia incluida) |
| `vulkan/` | Desarrollo y debug de Vulkan citando fuentes oficiales vendidas localmente: headers Khronos (`references/Vulkan-Headers/`) y Vulkan-Guide (`references/Vulkan-Guide/) | [rygo6/Vulkan-AGENTS](https://github.com/rygo6/Vulkan-AGENTS) | Sin licencia explícita; skill publicado por su autor para uso en agentes |
| `opengl-es/` | Motor de dibujo nativo del proyecto (GL ES 3.x + EGL en el NDK): trazos, batching, AA, undo, latencia dedo→píxel, GL_KHR_debug, RenderDoc/AGI | Original, escrito para Dibujo- (no existía un skill dedicado pulido) | MIT (proyecto) |
| `codeql/` | SAST con CodeQL: dataflow y taint tracking interprocedural (C/C++, Java/Kotlin y más), modos "run all"/"important only" y modelos de extensión para fuentes/sinks propios | [trailofbits/skills](https://github.com/trailofbits/skills) (`plugins/static-analysis/skills/codeql`) | CC BY-SA 4.0 (copia incluida) |
| `semgrep/` | SAST rápido con Semgrep: detección de lenguajes, reglas de Trail of Bits/0xdea/Decurity y salida SARIF (siempre `--metrics=off`) | [trailofbits/skills](https://github.com/trailofbits/skills) (`plugins/static-analysis/skills/semgrep`) | CC BY-SA 4.0 (copia incluida) |
| `sarif-parsing/` | Leer, filtrar, deduplicar y convertir SARIF de CodeQL/Semgrep; integración con reportes y CI | [trailofbits/skills](https://github.com/trailofbits/skills) (`plugins/static-analysis/skills/sarif-parsing`) | CC BY-SA 4.0 (copia incluida) |
| `address-sanitizer/` | Compilar/correr bajo AddressSanitizer (`-fsanitize=address`) para cazar overflows, use-after-free y fugas en el motor C++ | [trailofbits/skills](https://github.com/trailofbits/skills) (`plugins/testing-handbook-skills/skills/address-sanitizer`) | CC BY-SA 4.0 (copia incluida) |
| `supply-chain-risk-auditor/` | Auditar dependencias (advisories por versión, lockfiles, upstreams abandonados, scripts de instalación); relevante por el riesgo de la cadena de suministro | [trailofbits/skills](https://github.com/trailofbits/skills) (`plugins/supply-chain-risk-auditor`) | CC BY-SA 4.0 (copia incluida) |
| `android-security-audit/` | Auditoría de `:app` contra OWASP MASVS v2.0 (51 ítems en 8 categorías) con ejemplos Kotlin: storage, crypto, auth, red, plataforma, código, resiliencia y privacidad | [liuyi0808/android-review](https://github.com/liuyi0808/android-review) (`skills/security-audit`) | MIT (copia incluida) |

## Notas

- `Kotlin/kotlin-agent-skills` (oficial de JetBrains, Apache-2.0) se descartó:
  sus skills reales son de backend (JPA, Kotlin Toolchain/Amper, migraciones
  KMP) y no aplican a una app Android; las buenas prácticas del lenguaje
  quedan cubiertas por los dos skills de Compose.
- Las referencias pesadas de Vulkan (spec completa, validation layers, samples
  y MoltenVK) no se vendieron para no inflar el repo; `vulkan/SKILL.md`
  documenta cómo clonarlas on-demand si una tarea las necesita.
- Mantener cada `SKILL.md` liviano (~<500 líneas) y el detalle en
  `references/` (progressive disclosure) para no saturar el contexto.

### Skills de seguridad (issue 60)

- **Procedencia pineada**: el lote de Trail of Bits viene de
  `trailofbits/skills` en el commit `5c99c03fb8a9ea568432ecbcba5d86e9f4ac2bf0`
  y el de Android de `liuyi0808/android-review` en
  `26fbdc85360e0445699a6f1e5a11cfe52005cb84`. Para actualizar, re-clonar,
  re-copiar y volver a correr el escáner (abajo).
- **Verificación de seguridad**: las skills instaladas se escanean con
  [Mondoo `skillcheck`](https://github.com/mondoohq/skillcheck):
  `npx -y @mondoohq/skillcheck@0.4.0 --project .`. Resultado del alta:
  **PASS — sin hallazgos críticos ni de riesgo alto** (11 skills). Es un
  requisito antes de aceptar cualquier skill nueva (el research *ToxicSkills*
  de Snyk encontró prompt injection en ~36% de las skills testeadas).
- **Portabilidad desde Claude Code**: los skills de Trail of Bits se
  escribieron para Claude Code. En OpenCode, al cargar un skill se inyecta su
  **directorio base**, así que las rutas `references/`, `scripts/` y
  `{baseDir}/...` se resuelven contra la carpeta del skill. Los comandos slash
  tipo `/static-analysis:semgrep-scan` y el tool `Workflow` (orquestación
  multi-agente) **no existen acá**: seguí el workflow de la propia `SKILL.md`
  paso a paso y llamá los scripts a mano con `uv`/`bash`.
- **`c-review` descartado**: es el skill estrella de revisión C/C++ de Trail
  of Bits, pero su `SKILL.md` depende de un único tool `Workflow` de Claude
  Code (más `workflows/*.js`). En OpenCode no hay ese tool y el skill queda
  inservible; para el motor nativo quedan `semgrep` + `codeql` + `sarif-parsing`
  + `address-sanitizer`.
- **`modern-cpp` descartado**: se solapa con `cpp-expert-skill` (mismo foco,
  C++ moderno y seguridad); no se agrega ruido.
- **`OWASP/secure-agent-playbook`: no es lo que se creía.** Se recomendó como
  auditoría móvil (MASVS), pero el repo es sobre **seguridad de agentes de IA**
  (AISVS, LLM Top 10, threat modeling multi-agente), no sobre apps Android. No
  se instaló; el lado `:app` lo cubre `android-security-audit` (MASVS real).
  Si más adelante querés endurecer *el agente* que corre en GitHub Actions
  (prompt injection vía issues), ese playbook sí aplica y se puede sumar aparte.
- **Licencias**: los skills de Trail of Bits son **CC BY-SA 4.0** (copias con
  su `LICENSE` dentro de cada carpeta); `android-security-audit` es **MIT**.
  Al modificar el contenido hay que mantener la misma licencia (share-alike).
