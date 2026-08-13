interface Env {
  BRAVE_SEARCH_API_KEY: string;
}

type SearchRequest = { query: string; locale?: string };
type BraveItem = {
  title?: string;
  url?: string;
  description?: string;
  page_age?: string;
  profile?: { long_name?: string };
};

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/v1/search") {
      return json({ error: "not_found" }, 404);
    }
    if (!request.headers.get("content-type")?.toLowerCase().startsWith("application/json")) {
      return json({ error: "json_required" }, 415);
    }
    const length = Number(request.headers.get("content-length") ?? 0);
    if (!Number.isFinite(length) || length > 8_192) return json({ error: "request_too_large" }, 413);

    let body: SearchRequest;
    try {
      const rawBody = await request.arrayBuffer();
      if (rawBody.byteLength > 8_192) return json({ error: "request_too_large" }, 413);
      body = JSON.parse(new TextDecoder().decode(rawBody)) as SearchRequest;
    } catch {
      return json({ error: "invalid_json" }, 400);
    }
    const query = normalize(body.query ?? "").slice(0, 512);
    if (!query) return json({ error: "query_required" }, 400);
    const locale = /^[a-z]{2}(?:-[A-Z]{2})?$/.test(body.locale ?? "") ? body.locale! : "ko-KR";

    const providerUrl = new URL("https://api.search.brave.com/res/v1/web/search");
    providerUrl.searchParams.set("q", query);
    providerUrl.searchParams.set("count", "5");
    providerUrl.searchParams.set("safesearch", "strict");
    providerUrl.searchParams.set("search_lang", locale.slice(0, 2));
    const upstream = await fetch(providerUrl, {
      headers: {
        accept: "application/json",
        "x-subscription-token": env.BRAVE_SEARCH_API_KEY,
      },
      redirect: "error",
    });
    if (!upstream.ok) return json({ error: "search_unavailable" }, 502);
    const payload = await upstream.json<{ web?: { results?: BraveItem[] } }>();
    const results = (payload.web?.results ?? []).slice(0, 5).flatMap((item) => {
      const sourceUrl = safeHttpsUrl(item.url);
      if (!sourceUrl) return [];
      return [{
        title: normalize(item.title ?? "제목 없음").slice(0, 240),
        host: sourceUrl.hostname,
        url: sourceUrl.toString(),
        snippet: normalize(item.description ?? "").slice(0, 2_000),
        publishedAt: item.page_age?.slice(0, 40) ?? null,
      }];
    });
    return json({ results }, 200);
  },
};

function normalize(value: string): string {
  return value.replace(/[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/g, " ").replace(/\s+/g, " ").trim();
}

function safeHttpsUrl(value?: string): URL | null {
  try {
    const url = new URL(value ?? "");
    return url.protocol === "https:" && !url.username && !url.password ? url : null;
  } catch {
    return null;
  }
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}
