package dev.nmrfirewall.agent;

public final class BlockedOperationException extends SecurityException {
    public BlockedOperationException(String message) { super(message); }
}
