package com.geno1024.ai.freezip.cli

import com.geno1024.ai.freezip.AssembleOptions
import com.geno1024.ai.freezip.Assembler
import com.geno1024.ai.freezip.CompressionMethod
import com.geno1024.ai.freezip.EntryEditor
import com.geno1024.ai.freezip.EntryOperation
import com.geno1024.ai.freezip.PackOptions
import com.geno1024.ai.freezip.Packer
import com.geno1024.ai.freezip.StoredEntry
import com.geno1024.ai.freezip.StoredEntryFiles
import com.geno1024.ai.freezip.Verifier
import com.geno1024.ai.freezip.ZipFormat
import java.io.BufferedReader
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.Deflater
import kotlin.system.exitProcess

/**
 * Command line front end.
 *
 * ```
 * free-zip pack     <input-file> <output-prefix> [--name N] [--method store|deflate] [--level L]
 * free-zip edit     <entry>                     [--name N] [--comment C] [--time ISO8601] [--mode OCTAL]
 * free-zip verify   <entry|dir>...
 * free-zip info     <entry|dir>...
 * free-zip assemble <output.zip> <entry|dir>...  [--zip64 auto|always|never] [--comment C]
 * free-zip size     <entry|dir>...               [--zip64 auto|always|never] [--comment C]
 * free-zip repl
 * ```
 */
fun main(args: Array<String>) {
    try {
        execute(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("error: ${e.message}")
        usage(System.err)
        exitProcess(2)
    } catch (e: Exception) {
        System.err.println("error: ${e.message}")
        exitProcess(1)
    }
}

private fun execute(args: Array<String>) {
    if (args.isEmpty()) {
        usage(System.out)
        return
    }
    when (args[0]) {
        "pack" -> pack(args)
        "edit" -> edit(args)
        "verify" -> verify(args)
        "info" -> info(args)
        "assemble" -> assemble(args)
        "size" -> size(args)
        "repl" -> repl()
        "help", "-h", "--help" -> usage(System.out)
        else -> throw IllegalArgumentException("unknown command: ${args[0]}")
    }
}

private fun pack(args: Array<String>) {
    val parsed = Parsed.from(args)
    require(parsed.positional.size == 2) { "pack expects <input-file> <output-prefix>" }
    val input = Path.of(parsed.positional[0])
    val prefix = Path.of(parsed.positional[1])
    prefix.parent?.let { Files.createDirectories(it) }
    val method = parseMethod(parsed.option("method", "deflate"))
    val level = if (parsed.has("level")) {
        parsed.option("level", "").toInt()
    } else {
        Deflater.DEFAULT_COMPRESSION
    }
    val options = PackOptions(parsed.optionOrNull("name"), method, level)

    val entry = Packer.pack(input, prefix, options)
    println("packed $input -> ${StoredEntryFiles.key(prefix)}")
    println(
        "  name=${entry.name} method=${entry.method.name.lowercase()} " +
            "compressed=${entry.compressedSize} uncompressed=${entry.uncompressedSize} " +
            "crc=${entry.crc32.toString(16).padStart(8, '0')}",
    )
}

private fun edit(args: Array<String>) {
    val parsed = Parsed.from(args)
    require(parsed.positional.size == 1) { "edit expects <entry>" }
    val key = StoredEntryFiles.key(Path.of(parsed.positional[0]))
    require(StoredEntryFiles.exists(key)) { "incomplete stored entry: $key" }

    val operations = ArrayList<EntryOperation>()
    if (parsed.has("comment")) {
        operations.add(EntryOperation.SetComment(parsed.option("comment", "")))
    }
    if (parsed.has("name")) {
        operations.add(EntryOperation.Rename(parsed.option("name", "")))
    }
    if (parsed.has("time")) {
        operations.add(EntryOperation.SetModificationTime(LocalDateTime.parse(parsed.option("time", ""))))
    }
    if (parsed.has("mode")) {
        operations.add(EntryOperation.SetUnixMode(parsed.option("mode", "").toInt(8)))
    }
    require(operations.isNotEmpty()) {
        "edit needs at least one of --comment/--name/--time/--mode"
    }

    val entry = EntryEditor.edit(key, operations)
    println("edited $key")
    println("  name=${entry.name} time=${entry.dosTime} comment=${entry.comment}")
}

private fun verify(args: Array<String>) {
    val parsed = Parsed.from(args)
    val keys = collectKeys(parsed.positional)
    var failed = 0
    for (key in keys) {
        try {
            Verifier.verify(key)
            println("ok $key")
        } catch (e: IOException) {
            failed++
            System.err.println("FAIL $key: ${e.message}")
        }
    }
    if (failed > 0) {
        throw IOException("$failed of ${keys.size} entries failed verification")
    }
}

private fun info(args: Array<String>) {
    val parsed = Parsed.from(args)
    val keys = collectKeys(parsed.positional)
    for (key in keys) {
        describeEntry(key, EntryEditor.load(key))
    }
}

private fun describeEntry(key: Path, entry: StoredEntry) {
    val mode = if (entry.hostSystem == ZipFormat.HOST_UNIX && entry.externalAttributes != 0) {
        " mode=${(entry.externalAttributes ushr 16).toString(8).padStart(4, '0')}"
    } else {
        ""
    }
    println(key)
    println(
        "  name=${entry.name} method=${entry.method.name.lowercase()} " +
            "compressed=${entry.compressedSize} uncompressed=${entry.uncompressedSize} " +
            "crc=${entry.crc32.toString(16).padStart(8, '0')}",
    )
    println(
        "  modified=${entry.dosTime.toLocalDateTime()} " +
            "host=${if (entry.hostSystem == ZipFormat.HOST_UNIX) "unix" else "dos"}$mode " +
            "comment=${entry.comment ?: ""}",
    )
}

private fun assemble(args: Array<String>) {
    val parsed = Parsed.from(args)
    require(parsed.positional.isNotEmpty()) { "assemble expects <output.zip> <entry|dir>..." }
    val output = Path.of(parsed.positional[0])
    val keys = collectKeys(parsed.positional.drop(1))
    val options = assembleOptions(parsed)

    Files.newOutputStream(output).use { raw ->
        BufferedOutputStream(raw).use { out ->
            Assembler.assemble(keys, out, options)
        }
    }
    println("assembled ${keys.size} entries -> $output (${Files.size(output)} bytes)")
}

private fun size(args: Array<String>) {
    val parsed = Parsed.from(args)
    val keys = collectKeys(parsed.positional)
    println(Assembler.estimateSize(keys, assembleOptions(parsed)))
}

private fun repl() {
    println("free-zip repl — type a command (help for usage, exit to quit)")
    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    while (true) {
        print("free-zip> ")
        System.out.flush()
        val line = reader.readLine() ?: break
        val argv = tokenize(line.trim())
        if (argv.isEmpty()) continue
        when (argv[0]) {
            "exit", "quit" -> return
            "repl" -> System.err.println("error: already in the repl")
            else -> {
                try {
                    execute(argv)
                } catch (e: Exception) {
                    System.err.println("error: ${e.message}")
                }
            }
        }
    }
}

/** Splits a REPL line into argv tokens, honoring single/double quotes. */
private fun tokenize(line: String): Array<String> {
    val tokens = ArrayList<String>()
    var i = 0
    while (i < line.length) {
        while (i < line.length && line[i].isWhitespace()) i++
        if (i >= line.length) break
        val quote: Char? = if (line[i] == '"' || line[i] == '\'') line[i] else null
        if (quote != null) i++
        val token = StringBuilder()
        while (i < line.length && (if (quote != null) line[i] != quote else !line[i].isWhitespace())) {
            token.append(line[i++])
        }
        if (quote != null && i < line.length) i++
        tokens.add(token.toString())
    }
    return tokens.toTypedArray()
}

private fun assembleOptions(parsed: Parsed): AssembleOptions {
    val mode = parsed.option("zip64", "auto").lowercase()
    val zip64 = when (mode) {
        "auto" -> AssembleOptions.Zip64Mode.AUTO
        "always" -> AssembleOptions.Zip64Mode.ALWAYS
        "never" -> AssembleOptions.Zip64Mode.NEVER
        else -> throw IllegalArgumentException("invalid --zip64 value: $mode")
    }
    return AssembleOptions(zip64, parsed.option("comment", ""))
}

private fun collectKeys(arguments: List<String>): List<Path> {
    val keys = ArrayList<Path>()
    for (argument in arguments) {
        val path = Path.of(argument)
        if (Files.isDirectory(path)) {
            Files.list(path).use { stream ->
                stream
                    .filter { it.fileName.toString().endsWith(StoredEntryFiles.LOCAL_HEADER_EXTENSION) }
                    .sorted()
                    .map(StoredEntryFiles::key)
                    .forEach(keys::add)
            }
        } else {
            keys.add(StoredEntryFiles.key(path))
        }
    }
    for (key in keys) {
        require(StoredEntryFiles.exists(key)) { "incomplete stored entry: $key" }
    }
    return keys
}

private fun parseMethod(value: String): CompressionMethod = when (value.lowercase()) {
    "store", "stored", "0" -> CompressionMethod.STORED
    "deflate", "deflated", "8" -> CompressionMethod.DEFLATED
    else -> throw IllegalArgumentException("invalid --method value: $value")
}

private fun usage(out: PrintStream) {
    out.println("free-zip — pre-compressed ZIP assembly")
    out.println()
    out.println("  pack     <input-file> <output-prefix> [--name N] [--method store|deflate] [--level L]")
    out.println("  edit     <entry>                     [--name N] [--comment C] [--time ISO8601] [--mode OCTAL]")
    out.println("  verify   <entry|dir>...              checks size and CRC of stored payloads")
    out.println("  info     <entry|dir>...              shows entry metadata")
    out.println("  assemble <output.zip> <entry|dir>...  [--zip64 auto|always|never] [--comment C]")
    out.println("  size     <entry|dir>...               [--zip64 auto|always|never] [--comment C]")
    out.println("  repl                                  interactive session")
    out.println()
    out.println("A stored entry is a key plus three files: <key>.lfh, <key>.data, <key>.cdfh.")
}

private class Parsed {
    val positional = ArrayList<String>()
    private val options = LinkedHashMap<String, String>()

    fun has(key: String): Boolean = options.containsKey(key)

    fun option(key: String, fallback: String): String = options.getOrDefault(key, fallback)

    fun optionOrNull(key: String): String? = options[key]

    companion object {
        fun from(args: Array<String>): Parsed {
            val parsed = Parsed()
            var i = 1
            while (i < args.size) {
                val argument = args[i]
                if (argument.startsWith("--")) {
                    var key = argument.substring(2)
                    var value = "true"
                    val equals = key.indexOf('=')
                    if (equals >= 0) {
                        value = key.substring(equals + 1)
                        key = key.substring(0, equals)
                    } else if (i + 1 < args.size && !args[i + 1].startsWith("--")) {
                        value = args[++i]
                    }
                    parsed.options[key] = value
                } else {
                    parsed.positional.add(argument)
                }
                i++
            }
            return parsed
        }
    }
}