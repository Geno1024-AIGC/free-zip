package com.geno1024.ai.freezip.cli;

import com.geno1024.ai.freezip.AssembleOptions;
import com.geno1024.ai.freezip.Assembler;
import com.geno1024.ai.freezip.CompressionMethod;
import com.geno1024.ai.freezip.EntryEditor;
import com.geno1024.ai.freezip.EntryOperation;
import com.geno1024.ai.freezip.PackOptions;
import com.geno1024.ai.freezip.Packer;
import com.geno1024.ai.freezip.StoredEntry;
import com.geno1024.ai.freezip.StoredEntryFiles;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.Deflater;

/**
 * Command line front end.
 *
 * <pre>
 * free-zip pack     &lt;input-file&gt; &lt;output-prefix&gt; [--name N] [--method store|deflate] [--level L]
 * free-zip edit     &lt;entry&gt;    [--name N] [--comment C] [--time ISO8601] [--mode OCTAL]
 * free-zip assemble &lt;output.zip&gt; &lt;entry|dir&gt;...    [--zip64 auto|always|never] [--comment C]
 * free-zip size     &lt;entry|dir&gt;...                 [--zip64 auto|always|never] [--comment C]
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage(System.out);
            return;
        }
        switch (args[0]) {
            case "pack" -> pack(args);
            case "edit" -> edit(args);
            case "assemble" -> assemble(args);
            case "size" -> size(args);
            case "help", "-h", "--help" -> usage(System.out);
            default -> {
                System.err.println("unknown command: " + args[0]);
                usage(System.err);
                System.exit(2);
            }
        }
    }

    private static void pack(String[] args) throws IOException {
        Parsed parsed = Parsed.from(args);
        if (parsed.positional.size() != 2) {
            throw new IllegalArgumentException("pack expects <input-file> <output-prefix>");
        }
        Path input = Path.of(parsed.positional.get(0));
        Path prefix = Path.of(parsed.positional.get(1));
        CompressionMethod method = parseMethod(parsed.option("method", "deflate"));
        int level = parsed.has("level")
                ? Integer.parseInt(parsed.option("level", ""))
                : Deflater.DEFAULT_COMPRESSION;
        PackOptions options = new PackOptions(parsed.option("name", null), method, level);

        StoredEntry entry = Packer.pack(input, prefix, options);
        System.out.printf("packed %s -> %s%n", input, StoredEntryFiles.key(prefix));
        System.out.printf("  name=%s method=%s compressed=%d uncompressed=%d crc=%08x%n",
                entry.name(), entry.method().name().toLowerCase(),
                entry.compressedSize(), entry.uncompressedSize(), entry.crc32());
    }

    private static void edit(String[] args) throws IOException {
        Parsed parsed = Parsed.from(args);
        if (parsed.positional.size() != 1) {
            throw new IllegalArgumentException("edit expects <entry>");
        }
        Path key = StoredEntryFiles.key(Path.of(parsed.positional.get(0)));
        if (!StoredEntryFiles.exists(key)) {
            throw new IllegalArgumentException("incomplete stored entry: " + key);
        }

        List<EntryOperation> operations = new ArrayList<>();
        if (parsed.has("comment")) {
            operations.add(new EntryOperation.SetComment(parsed.option("comment", "")));
        }
        if (parsed.has("name")) {
            operations.add(new EntryOperation.Rename(parsed.option("name", "")));
        }
        if (parsed.has("time")) {
            operations.add(new EntryOperation.SetModificationTime(
                    LocalDateTime.parse(parsed.option("time", ""))));
        }
        if (parsed.has("mode")) {
            operations.add(new EntryOperation.SetUnixMode(Integer.parseInt(parsed.option("mode", ""), 8)));
        }
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("edit needs at least one of --comment/--name/--time/--mode");
        }

        StoredEntry entry = EntryEditor.edit(key, operations);
        System.out.printf("edited %s%n", key);
        System.out.printf("  name=%s time=%s comment=%s%n",
                entry.name(), entry.dosTime(), entry.comment());
    }

    private static void assemble(String[] args) throws IOException {
        Parsed parsed = Parsed.from(args);
        if (parsed.positional.isEmpty()) {
            throw new IllegalArgumentException("assemble expects <output.zip> <entry|dir>...");
        }
        Path output = Path.of(parsed.positional.get(0));
        List<Path> keys = collectKeys(parsed.positional.subList(1, parsed.positional.size()));
        AssembleOptions options = assembleOptions(parsed);

        try (OutputStream out = Files.newOutputStream(output)) {
            Assembler.assemble(keys, out, options);
        }
        System.out.printf("assembled %d entries -> %s (%d bytes)%n",
                keys.size(), output, Files.size(output));
    }

    private static void size(String[] args) throws IOException {
        Parsed parsed = Parsed.from(args);
        List<Path> keys = collectKeys(parsed.positional);
        long estimated = Assembler.estimateSize(keys, assembleOptions(parsed));
        System.out.println(estimated);
    }

    private static AssembleOptions assembleOptions(Parsed parsed) {
        String mode = parsed.option("zip64", "auto").toLowerCase();
        AssembleOptions.Zip64Mode zip64 = switch (mode) {
            case "auto" -> AssembleOptions.Zip64Mode.AUTO;
            case "always" -> AssembleOptions.Zip64Mode.ALWAYS;
            case "never" -> AssembleOptions.Zip64Mode.NEVER;
            default -> throw new IllegalArgumentException("invalid --zip64 value: " + mode);
        };
        return new AssembleOptions(zip64, parsed.option("comment", ""));
    }

    private static List<Path> collectKeys(List<String> arguments) throws IOException {
        List<Path> keys = new ArrayList<>();
        for (String argument : arguments) {
            Path path = Path.of(argument);
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(StoredEntryFiles.LOCAL_HEADER_EXTENSION))
                            .sorted()
                            .map(StoredEntryFiles::key)
                            .forEach(keys::add);
                }
            } else {
                keys.add(StoredEntryFiles.key(path));
            }
        }
        for (Path key : keys) {
            if (!StoredEntryFiles.exists(key)) {
                throw new IllegalArgumentException("incomplete stored entry: " + key);
            }
        }
        return keys;
    }

    private static CompressionMethod parseMethod(String value) {
        return switch (value.toLowerCase()) {
            case "store", "stored", "0" -> CompressionMethod.STORED;
            case "deflate", "deflated", "8" -> CompressionMethod.DEFLATED;
            default -> throw new IllegalArgumentException("invalid --method value: " + value);
        };
    }

    private static void usage(java.io.PrintStream out) {
        out.println("free-zip — pre-compressed ZIP assembly");
        out.println();
        out.println("  pack     <input-file> <output-prefix> [--name N] [--method store|deflate] [--level L]");
        out.println("  edit     <entry>                     [--name N] [--comment C] [--time ISO8601] [--mode OCTAL]");
        out.println("  assemble <output.zip> <entry|dir>...  [--zip64 auto|always|never] [--comment C]");
        out.println("  size     <entry|dir>...               [--zip64 auto|always|never] [--comment C]");
        out.println();
        out.println("A stored entry is a key plus three files: <key>.lfh, <key>.data, <key>.cdfh.");
    }

    private static final class Parsed {
        private final List<String> positional = new ArrayList<>();
        private final Map<String, String> options = new LinkedHashMap<>();

        static Parsed from(String[] args) {
            Parsed parsed = new Parsed();
            for (int i = 1; i < args.length; i++) {
                String argument = args[i];
                if (argument.startsWith("--")) {
                    String key = argument.substring(2);
                    String value = "true";
                    int equals = key.indexOf('=');
                    if (equals >= 0) {
                        value = key.substring(equals + 1);
                        key = key.substring(0, equals);
                    } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        value = args[++i];
                    }
                    parsed.options.put(key, value);
                } else {
                    parsed.positional.add(argument);
                }
            }
            return parsed;
        }

        boolean has(String key) {
            return options.containsKey(key);
        }

        String option(String key, String fallback) {
            return options.getOrDefault(key, fallback);
        }
    }
}
