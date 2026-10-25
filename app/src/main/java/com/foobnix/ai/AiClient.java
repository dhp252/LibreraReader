package com.foobnix.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * One question to the model, streamed: the Claude Messages API, or an OpenAI-compatible chat
 * completions endpoint (OpenAI, OpenRouter, a local LM Studio or Ollama). Plain HTTP on the
 * OkHttp 3 the app already ships: the Anthropic Java SDK needs OkHttp 4, which the app cannot take.
 */
public class AiClient {

    /** Text as it arrives; called on the thread that runs {@link #complete}. */
    public interface Output {
        void onText(String delta);
    }

    /** The provider answered, but not with a usable answer. */
    public static class AiException extends IOException {
        public final boolean refusal;
        /**
         * Worth asking again later: no answer (the network), a busy or failing server (408, 429,
         * 5xx, overloaded), an answer cut short in the stream. Not a refused key or request.
         */
        public final boolean retryable;

        public AiException(String message) {
            this(message, false, false);
        }

        AiException(String message, boolean refusal) {
            this(message, refusal, false);
        }

        AiException(String message, boolean refusal, boolean retryable) {
            super(message);
            this.refusal = refusal;
            this.retryable = retryable;
        }
    }

    /** HTTP statuses worth another try: a timeout, too many requests, a server error or overload. */
    static boolean retryableStatus(int code) {
        return code == 408 || code == 429 || code >= 500;
    }

    /** Error types of a stream worth another try. */
    static boolean retryableError(String type) {
        return "overloaded_error".equals(type) || "rate_limit_error".equals(type) || "api_error".equals(type)
                || "timeout_error".equals(type);
    }

    static final String ANTHROPIC_VERSION = "2023-06-01";
    /** Re-runs a request the model's safety classifiers declined on a recommended model, server side. */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    private static final Set<String> FALLBACK_MODELS = new HashSet<>(Arrays.asList(
            "claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5"));
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    /** Long answers stream, so this caps only an answer that runs away. */
    private static final int MAX_TOKENS = 64000;

    private final OkHttpClient http;
    private final AiSettings settings;
    private volatile Call call;
    private volatile String stopReason;
    private volatile Usage usage;

    /** Tokens of the last Claude answer as the API counted them, prompt caching included. */
    public static class Usage {
        /** Input not read from the cache nor written to it. */
        public int input;
        public int cacheRead;
        public int cacheWrite;
        public int output;

        @Override
        public String toString() {
            return "input " + input + ", cache read " + cacheRead + ", cache write " + cacheWrite + ", output " + output;
        }
    }

    public AiClient(AiSettings settings) {
        this(new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                // between two chunks of the stream: a local model can take long over a big prompt
                .readTimeout(300, TimeUnit.SECONDS)
                .build(), settings);
    }

    AiClient(OkHttpClient http, AiSettings settings) {
        this.http = http;
        this.settings = settings;
    }

    /** Stops the request running in {@link #complete}, which then throws. */
    public void cancel() {
        final Call running = call;
        if (running != null) {
            running.cancel();
        }
    }

    /** Why the last answer ended: "end_turn", "max_tokens" (cut off), "stop"... */
    public String getStopReason() {
        return stopReason;
    }

    /** The tokens of the last Claude answer, or null (another provider, or none counted). */
    public Usage getUsage() {
        return usage;
    }

    /** @return the whole answer; {@code output} also gets it piece by piece as it streams */
    public String complete(String system, String user, Output output) throws IOException {
        final Request request;
        try {
            request = settings.isClaude() ? claudeRequest(system, user) : openAiRequest(system, user);
        } catch (JSONException | IllegalArgumentException e) {
            throw new AiException(e.getMessage());
        }
        stopReason = null;
        usage = null;
        call = http.newCall(request);
        try (Response response = call.execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw new AiException(errorMessage(response.code(), body == null ? "" : body.string()), false,
                        retryableStatus(response.code()));
            }
            if (body == null) {
                throw new AiException("empty answer");
            }
            return settings.isClaude() ? readClaude(body.source(), output) : readOpenAi(body.source(), output);
        } catch (JSONException e) {
            throw new AiException("unreadable answer: " + e.getMessage());
        } finally {
            call = null;
        }
    }

    // --- Claude Messages API ---

    Request claudeRequest(String system, String user) throws JSONException {
        final JSONObject body = new JSONObject();
        body.put("model", settings.model.trim());
        body.put("max_tokens", MAX_TOKENS);
        body.put("stream", true);
        if (!system.isEmpty()) {
            // the same instructions go with every part of a task: cached, the next requests read them
            // back at a tenth of the price. Below the model's minimum (512 to 4096 tokens) nothing is
            // cached and nothing extra is paid; the book's text differs per request and is never cached.
            body.put("system", new JSONArray().put(new JSONObject()
                    .put("type", "text")
                    .put("text", system)
                    .put("cache_control", new JSONObject().put("type", "ephemeral"))));
        }
        body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", user)));
        final Request.Builder request = new Request.Builder()
                .url(endpoint(settings.url, "v1/messages"))
                .header("x-api-key", settings.apiKey.trim())
                .header("anthropic-version", ANTHROPIC_VERSION);
        if (usesServerFallback()) {
            body.put("fallbacks", "default");
            request.header("anthropic-beta", FALLBACK_BETA);
        }
        return request.post(RequestBody.create(JSON, body.toString())).build();
    }

    /** Server-side fallback exists for these models on Anthropic's own API (not on proxies). */
    boolean usesServerFallback() {
        final HttpUrl url = HttpUrl.parse(settings.url.trim());
        return url != null && "api.anthropic.com".equals(url.host()) && FALLBACK_MODELS.contains(settings.model.trim());
    }

    String readClaude(BufferedSource source, Output output) throws IOException, JSONException {
        final StringBuilder text = new StringBuilder();
        String category = null;
        String line;
        while ((line = source.readUtf8Line()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }
            final String data = line.substring(5).trim();
            if (data.isEmpty()) {
                continue;
            }
            final JSONObject event = new JSONObject(data);
            final String type = event.optString("type");
            if ("message_start".equals(type)) {
                final JSONObject message = event.optJSONObject("message");
                count(message == null ? null : message.optJSONObject("usage"));
            } else if ("content_block_delta".equals(type)) {
                final JSONObject delta = event.optJSONObject("delta");
                // only text: thinking and other blocks (a fallback marker) are not part of the answer
                if (delta != null && "text_delta".equals(delta.optString("type"))) {
                    final String piece = delta.optString("text");
                    text.append(piece);
                    if (output != null) {
                        output.onText(piece);
                    }
                }
            } else if ("message_delta".equals(type)) {
                final JSONObject delta = event.optJSONObject("delta");
                if (delta != null && !delta.isNull("stop_reason")) {
                    stopReason = delta.optString("stop_reason", null);
                }
                JSONObject details = delta == null ? null : delta.optJSONObject("stop_details");
                if (details == null) {
                    details = event.optJSONObject("stop_details");
                }
                if (details != null && !details.isNull("category")) {
                    category = details.optString("category", null);
                }
                count(event.optJSONObject("usage"));
            } else if ("error".equals(type)) {
                final JSONObject error = event.optJSONObject("error");
                throw new AiException(error == null ? data : error.optString("type") + ": " + error.optString("message"),
                        false, error != null && retryableError(error.optString("type")));
            } else if ("message_stop".equals(type)) {
                break;
            }
        }
        if ("refusal".equals(stopReason)) {
            // declined by the model or its safety classifiers: what streamed before is not an answer
            throw new AiException("declined" + (category == null ? "" : " (" + category + ")"), true);
        }
        if (stopReason == null) {
            throw new AiException("the answer stopped before it was complete", false, true);
        }
        return text.toString();
    }

    /** The counts of message_start, then the totals message_delta may repeat or raise. */
    private void count(JSONObject counted) {
        if (counted == null) {
            return;
        }
        final Usage u = usage == null ? new Usage() : usage;
        u.input = counted.optInt("input_tokens", u.input);
        u.cacheRead = counted.optInt("cache_read_input_tokens", u.cacheRead);
        u.cacheWrite = counted.optInt("cache_creation_input_tokens", u.cacheWrite);
        u.output = counted.optInt("output_tokens", u.output);
        usage = u;
    }

    // --- OpenAI-compatible chat completions ---

    Request openAiRequest(String system, String user) throws JSONException {
        final JSONObject body = new JSONObject();
        body.put("model", settings.model.trim());
        body.put("stream", true);
        body.put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system))
                .put(new JSONObject().put("role", "user").put("content", user)));
        final Request.Builder request = new Request.Builder().url(endpoint(settings.url, "chat/completions"));
        if (!settings.apiKey.trim().isEmpty()) {
            request.header("Authorization", "Bearer " + settings.apiKey.trim());
        }
        return request.post(RequestBody.create(JSON, body.toString())).build();
    }

    String readOpenAi(BufferedSource source, Output output) throws IOException, JSONException {
        final StringBuilder text = new StringBuilder();
        boolean done = false;
        String line;
        while ((line = source.readUtf8Line()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }
            final String data = line.substring(5).trim();
            if (data.isEmpty()) {
                continue;
            }
            if ("[DONE]".equals(data)) {
                done = true;
                break;
            }
            final JSONObject chunk = new JSONObject(data);
            final JSONObject error = chunk.optJSONObject("error");
            if (error != null) {
                throw new AiException(error.optString("message", data));
            }
            final JSONArray choices = chunk.optJSONArray("choices");
            final JSONObject choice = choices == null || choices.length() == 0 ? null : choices.optJSONObject(0);
            if (choice == null) {
                continue;
            }
            final JSONObject delta = choice.optJSONObject("delta");
            if (delta != null && !delta.isNull("content")) {
                final String piece = delta.optString("content");
                text.append(piece);
                if (output != null) {
                    output.onText(piece);
                }
            }
            if (!choice.isNull("finish_reason")) {
                stopReason = choice.optString("finish_reason", null);
            }
        }
        if (!done && stopReason == null) {
            throw new AiException("the answer stopped before it was complete", false, true);
        }
        return text.toString();
    }

    // --- shared ---

    static HttpUrl endpoint(String base, String path) {
        final HttpUrl url = HttpUrl.parse(base.trim());
        if (url == null) {
            throw new IllegalArgumentException("not an http(s) URL: " + base);
        }
        final HttpUrl.Builder builder = url.newBuilder();
        // "https://host/v1/" and "https://host/v1" both mean the folder v1
        final int last = url.pathSize() - 1;
        if (last >= 0 && url.pathSegments().get(last).isEmpty()) {
            builder.removePathSegment(last);
        }
        builder.addPathSegments(path);
        return builder.build();
    }

    /** The message of an error answer of either API, with the status code. */
    static String errorMessage(int code, String body) {
        String message = null;
        try {
            final JSONObject json = new JSONObject(body);
            final JSONObject error = json.optJSONObject("error");
            if (error != null) {
                message = error.optString("message", null);
            } else {
                message = json.optString("message", null);
            }
        } catch (JSONException e) {
            // not JSON: the status code alone
        }
        if (code == 401 || code == 403) {
            return code + ": the API key was not accepted" + (message == null ? "" : " (" + message + ")");
        }
        if (message == null || message.isEmpty()) {
            message = body == null || body.length() > 300 ? "" : body.trim();
        }
        return code + (message.isEmpty() ? "" : ": " + message);
    }
}
