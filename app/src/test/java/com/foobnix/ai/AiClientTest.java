package com.foobnix.ai;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Buffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AiClientTest {

    private static AiSettings claude(String url, String model) {
        AiSettings s = new AiSettings();
        s.provider = AiSettings.CLAUDE;
        s.url = url;
        s.model = model;
        s.apiKey = " sk-ant-test ";
        return s;
    }

    private static AiSettings openAi(String url, String key) {
        AiSettings s = new AiSettings();
        s.provider = AiSettings.OPENAI;
        s.url = url;
        s.model = "local-model";
        s.apiKey = key;
        return s;
    }

    private static JSONObject body(Request request) throws Exception {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return new JSONObject(buffer.readUtf8());
    }

    private static Buffer sse(String... events) {
        StringBuilder out = new StringBuilder();
        for (String e : events) {
            out.append("event: x\n").append("data: ").append(e).append("\n\n");
        }
        return new Buffer().writeUtf8(out.toString());
    }

    @Test
    public void claudeRequestStreamsWithKeyVersionAndServerFallback() throws Exception {
        AiClient client = new AiClient(new OkHttpClient(), claude("https://api.anthropic.com", "claude-opus-5-5"));
        Request request = client.claudeRequest("Be brief.", "Summarize.");

        assertEquals("https://api.anthropic.com/v1/messages", request.url().toString());
        assertEquals("sk-ant-test", request.header("x-api-key"));
        assertEquals("2023-06-01", request.header("anthropic-version"));
        assertEquals("server-side-fallback-2026-07-01", request.header("anthropic-beta"));
        JSONObject body = body(request);
        assertEquals("claude-opus-5-5", body.getString("model"));
        assertTrue(body.getBoolean("stream"));
        JSONObject system = body.getJSONArray("system").getJSONObject(0);
        assertEquals("text", system.getString("type"));
        assertEquals("Be brief.", system.getString("text"));
        assertEquals("ephemeral", system.getJSONObject("cache_control").getString("type"));
        assertFalse("the book's text is not cached", body.has("cache_control"));
        assertEquals("Summarize.", body.getJSONArray("messages").getJSONObject(0).getString("content"));
        assertEquals("default", body.getString("fallbacks"));
        assertFalse(body.has("thinking"));
    }

    @Test
    public void serverFallbackOnlyForAnthropicsOwnApiAndItsModels() throws Exception {
        Request proxy = new AiClient(new OkHttpClient(), claude("https://proxy.example.com/", "claude-opus-5-5"))
                .claudeRequest("s", "u");
        assertNull(proxy.header("anthropic-beta"));
        assertFalse(body(proxy).has("fallbacks"));
        assertEquals("https://proxy.example.com/v1/messages", proxy.url().toString());

        Request haiku = new AiClient(new OkHttpClient(), claude("https://api.anthropic.com", "claude-haiku-5-5"))
                .claudeRequest("s", "u");
        assertNull(haiku.header("anthropic-beta"));
        assertFalse(body(haiku).has("fallbacks"));
    }

    @Test
    public void openAiRequestUsesTheBaseUrlAndAnOptionalKey() throws Exception {
        Request local = new AiClient(new OkHttpClient(), openAi("http://192.168.1.5:1234/v1/", "")).openAiRequest("s", "u");
        assertEquals("http://192.168.1.5:1234/v1/chat/completions", local.url().toString());
        assertNull(local.header("Authorization"));
        JSONObject body = body(local);
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("u", body.getJSONArray("messages").getJSONObject(1).getString("content"));

        Request hosted = new AiClient(new OkHttpClient(), openAi("https://api.openai.com/v1", "sk-1")).openAiRequest("s", "u");
        assertEquals("Bearer sk-1", hosted.header("Authorization"));
    }

    @Test
    public void claudeStreamGivesTheTextOnly() throws Exception {
        AiClient client = new AiClient(new OkHttpClient(), claude("https://api.anthropic.com", "claude-opus-5-5"));
        List<String> pieces = new ArrayList<>();
        String text = client.readClaude(sse(
                "{\"type\":\"message_start\",\"message\":{}}",
                "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\"}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"hmm\"}}",
                "{\"type\":\"ping\"}",
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello \"}}",
                "{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"world\"}}",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}",
                "{\"type\":\"message_stop\"}"), pieces::add);

        assertEquals("Hello world", text);
        assertEquals(2, pieces.size());
        assertEquals("end_turn", client.getStopReason());
    }

    @Test
    public void anEmptySystemPromptIsLeftOut() throws Exception {
        AiClient client = new AiClient(new OkHttpClient(), claude("https://api.anthropic.com", "claude-opus-5-5"));
        assertFalse(body(client.claudeRequest("", "u")).has("system"));
    }

    @Test
    public void claudeStreamCountsTheTokensWithTheCache() throws Exception {
        AiClient client = new AiClient(new OkHttpClient(), claude("https://api.anthropic.com", "claude-opus-5-5"));
        assertNull(client.getUsage());
        client.readClaude(sse(
                "{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":3000,"
                        + "\"cache_read_input_tokens\":900,\"cache_creation_input_tokens\":0,\"output_tokens\":1}}}",
                "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":250}}",
                "{\"type\":\"message_stop\"}"), null);

        AiClient.Usage usage = client.getUsage();
        assertEquals(3000, usage.input);
        assertEquals(900, usage.cacheRead);
        assertEquals(0, usage.cacheWrite);
        assertEquals("message_delta's count wins", 250, usage.output);
    }

    @Test
    public void aClaudeRefusalIsAnErrorNotAnAnswer() throws Exception {
        AiClient client = new AiClient(new OkHttpClient(), claude("https://api.anthropic.com", "claude-opus-5-5"));
        try {
            client.readClaude(sse(
                    "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Part\"}}",
                    "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"refusal\",\"stop_details\":{\"category\":\"cyber\"}}}",
                    "{\"type\":\"message_stop\"}"), null);
            fail();
        } catch (AiClient.AiException e) {
            assertTrue(e.refusal);
            assertFalse("a refusal comes back the same", e.retryable);
            assertTrue(e.getMessage().contains("cyber"));
        }
    }

    @Test
    public void aClaudeStreamThatStopsEarlyOrFailsIsAnError() throws Exception {
        AiClient client = new AiClient(new OkHttpClient(), claude("https://api.anthropic.com", "claude-opus-5-5"));
        try {
            client.readClaude(sse("{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Par\"}}"), null);
            fail();
        } catch (AiClient.AiException e) {
            assertFalse(e.refusal);
            assertTrue("a stream cut short may come whole next time", e.retryable);
        }
        try {
            client.readClaude(sse("{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}"), null);
            fail();
        } catch (AiClient.AiException e) {
            assertEquals("overloaded_error: Overloaded", e.getMessage());
            assertTrue(e.retryable);
        }
    }

    @Test
    public void openAiStreamEndsWithDone() throws Exception {
        AiClient client = new AiClient(new OkHttpClient(), openAi("http://localhost:1234/v1", ""));
        String text = client.readOpenAi(new Buffer().writeUtf8(
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                        + ": keep-alive\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"One \"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\"two\"},\"finish_reason\":\"length\"}]}\n\n"
                        + "data: [DONE]\n\n"), null);
        assertEquals("One two", text);
        assertEquals("length", client.getStopReason());
    }

    @Test
    public void errorMessagesNameTheProblem() {
        assertEquals("401: the API key was not accepted (invalid x-api-key)", AiClient.errorMessage(401,
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"));
        assertEquals("404: model not found", AiClient.errorMessage(404, "{\"error\":{\"message\":\"model not found\"}}"));
        assertEquals("502: Bad gateway", AiClient.errorMessage(502, "Bad gateway"));
    }
}
