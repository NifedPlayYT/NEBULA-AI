export interface ChatLine {
  role: "system" | "user" | "assistant";
  content: string;
}

export class ProviderError extends Error {
  readonly status: number;
  readonly provider: string;
  readonly safeMessage: string;
  readonly retryable: boolean;

  constructor(provider: string, status: number, message: string, retryable: boolean) {
    super(message);
    this.provider = provider;
    this.status = status;
    this.safeMessage = message;
    this.retryable = retryable;
  }
}

interface ProviderConfig {
  name: "deepseek" | "openrouter";
  url: string;
  key: string;
  model: string;
}

function getProviders(): ProviderConfig[] {
  const deepseekKey = Deno.env.get("DEEPSEEK_API_KEY") ?? "";
  const deepseekModel = Deno.env.get("DEEPSEEK_MODEL") ?? "deepseek-flash";
  const routerKey = Deno.env.get("OPENROUTER_API_KEY") ?? "";
  const routerModel = Deno.env.get("OPENROUTER_MODEL") ?? "";

  const providers: ProviderConfig[] = [];
  if (deepseekKey) {
    providers.push({
      name: "deepseek",
      url: "https://api.deepseek.com/chat/completions",
      key: deepseekKey,
      model: deepseekModel,
    });
  }
  if (routerKey && routerModel) {
    providers.push({
      name: "openrouter",
      url: "https://openrouter.ai/api/v1/chat/completions",
      key: routerKey,
      model: routerModel,
    });
  }
  return providers;
}

function retryableStatus(status: number): boolean {
  return status === 408 || status === 409 || status === 425 ||
    status === 429 || status >= 500;
}

async function callProvider(
  provider: ProviderConfig,
  messages: ChatLine[],
  stream: boolean,
): Promise<Response> {
  const signal = AbortSignal.timeout(95_000);
  let response: Response;
  try {
    response = await fetch(provider.url, {
      method: "POST",
      signal,
      headers: {
        Authorization: `Bearer ${provider.key}`,
        "Content-Type": "application/json",
        Accept: stream ? "text/event-stream" : "application/json",
        ...(provider.name === "openrouter"
          ? { "HTTP-Referer": "https://github.com/NifedPlayYT/NEBULA-AI", "X-Title": "NEBULA AI" }
          : {}),
      },
      body: JSON.stringify({
        model: provider.model,
        messages,
        stream,
        temperature: 0.78,
        max_tokens: 900,
      }),
    });
  } catch (error) {
    throw new ProviderError(provider.name, 0, "Provider transport failed.", true);
  }

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    const parsed = (() => {
      try {
        const json = JSON.parse(body) as { error?: { message?: string } };
        return json.error?.message ?? body;
      } catch {
        return body;
      }
    })();
    const contentRisk = /content exists risk/i.test(parsed);
    throw new ProviderError(
      provider.name,
      response.status,
      contentRisk ? "Content Exists Risk" : parsed.slice(0, 500) || "Provider request failed.",
      !contentRisk && retryableStatus(response.status),
    );
  }

  if (stream && !response.body) {
    throw new ProviderError(provider.name, 502, "Provider returned an empty stream.", true);
  }
  return response;
}

async function request(
  messages: ChatLine[],
  stream: boolean,
): Promise<{ response: Response; provider: string }> {
  const providers = getProviders();
  if (providers.length === 0) {
    throw new Error("DEEPSEEK_API_KEY is not configured on the server.");
  }

  let firstError: unknown;
  for (let index = 0; index < providers.length; index++) {
    const provider = providers[index];
    try {
      const response = await callProvider(provider, messages, stream);
      return { response, provider: provider.name };
    } catch (error) {
      if (index === 0) firstError = error;
      const canTryNext = index + 1 < providers.length &&
        (error instanceof ProviderError ? error.retryable : true);
      if (!canTryNext) throw error;
      console.warn("Primary AI provider failed; trying configured fallback.", {
        provider: provider.name,
        status: error instanceof ProviderError ? error.status : 0,
      });
    }
  }
  throw firstError ?? new Error("No AI provider accepted the request.");
}

export async function openChatStream(
  messages: ChatLine[],
): Promise<{ response: Response; provider: string }> {
  return await request(messages, true);
}

export async function completeChat(
  messages: ChatLine[],
): Promise<{ text: string; provider: string }> {
  const result = await request(messages, false);
  const body = await result.response.json() as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const content = body.choices?.[0]?.message?.content ?? "";
  if (!content.trim()) throw new Error("The model returned an empty completion.");
  return { text: content, provider: result.provider };
}
