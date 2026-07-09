package com.dust.wxclawbackfront.ai.tools.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TextSanitizer {

    private TextSanitizer() {
    }

    public static String summarizeDataUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("data:")) {
            return trimmed;
        }
        int comma = trimmed.indexOf(',');
        if (comma < 0) {
            return trimmed;
        }
        String header = trimmed.substring(0, comma + 1);
        String payload = trimmed.substring(comma + 1);
        String hash = sha256Hex(payload);
        return header + "<omitted len=" + payload.length() + " sha256=" + hash + ">";
    }

    public static String sanitizeForPrompt(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String step1 = replaceInlineDataUrlPayload(text);
        return replaceLongBase64Runs(step1, 2000);
    }

    private static String replaceInlineDataUrlPayload(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (true) {
            int start = text.indexOf("data:", i);
            if (start < 0) {
                sb.append(text, i, text.length());
                return sb.toString();
            }
            sb.append(text, i, start);
            int comma = text.indexOf(',', start);
            if (comma < 0) {
                sb.append(text, start, text.length());
                return sb.toString();
            }
            int end = findDataUrlPayloadEnd(text, comma + 1);
            String header = text.substring(start, comma + 1);
            String payload = text.substring(comma + 1, end);
            String hash = sha256Hex(payload);
            sb.append(header)
                    .append("<omitted len=")
                    .append(payload.length())
                    .append(" sha256=")
                    .append(hash)
                    .append(">");
            i = end;
        }
    }

    private static int findDataUrlPayloadEnd(String text, int from) {
        int i = from;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == ')' || c == ']' || c == '}' || c == '<' || c == '>') {
                return i;
            }
            i++;
        }
        return text.length();
    }

    private static String replaceLongBase64Runs(String text, int threshold) {
        if (text == null || text.isBlank() || threshold <= 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!isBase64Char(c)) {
                sb.append(c);
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && isBase64Char(text.charAt(i))) {
                i++;
            }
            int len = i - start;
            String run = text.substring(start, i);
            if (len >= threshold && looksLikeBase64(run)) {
                String hash = sha256Hex(run);
                sb.append("<base64 omitted len=").append(len).append(" sha256=").append(hash).append(">");
            } else {
                sb.append(run);
            }
        }
        return sb.toString();
    }

    private static boolean looksLikeBase64(String run) {
        return run.indexOf('/') >= 0 || run.indexOf('+') >= 0 || run.indexOf('=') >= 0;
    }

    private static boolean isBase64Char(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '+'
                || c == '/'
                || c == '=';
    }

    private static String sha256Hex(String text) {
        if (text == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "n/a";
        }
    }
}


