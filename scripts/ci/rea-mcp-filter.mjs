#!/usr/bin/env node
/**
 * rea-mcp-filter.mjs — Proxy MCP stdio que filtra las herramientas de REA.
 *
 * Por qué existe:
 *   REA anuncia 139 tools y la API del modelo rechaza >128:
 *   "Invalid value for 'tools': maximum number of items is 128".
 *   `permission: deny` / `tools: false` NO sirven: opencode igual las envía
 *   en la petición. Hay que filtrarlas en el tools/list del servidor.
 *
 * Qué hace:
 *   - Arranca `npx -y rea-agents@6.7.0 mcp` como hijo.
 *   - Reenvía todo el JSON-RPC por stdio (línea a línea) intacto,
 *     EXCEPTO la respuesta a `tools/list`, donde conserva solo las
 *     herramientas Android (jadx/apk/dex/smali).
 *
 * Uso en opencode.json:
 *   "rea": {
 *     "type": "local",
 *     "command": ["node", "{env:REA_FILTER_SCRIPT}"],
 *     "enabled": true,
 *     "timeout": 120000
 *   }
 *
 * Self-test (cuenta tools sin opencode):
 *   node scripts/ci/rea-mcp-filter.mjs --self-test
 */

import { spawn } from "node:child_process"

const REA_ARGS = ["-y", "rea-agents@6.7.0", "mcp"]
// Solo el subconjunto Android, que es lo que el runner usa (JADX/APK).
const KEEP = /android|apk|jadx|dex|smali/i

/** Filtra el array tools de un mensaje tools/list (muta y devuelve el msg). */
function filterToolsMessage(msg) {
  if (msg && msg.result && Array.isArray(msg.result.tools)) {
    const all = msg.result.tools
    const kept = all.filter((t) => KEEP.test(t.name || ""))
    msg.result.tools = kept
    console.error(`[rea-filter] tools/list filtrado: ${all.length} -> ${kept.length} (solo Android)`)
  }
  return msg
}

/** Lee un stream en modo línea (MCP stdio = JSON-RPC delimitado por \n). */
function onLine(stream, handler) {
  let buf = ""
  stream.on("data", (d) => {
    buf += d.toString("utf8")
    let idx
    while ((idx = buf.indexOf("\n")) !== -1) {
      const line = buf.slice(0, idx)
      buf = buf.slice(idx + 1)
      if (line.trim()) handler(line)
    }
  })
}

/** Modo proxy: opencode <-> este proceso <-> REA. */
function proxy() {
  const child = spawn("npx", REA_ARGS, { stdio: ["pipe", "pipe", "inherit"] })
  child.stdin.on("error", () => {}) // EPIPE si el hijo muere antes

  // Cliente (opencode) -> REA: reenviar tal cual.
  onLine(process.stdin, (line) => child.stdin.write(line + "\n"))

  // REA -> cliente: reenviar, filtrando solo la respuesta de tools/list.
  onLine(child.stdout, (line) => {
    let out = line
    try {
      out = JSON.stringify(filterToolsMessage(JSON.parse(line)))
    } catch {
      // No es JSON parseable: pasar la línea intacta (defensivo).
    }
    process.stdout.write(out + "\n")
  })

  child.on("exit", (code) => process.exit(code ?? 0))
  process.on("SIGTERM", () => child.kill("SIGTERM"))
  process.on("SIGINT", () => child.kill("SIGINT"))
}

/** Modo self-test: handshake MCP + tools/list e informe del conteo. */
async function selfTest() {
  const child = spawn("npx", REA_ARGS, { stdio: ["pipe", "pipe", "inherit"] })
  const waiters = new Map()
  onLine(child.stdout, (line) => {
    let msg
    try { msg = JSON.parse(line) } catch { return }
    if (msg.id !== undefined && waiters.has(msg.id)) {
      waiters.get(msg.id)(msg)
      waiters.delete(msg.id)
    }
  })
  const send = (obj) => child.stdin.write(JSON.stringify(obj) + "\n")
  const request = (id, method, params) =>
    new Promise((resolve, reject) => {
      waiters.set(id, resolve)
      send({ jsonrpc: "2.0", id, method, params })
      setTimeout(() => reject(new Error(`timeout esperando ${method}`)), 90000)
    })

  await request(1, "initialize", {
    protocolVersion: "2024-11-05",
    capabilities: {},
    clientInfo: { name: "rea-filter-selftest", version: "1.0.0" },
  })
  send({ jsonrpc: "2.0", method: "notifications/initialized" })
  const list = await request(2, "tools/list", {})
  const all = list.result?.tools ?? []
  const kept = all.filter((t) => KEEP.test(t.name || ""))
  console.log(JSON.stringify({
    total_anunciadas: all.length,
    total_tras_filtro: kept.length,
    herramientas_visibles: kept.map((t) => t.name),
  }, null, 2))
  child.kill("SIGTERM")
  process.exit(0)
}

if (process.argv.includes("--self-test")) {
  selfTest().catch((err) => { console.error("self-test falló:", err.message); process.exit(1) })
} else {
  proxy()
                  }
