package com.dust.wxclawbackfront.bot.agent.llm.voice;

import com.dust.wxclawbackfront.bot.agent.llm.TenantAiKeyProvider;
import com.dust.wxclawbackfront.bot.agent.tools.shared.TextSanitizer;
import com.dust.wxclawbackfront.observability.llm.service.LlmInvocationRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class VolcTtsHandler {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final String url;
    private final Duration timeout;
    private final int maxAttempts;

    private final TenantAiKeyProvider keyProvider;
    private final String model;
    private final String outputFormat;
    private final Integer sampleRate;
    private final Integer pitchRate;
    private final Integer speechRate;
    private final Integer loudnessRate;

    private final int maxVoiceMs;
    private final int ilinkEncodeType;
    private final int ilinkBitsPerSample;
    private final LlmInvocationRecorder invocationRecorder;

    public VolcTtsHandler(ObjectMapper objectMapper,
                          LlmInvocationRecorder invocationRecorder,
                          @Value("${wxclaw.ai.tts.url:https://openspeech.bytedance.com/api/v3/tts/create}") String url,
                          @Value("${wxclaw.ai.tts.timeout:PT90S}") Duration timeout,
                          @Value("${wxclaw.ai.tts.max-attempts:2}") int maxAttempts,
                          TenantAiKeyProvider keyProvider,
                          @Value("${wxclaw.ai.tts.model:seed-audio-1.0}") String model,
                          @Value("${wxclaw.ai.tts.output-format:mp3}") String outputFormat,
                          @Value("${wxclaw.ai.tts.sample-rate:24000}") Integer sampleRate,
                          @Value("${wxclaw.ai.tts.pitch-rate:0}") Integer pitchRate,
                          @Value("${wxclaw.ai.tts.speech-rate:0}") Integer speechRate,
                          @Value("${wxclaw.ai.tts.loudness-rate:0}") Integer loudnessRate,
                          @Value("${wxclaw.ai.tts.max-voice-ms:60000}") int maxVoiceMs,
                          @Value("${wxclaw.ai.tts.ilink.encode-type:1}") int ilinkEncodeType,
                          @Value("${wxclaw.ai.tts.ilink.bits-per-sample:16}") int ilinkBitsPerSample) {
        this.objectMapper = objectMapper;
        this.invocationRecorder = invocationRecorder;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.url = url;
        this.timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
        this.maxAttempts = maxAttempts <= 0 ? 2 : maxAttempts;
        this.keyProvider = keyProvider;
        this.model = model;
        this.outputFormat = outputFormat;
        this.sampleRate = sampleRate;
        this.pitchRate = pitchRate;
        this.speechRate = speechRate;
        this.loudnessRate = loudnessRate;
        this.maxVoiceMs = maxVoiceMs;
        this.ilinkEncodeType = ilinkEncodeType;
        this.ilinkBitsPerSample = ilinkBitsPerSample;
    }

    public VolcTtsResult synthesize(String text) {
        if (text == null || text.isBlank()) {
            return new VolcTtsResult(null, null, "TTS 文本为空", null, null, null, null, null, null);
        }

        String actualUrl = url == null ? null : url.trim();
        if (actualUrl == null || actualUrl.isBlank()) {
            return new VolcTtsResult(null, null, "未配置 wxclaw.ai.tts.url", null, null, null, null, null, null);
        }
        String key = keyProvider.ttsKey() == null ? null : keyProvider.ttsKey().trim();
        if (key == null || key.isBlank()) {
            return new VolcTtsResult(null, null, "未配置 wxclaw.ai.tts.api-key", null, null, null, null, null, null);
        }
        String actualModel = model == null ? null : model.trim();
        if (actualModel == null || actualModel.isBlank()) {
            return new VolcTtsResult(null, null, "未配置 wxclaw.ai.tts.model", null, null, null, null, null, null);
        }

        Map<String, Object> payload = buildRequestPayload(actualModel, text.trim());
        String requestJson = toPrettyJsonOrNull(payload);
        LlmInvocationRecorder.InvocationHandle handle = invocationRecorder.start(
                "TTS", "VOLCENGINE", actualModel, requestJson);

        long start = System.currentTimeMillis();
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            HttpResponse<String> resp = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(actualUrl))
                        .timeout(timeout)
                        .header("X-Api-Key", key)
                        .header("X-Api-Request-Id", UUID.randomUUID().toString())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                        .build();
                try {
                    resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    break;
                } catch (IOException | InterruptedException ex) {
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                    }
                    log.warn("TTS 请求失败（第 {}/{} 次），将重试: {}", attempt, maxAttempts, ex.getMessage());
                    if (attempt == maxAttempts) {
                        throw ex;
                    }
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            log.info("TTS语音合成完成, 耗时={}ms, model={}", elapsed, actualModel);
            String responseText = resp.body();
            String responseJson = TextSanitizer.sanitizeForPrompt(toPrettyJsonOrRaw(responseText));

            if (resp.statusCode() / 100 != 2) {
                return complete(handle, new VolcTtsResult(requestJson, responseJson, buildHttpError(resp.statusCode(), responseText), null, null, null, null, null, null));
            }

            Integer code = extractIntField(responseText, "code");
            if (code != null && code != 0) {
                String message = extractStringField(responseText, "message");
                String msg = message == null || message.isBlank()
                        ? ("TTS 返回错误码: " + code)
                        : ("TTS 返回错误: " + message);
                String hint = actionableHint(code, message);
                if (hint != null) {
                    msg = msg + "。" + hint;
                }
                return complete(handle, new VolcTtsResult(requestJson, responseJson, msg, null, null, null, null, null, null));
            }

            String b64 = extractAudioBase64(responseText);
            if (b64 == null || b64.isBlank()) {
                return complete(handle, new VolcTtsResult(requestJson, responseJson, "TTS 响应缺少 audio/data 字段", null, null, null, null, null, null));
            }

            byte[] audioBytes = Base64.getDecoder().decode(b64);
            String ext = resolveExt(outputFormat);
            String fileName = "tts-" + Instant.now().toEpochMilli() + "." + ext;
            Integer playTimeMs = null;
            Integer actualSampleRate = sampleRate;
            Integer bitsPerSample = ilinkBitsPerSample;
            Integer encodeType = ilinkEncodeType;

            if ("wav".equalsIgnoreCase(outputFormat)) {
                byte[] trimmed = WavUtils.trimToDurationMs(audioBytes, maxVoiceMs);
                WavAudio wav = WavUtils.parse(trimmed);
                if (wav != null) {
                    actualSampleRate = wav.sampleRate();
                    bitsPerSample = wav.bitsPerSample();
                    playTimeMs = wav.durationMs();
                }
                audioBytes = trimmed;
            }

            if (playTimeMs == null) {
                Double durationSec = extractDoubleField(responseText, "duration");
                if (durationSec != null && durationSec > 0) {
                    playTimeMs = (int) Math.min((double) maxVoiceMs, durationSec * 1000.0);
                }
            }

            if (playTimeMs != null && playTimeMs > maxVoiceMs) {
                playTimeMs = maxVoiceMs;
            }

            return complete(handle, new VolcTtsResult(requestJson, responseJson, null, audioBytes, playTimeMs, actualSampleRate, bitsPerSample, encodeType, fileName));
        } catch (Exception ex) {
            String errorMsg = ex instanceof HttpTimeoutException
                    ? "语音合成服务响应超时，请稍后重试"
                    : ex.getMessage();
            return complete(handle, new VolcTtsResult(requestJson, null, errorMsg, null, null, null, null, null, null));
        }
    }

    private VolcTtsResult complete(LlmInvocationRecorder.InvocationHandle handle, VolcTtsResult result) {
        if (result.getErrorMsg() == null) {
            invocationRecorder.success(handle, result.getResponseJson(), null, null, null);
        } else {
            invocationRecorder.failure(handle, new IllegalStateException(result.getErrorMsg()), result.getResponseJson());
        }
        return result;
    }

    private Map<String, Object> buildRequestPayload(String model, String text) {
        Map<String, Object> audioConfig = new LinkedHashMap<>();
        audioConfig.put("format", outputFormat);
        audioConfig.put("sample_rate", sampleRate);
        audioConfig.put("pitch_rate", pitchRate);
        audioConfig.put("speech_rate", speechRate);
        audioConfig.put("loudness_rate", loudnessRate);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("text_prompt", text);
        payload.put("audio_config", audioConfig);
        payload.put("watermark", new LinkedHashMap<>());
        return payload;
    }

    private String extractAudioBase64(String responseText) {
        try {
            Map<?, ?> m = objectMapper.readValue(responseText, Map.class);
            Object audio = m.get("audio");
            if (audio != null) {
                return String.valueOf(audio);
            }
            Object data = m.get("data");
            return data == null ? null : String.valueOf(data);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveExt(String format) {
        if (format == null || format.isBlank()) {
            return "mp3";
        }
        String lower = format.trim().toLowerCase();
        if ("ogg_opus".equals(lower)) {
            return "ogg";
        }
        return lower;
    }

    private Integer extractIntField(String json, String fieldName) {
        try {
            Map<?, ?> m = objectMapper.readValue(json, Map.class);
            Object v = m.get(fieldName);
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
            if (v instanceof String s && !s.isBlank()) {
                return Integer.parseInt(s.trim());
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Double extractDoubleField(String json, String fieldName) {
        try {
            Map<?, ?> m = objectMapper.readValue(json, Map.class);
            Object v = m.get(fieldName);
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
            if (v instanceof String s && !s.isBlank()) {
                return Double.parseDouble(s.trim());
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 构造非 2xx 响应时的可读错误信息：优先解析服务端 code/message 并给出可执行提示，
     * 无法解析时回退为 HTTP 状态码 + 截断的响应体（响应体为空说明请求在网关层被拒绝）。
     */
    String buildHttpError(int statusCode, String responseText) {
        Integer code = extractIntField(responseText, "code");
        String message = extractStringField(responseText, "message");
        String hint = actionableHint(code, message);
        if (hint != null) {
            return hint;
        }
        if (statusCode == 401) {
            return "API Key 无效或未配置，请在豆包语音控制台核对 API Key（设置页「语音合成」或后端 wxclaw.ai.tts.api-key）";
        }
        String body = responseText == null ? "" : responseText.trim();
        if (body.length() > 200) {
            body = body.substring(0, 200) + "…";
        }
        return "TTS 请求失败，HTTP " + statusCode + (body.isEmpty() ? "" : "，服务端返回: " + body);
    }

    /**
     * 火山豆包语音常见错误映射：45000030 / "resource not granted" 表示账号未开通或未授权该模型资源
     * （HTTP 403 空响应体通常是同一原因在网关层被拒绝）。
     */
    private String actionableHint(Integer code, String message) {
        String msg = message == null ? "" : message;
        if ((code != null && code == 45000030) || msg.contains("requested resource not granted") || msg.contains("resource not granted")) {
            String detail = msg.isBlank() ? ("错误码 " + code) : msg;
            return "豆包语音模型服务未开通/未授权（" + detail + "）。请到火山引擎豆包语音控制台（console.volcengine.com/speech/new/setting/apikeys）开通语音合成/音频生成服务（seed-audio-1.0），并确认 API Key 已授权该模型";
        }
        if ((code != null && code == 45000003) || msg.contains("invalid api key") || msg.contains("invalid credential")) {
            return "API Key 无效，请在豆包语音控制台重新生成并核对";
        }
        return null;
    }

    private String extractStringField(String json, String fieldName) {
        try {
            Map<?, ?> m = objectMapper.readValue(json, Map.class);
            Object v = m.get(fieldName);
            return v == null ? null : String.valueOf(v);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toPrettyJsonOrNull(Object any) {
        if (any == null) {
            return null;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(any);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toPrettyJsonOrRaw(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            Object any = objectMapper.readValue(text, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(any);
        } catch (Exception ignore) {
            return text;
        }
    }
}
