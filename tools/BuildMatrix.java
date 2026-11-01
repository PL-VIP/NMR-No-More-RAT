import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Cross-platform release builder. Run with: java tools/BuildMatrix.java
 * It needs only a JDK and the Gradle wrapper already committed to this repository.
 */
public final class BuildMatrix {
    private static final Pattern MINECRAFT = Pattern.compile("\\\"minecraft\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern VERSION = Pattern.compile("(?m)^mod_version=(.+)$");

    private BuildMatrix() { }

    public static void main(String[] args) throws Exception {
        Arguments options = Arguments.parse(args);
        Path root = Path.of("").toAbsolutePath().normalize();
        List<String> supported = versions(root.resolve("versions.json"));
        List<String> targets = options.targets.isEmpty() ? supported : options.targets;
        for (String target : targets) {
            if (!supported.contains(target)) throw new IllegalArgumentException("Unsupported build target: " + target);
        }

        for (String target : targets) build(root, target, options.javaHome);

        String releaseVersion = property(root.resolve("gradle.properties"), VERSION);
        Path release = root.resolve("build/releases").resolve(releaseVersion);
        Files.createDirectories(release);
        List<Path> artifacts = new ArrayList<>();
        for (String target : targets) {
            String name = "no-more-rat-" + target + "-" + releaseVersion + ".jar";
            Path source = root.resolve("fabric/build").resolve(target).resolve("libs").resolve(name);
            if (!Files.isRegularFile(source)) throw new IOException("Missing release artifact: " + source);
            Path destination = release.resolve(name);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            artifacts.add(destination);
        }

        List<String> checksums = new ArrayList<>();
        for (Path artifact : artifacts) checksums.add(sha256(artifact) + "  " + artifact.getFileName());
        Path sums = release.resolve("SHA256SUMS.txt");
        Files.writeString(sums, String.join("\n", checksums) + "\n", StandardCharsets.US_ASCII);
        if (options.targets.isEmpty()) zip(release.resolve("no-more-rat-" + releaseVersion + "-multiversion.zip"), artifacts, sums);
        System.out.println("Verified build artifacts: " + release);
    }

    private static void build(Path root, String target, String javaHome) throws IOException, InterruptedException {
        System.out.println("Building NMR for Minecraft " + target);
        List<String> command = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            command.add("cmd"); command.add("/c"); command.add("gradlew.bat");
        } else {
            command.add("./gradlew");
        }
        command.add(":fabric:build");
        command.add("-Pmc=" + target);
        command.add("--console=plain");
        command.add("--no-daemon");
        if (javaHome != null) command.add("-Dorg.gradle.java.home=" + javaHome);
        Process process = new ProcessBuilder(command).directory(root.toFile()).inheritIO().start();
        if (process.waitFor() != 0) throw new IOException("Build failed for " + target + "; no release was created.");
    }

    private static List<String> versions(Path file) throws IOException {
        Matcher matcher = MINECRAFT.matcher(Files.readString(file));
        List<String> result = new ArrayList<>();
        while (matcher.find()) result.add(matcher.group(1));
        if (result.isEmpty()) throw new IOException("No Minecraft targets found in " + file);
        return List.copyOf(result);
    }

    private static String property(Path file, Pattern pattern) throws IOException {
        Matcher matcher = pattern.matcher(Files.readString(file));
        if (!matcher.find()) throw new IOException("Missing mod_version in " + file);
        return matcher.group(1).trim();
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void zip(Path output, List<Path> artifacts, Path sums) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (Path file : artifacts) add(zip, file);
            add(zip, sums);
        }
    }

    private static void add(ZipOutputStream zip, Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(file.getFileName().toString()));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static final class Arguments {
        private final String javaHome;
        private final List<String> targets;

        private Arguments(String javaHome, List<String> targets) {
            this.javaHome = javaHome;
            this.targets = List.copyOf(targets);
        }

        private static Arguments parse(String[] args) {
            String javaHome = null;
            List<String> targets = new ArrayList<>();
            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--java-home" -> {
                        if (++index == args.length) throw new IllegalArgumentException("--java-home requires a path");
                        javaHome = args[index];
                    }
                    case "--versions" -> {
                        if (++index == args.length) throw new IllegalArgumentException("--versions requires one or more versions");
                        while (index < args.length && !args[index].startsWith("--")) targets.add(args[index++]);
                        index--;
                    }
                    case "--help", "-h" -> {
                        System.out.println("Usage: java tools/BuildMatrix.java [--java-home <path>] [--versions <minecraft> ...]");
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[index]);
                }
            }
            return new Arguments(javaHome, targets);
        }
    }
}
