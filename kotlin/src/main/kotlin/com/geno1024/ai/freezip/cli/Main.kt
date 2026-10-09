package com.geno1024.ai.freezip.cli

import com.geno1024.ai.freezip.AssembleOptions
import com.geno1024.ai.freezip.Assembler
import com.geno1024.ai.freezip.CompressionMethod
import com.geno1024.ai.freezip.PackOptions
import com.geno1024.ai.freezip.Packer
import com.geno1024.ai.freezip.StoredEntryFiles
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import kotlin.system.exitProcess

/**
 * Command line front end.
 *
 * ```
 * free-zip pack     <input-file> <output-prefix> [--name N] [--method store|deflate] [--level L] [--comment C]
 * free-zip assemble <output.zip> <entry|dir>...  [--zip64 auto|always|never] [--comment C]
 * free-zip size     <entry|dir>...               [--zip64 auto|always|never] [--comment C]
 * ```
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        usage(System.out)
        return
    }
    when (args[0]) {
        "pack" -> pack(args)
        "assemble" -> assemble(args)
        "size" -> size(args)
        "help", "-h", "--help" -> usage(System.out)
        else -> {
            System.err.println("unknown command: ${args[0]}")
            usage(System.err)
            exitProcess(2)
        }
    }
}

private fun pack(args: Array<String>) {
    val parsed = Parsed.from(args)
    require(parsed.positional.size == 2) { "pack expects <input-file> <output-prefix>" }
    val input = Path.of(parsed.positional[0])
    val prefix = Path.of(parsed.positional[1])
    val method = parseMethod(parsed.option("method", "deflate"))
    val level = if (parsed.has("level")) {
        parsed.option("level", "").toInt()
    } else {
        Deflater.DEFAULT_COMPRESSION
    }
    val options = PackOptions(parsed.optionOrNull("name"), method, level, parsed.option("comment", ""))

    val entry = Packer.pack(input, prefix, options)
    println("packed $input -> ${StoredEntryFiles.key(prefix)}")
    println(
        "  name=${entry.name} method=${entry.method.name.lowercase()} " +
            "compressed=${entry.compressedSize} uncompressed=${entry.uncompressedSize} " +
            "crc=${entry.crc32.toString(16).padStart(8, '0')}",
    )
}

private fun assemble(args: Array<String>) {
    val parsed = Parsed.from(args)
    require(parsed.positional.isNotEmpty()) { "assemble expects <output.zip> <entry|dir>..." }
    val output = Path.of(parsed.positional[0])
    val keys = collectKeys(parsed.positional.drop(1))
    val options = assembleOptions(parsed)

    Files.newOutputStream(output).use { out ->
        Assembler.assemble(keys, out, options)
    }
    println("assembled ${keys.size} entries -> $output (${Files.size(output)} bytes)")
}

private fun size(args: Array<String>) {
    val parsed = Parsed.from(args)
    val keys = collectKeys(parsed.positional)
    println(Assembler.estimateSize(keys, assembleOptions(parsed)))
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
    out.println("  pack     <input-file> <output-prefix> [--name N] [--method store|deflate] [--level L] [--comment C]")
    out.println("  assemble <output.zip> <entry|dir>...  [--zip64 auto|always|never] [--comment C]")
    out.println("  size     <entry|dir>...               [--zip64 auto|always|never] [--comment C]")
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
