import https from "node:https";
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const serverDir = path.dirname(fileURLToPath(import.meta.url));
const config = JSON.parse(fs.readFileSync(path.join(serverDir, "server-config.json"), "utf8").replace(/^\uFEFF/, ""));
const providerToken = execFileSync(
  "powershell",
  ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", path.join(serverDir, "read-secret.ps1")],
  { encoding: "utf8", windowsHide: true },
).trim();
const pfx = fs.readFileSync(path.join(serverDir, "server.p12"));
const model = config.model === "auto" ? "z-ai/glm-5.3" : config.model;
const visionModel = "z-ai/glm-4.6v";
const rateLimits = new Map();

function json(response, status, body) {
  const payload = Buffer.from(JSON.stringify(body), "utf8");
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": payload.length,
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  response.end(payload);
}

function authorized(request) {
  const supplied = request.headers["x-topjournal-key"];
  if (typeof supplied !== "string" || supplied.length !== config.clientKey.length) return false;
  return cryptoSafeEqual(supplied, config.clientKey);
}

function cryptoSafeEqual(left, right) {
  let mismatch = 0;
  for (let index = 0; index < left.length; index++) mismatch |= left.charCodeAt(index) ^ right.charCodeAt(index);
  return mismatch === 0;
}

async function readBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 12 * 1024 * 1024) throw new Error("payload_too_large");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function canRun(address) {
  const now = Date.now();
  const previous = rateLimits.get(address) || 0;
  if (now - previous < 2_500) return false;
  rateLimits.set(address, now);
  return true;
}

function compactForModel(value, depth = 0) {
  if (depth > 8) return "[вложенные данные сокращены]";
  if (Array.isArray(value)) return value.slice(0, 40).map((item) => compactForModel(item, depth + 1));
  if (value && typeof value === "object") {
    const result = {};
    for (const [key, item] of Object.entries(value).slice(0, 60)) {
      if (key.startsWith("_") || /token|authorization|password|avatar|email|phone/i.test(key)) continue;
      result[key] = compactForModel(item, depth + 1);
    }
    return result;
  }
  if (typeof value === "string" && value.length > 1200) return `${value.slice(0, 1200)}…`;
  return value;
}

function buildPrompt(snapshot) {
  return `Проанализируй учебные данные студента как спокойный персональный аналитик. Ответь на русском языке.

Обязательно:
1. Начни с краткого вывода в 2–3 предложениях.
2. Дай 3–5 приоритетов на ближайшие семь дней.
3. Проанализируй домашние задания и риск просрочки.
4. Проанализируй оценки, посещаемость и динамику по предметам.
5. Сравни позицию студента с группой/потоком только по реально переданным данным. Не выдумывай конкурентов и показатели.
6. Учти расписание, экзамены, награды и активность, если они присутствуют.
7. Заверши конкретным планом действий. Не предлагай обход правил, накрутку баллов или нечестную конкуренцию.

Используй короткие заголовки и маркированные пункты. Если данных не хватает, прямо укажи это.

Данные:
${JSON.stringify(compactForModel(snapshot)).slice(0, 72_000)}`;
}

async function callChat(modelName, messages, maxTokens, controller, attempt = 0) {
    let response;
    try {
      response = await fetch("https://beta.rurouter.ai/v1/chat/completions", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${providerToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: modelName,
          messages,
          temperature: 0.2,
          max_tokens: maxTokens,
        }),
        signal: controller.signal,
      });
    } catch (error) {
      if (attempt === 0 && error?.name !== "AbortError") {
        await new Promise((resolve) => setTimeout(resolve, 900));
        return callChat(modelName, messages, maxTokens, controller, attempt + 1);
      }
      error.networkCode = error?.cause?.code || "";
      throw error;
    }
    const raw = await response.text();
    let payload;
    try { payload = JSON.parse(raw); } catch { payload = {}; }
    if (!response.ok) {
      if (attempt === 0 && (response.status === 429 || response.status >= 500)) {
        await new Promise((resolve) => setTimeout(resolve, 900));
        return callChat(modelName, messages, maxTokens, controller, attempt + 1);
      }
      const error = new Error(`provider_${response.status}`);
      error.providerStatus = response.status;
      error.providerCode = payload?.error?.code || payload?.error?.type || "";
      throw error;
    }
    const choice = payload?.choices?.[0];
    const rawContent = choice?.message?.content;
    const content = typeof rawContent === "string" ? rawContent
      : Array.isArray(rawContent) ? rawContent.map((part) => typeof part === "string" ? part : part?.text || "").join("\n")
      : "";
    if (!content.trim()) {
      if (attempt === 0) {
        await new Promise((resolve) => setTimeout(resolve, 500));
        return callChat(modelName, messages, maxTokens, controller, attempt + 1);
      }
      const error = new Error("empty_provider_response");
      error.finishReason = choice?.finish_reason || "unknown";
      error.reasoningLength = String(choice?.message?.reasoning_content || "").length;
      throw error;
    }
    return content.trim();
}

async function callProvider(snapshot, controller) {
  return callChat(model, [
    { role: "system", content: "Ты аналитик учебного прогресса. Используй только переданные факты, береги приватность студента и сразу выдай итоговый ответ без черновика." },
    { role: "user", content: buildPrompt(snapshot) },
  ], 4800, controller);
}

async function analyze(snapshot) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 130_000);
  try {
    return await callProvider(snapshot, controller);
  } finally {
    clearTimeout(timeout);
  }
}

function cleanStructuredResult(raw) {
  const text = String(raw || "").trim();
  const candidate = text.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  try {
    const parsed = JSON.parse(candidate);
    return {
      headline: String(parsed.headline || "Разбор готов").slice(0, 100),
      summary: String(parsed.summary || "").slice(0, 500),
      actions: Array.isArray(parsed.actions) ? parsed.actions.slice(0, 6).map((x) => String(x).slice(0, 220)) : [],
      warnings: Array.isArray(parsed.warnings) ? parsed.warnings.slice(0, 4).map((x) => String(x).slice(0, 220)) : [],
      files: Array.isArray(parsed.files) ? parsed.files.slice(0, 12).map((x) => ({
        name: String(x?.name || "Файл").slice(0, 120), note: String(x?.note || "").slice(0, 320),
      })) : [],
    };
  } catch {
    return { headline: "Разбор готов", summary: text.slice(0, 500), actions: [], warnings: [], files: [] };
  }
}

async function inspectImages(files, controller) {
  const notes = [];
  let imageCount = 0;
  for (const file of files.slice(0, 10)) {
    const images = Array.isArray(file?.images) ? file.images : [];
    if (!images.length || imageCount >= 8) continue;
    const content = [{ type: "text", text: `Извлеки из изображений файла «${String(file.name || "Файл").slice(0, 100)}» только сведения, полезные для выполнения домашнего задания: текст, формулы, требования и данные. Максимум 700 символов, без вступления.` }];
    for (const encoded of images.slice(0, Math.min(4, 8 - imageCount))) {
      if (typeof encoded !== "string" || encoded.length > 1_800_000) continue;
      content.push({ type: "image_url", image_url: { url: `data:image/jpeg;base64,${encoded}` } });
      imageCount++;
    }
    if (content.length > 1) {
      try {
        const note = await callChat(visionModel, [{ role: "user", content }], 900, controller);
        notes.push({ name: String(file.name || "Файл"), note: note.slice(0, 1200) });
      } catch (error) {
        notes.push({ name: String(file.name || "Файл"), note: "Визуальное содержимое не удалось распознать; учтены имя и извлечённый текст файла." });
      }
    }
  }
  return notes;
}

async function analyzeHomework(body) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 150_000);
  try {
    const files = Array.isArray(body.files) ? body.files : [];
    const imageNotes = await inspectImages(files, controller);
    const preparedFiles = files.slice(0, 12).map((file) => ({
      name: String(file?.name || "Файл").slice(0, 120),
      mime: String(file?.mime || "").slice(0, 100),
      text: String(file?.text || "").slice(0, 18_000),
      visual: imageNotes.find((item) => item.name === String(file?.name || "Файл"))?.note || "",
    }));
    const actions = {
      priorities: "Выбери, какие домашние задания выполнять первыми с учётом сроков и объёма.",
      summary: "Кратко объясни, что именно требуется сделать.",
      plan: "Составь практичный пошаговый план выполнения.",
      explain: "Объясни тему простыми словами, но не решай работу за ученика.",
      requirements: "Выдели требования, критерии готовности и то, что легко пропустить.",
      files: "Разбери все вложения и свяжи их содержимое с заданием.",
    };
    const prompt = `Действие: ${actions[body.action] || actions.summary}\n\nДомашняя работа:\n${JSON.stringify(compactForModel(body.homework || {})).slice(0, 35_000)}\n\nВложения:\n${JSON.stringify(preparedFiles).slice(0, 55_000)}\n\nОтветь только JSON без markdown: {"headline":"до 80 символов","summary":"до 280 символов, без воды","actions":["1–5 конкретных шагов"],"warnings":["только реальные риски, максимум 3"],"files":[{"name":"имя","note":"до 220 символов"}]}. Не повторяй одни и те же мысли. Если данных нет, скажи это одной короткой фразой.`;
    const raw = await callChat(model, [
      { role: "system", content: "Ты лаконичный помощник по домашним заданиям. Не выдумывай факты и не выполняй оцениваемую работу вместо ученика." },
      { role: "user", content: prompt },
    ], 1500, controller);
    return cleanStructuredResult(raw);
  } finally {
    clearTimeout(timeout);
  }
}

const server = https.createServer({ pfx, passphrase: config.pfxPassword }, async (request, response) => {
  const url = new URL(request.url || "/", `https://${request.headers.host || "localhost"}`);
  if (request.method === "GET" && url.pathname === "/health") {
    json(response, 200, { ok: true, service: "topjournal-ai", model, visionModel, capabilities: ["insights", "homework", "attachments"] });
    return;
  }
  if (!authorized(request)) {
    json(response, 401, { ok: false, error: "unauthorized" });
    return;
  }
  if (request.method !== "POST" || !["/api/insights", "/api/homework"].includes(url.pathname)) {
    json(response, 404, { ok: false, error: "not_found" });
    return;
  }
  if (!canRun(request.socket.remoteAddress || "unknown")) {
    json(response, 429, { ok: false, error: "rate_limited" });
    return;
  }
  try {
    const body = await readBody(request);
    if (url.pathname === "/api/homework") {
      if (!body || typeof body.homework !== "object") {
        json(response, 400, { ok: false, error: "invalid_homework" });
        return;
      }
      const result = await analyzeHomework(body);
      json(response, 200, { ok: true, model, visionModel, generatedAt: new Date().toISOString(), result });
    } else {
      if (!body || typeof body.snapshot !== "object" || Array.isArray(body.snapshot)) {
        json(response, 400, { ok: false, error: "invalid_snapshot" });
        return;
      }
      const analysis = await analyze(body.snapshot);
      json(response, 200, { ok: true, model, generatedAt: new Date().toISOString(), analysis });
    }
  } catch (error) {
    const reason = error instanceof SyntaxError ? "invalid_json"
      : error?.message === "payload_too_large" ? "payload_too_large"
      : error?.name === "AbortError" ? "provider_timeout"
      : error?.providerStatus ? `provider_${error.providerStatus}` : "provider_error";
    const message = error?.providerStatus === 401 || error?.providerStatus === 403
      ? "RuRouter отклонил ключ доступа. Перезапустите server/setup.ps1 с новым ключом."
      : error?.providerStatus === 402
        ? "Недостаточно средств на балансе RuRouter."
        : error?.providerStatus === 429
          ? "RuRouter временно ограничил частоту запросов. Повторите через минуту."
          : error?.providerStatus === 400
            ? "RuRouter отклонил формат или объём данных. Снимок уже сокращён — повторите запрос."
            : error?.name === "AbortError"
              ? "RuRouter не ответил за отведённое время. Повторите запрос."
              : error?.message === "empty_provider_response"
                ? "RuRouter завершил генерацию без итогового текста. Сервер уже повторил запрос; попробуйте ещё раз чуть позже."
              : "RuRouter временно не обработал запрос. Повторите попытку.";
    const diagnostic = error?.providerCode || error?.networkCode || error?.finishReason || "";
    console.error(`[${new Date().toISOString()}] ${reason}${diagnostic ? `:${diagnostic}` : ""}`);
    json(response, reason === "payload_too_large" ? 413 : 502, { ok: false, error: reason, message });
  }
});

server.requestTimeout = 165_000;
server.headersTimeout = 15_000;
server.listen(config.port, config.host, () => {
  console.log(`TopJournal AI: https://${config.ip}:${config.port} (${model})`);
});
