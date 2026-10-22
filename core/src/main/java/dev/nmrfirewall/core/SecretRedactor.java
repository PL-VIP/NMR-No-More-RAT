package dev.nmrfirewall.core;

import java.util.regex.Pattern;

public final class SecretRedactor {
    private static final String MASK = "<REDACTED>";
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+\\-/]+=*");
    private static final Pattern TOKEN_PARAM = Pattern.compile("(?i)(access[_-]?token|refresh[_-]?token|session|authorization|password)([=:\\s]+)([^&\\s,;]+)");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}\\b");

    private SecretRedactor() { }

    public static String redact(String value) {
        if (value == null) return null;
        String result = BEARER.matcher(value).replaceAll("$1" + MASK);
        result = TOKEN_PARAM.matcher(result).replaceAll("$1$2" + MASK);
        return JWT.matcher(result).replaceAll(MASK);
    }
}
