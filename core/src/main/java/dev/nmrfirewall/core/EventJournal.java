package dev.nmrfirewall.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.List;

public final class EventJournal implements Closeable {
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(java.time.Instant.class,
                    (com.google.gson.JsonSerializer<java.time.Instant>) (value, type, context) -> new JsonPrimitive(value.toString()))
            .create();
    private int capacity;
    private final Path file;
    private final ArrayDeque<SecurityEvent> events = new ArrayDeque<>();
    private BufferedWriter writer;

    public EventJournal(Path file, int capacity) throws IOException {
        this.capacity = Math.max(100, capacity);
        this.file = file;
        Files.createDirectories(file.toAbsolutePath().getParent());
        openWriter();
    }

    public synchronized void append(SecurityEvent event) throws IOException {
        if (Files.exists(file) && Files.size(file) > 5L * 1024 * 1024) rotate();
        while (events.size() >= capacity) events.removeFirst();
        events.addLast(event);
        writer.write(gson.toJson(event));
        writer.newLine();
        writer.flush();
    }

    public synchronized List<SecurityEvent> snapshot() { return List.copyOf(events); }
    public synchronized void setCapacity(int capacity) {
        this.capacity = Math.max(100, capacity);
        while (events.size() > this.capacity) events.removeFirst();
    }
    @Override public synchronized void close() throws IOException { writer.close(); }

    private void rotate() throws IOException {
        writer.close();
        Path previous = file.resolveSibling(file.getFileName() + ".1");
        Files.move(file, previous, StandardCopyOption.REPLACE_EXISTING);
        openWriter();
    }

    private void openWriter() throws IOException {
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
}
