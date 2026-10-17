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
