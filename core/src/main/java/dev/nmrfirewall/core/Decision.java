package dev.nmrfirewall.core;

public enum Decision {
    ALLOW, DENY, ASK;

    public boolean allowed() { return this == ALLOW; }
}
