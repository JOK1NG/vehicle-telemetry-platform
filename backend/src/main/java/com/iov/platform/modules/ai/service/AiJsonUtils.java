package com.iov.platform.modules.ai.service;

final class AiJsonUtils {

    private AiJsonUtils() {
    }

    static boolean looksTruncatedJson(String raw) {
        String json = stripFence(raw);
        if (!json.startsWith("{")) {
            return false;
        }
        if (!json.endsWith("}")) {
            return true;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            if (depth < 0) {
                return true;
            }
        }
        return depth != 0 || inString;
    }

    static String stripFence(String raw) {
        if (raw == null) {
            return "";
        }
        String json = raw.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        return json.trim();
    }
}
