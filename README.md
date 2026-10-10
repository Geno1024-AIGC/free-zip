# free-zip

预压缩 ZIP 按需拼装工具：把“压缩”与“打包”拆成两个独立过程，让同一份压缩数据可以被反复、快速地组装成完整的 ZIP。

拼装过程只做字节复制，不再重新压缩，因此又快又省 CPU。

## 两个过程

```mermaid
flowchart LR
    file["文件"] -->|pack| store["<key>.lfh<br/><key>.data<br/><key>.cdfh"]
    store -->|assemble| zip["完整 .zip"]
    keys["若干 key"] -->|assemble| zip
```

- `pack`：压缩单个文件一次，落盘为三件套。
- `edit`：原地修改已打包条目的元数据（注释、名字、时间、Unix 模式），不重新压缩、不动 `.data`。
- `verify`：核对条目的数据大小与 CRC-32 是否与头部一致，发现损坏即失败。
- `assemble`：按顺序逐字节复制每个条目的本地头与数据，最后重建中央目录，追加结尾记录。整个过程顺序写出，条目本地头偏移在写中央目录时才知道——这正是“预存头部”方案成立的关键。

### 三件套命名

每个条目由 *key* 标识，对应三个文件：

| 文件 | 含义 | 规范章节 |
| --- | --- | --- |
| `<key>.lfh` | Local File Header | APPNOTE.TXT §4.3.7 |
| `<key>.data` | 文件数据（已压缩载荷） | APPNOTE.TXT §4.3.8 |
| `<key>.cdfh` | Central Directory File Header 模板，写盘时本地头偏移留 0，assemble 时回填 | APPNOTE.TXT §4.3.12 |

## 模块

| 模块 | 语言 | 入口类 | 产物 |
| --- | --- | --- | --- |
| `:java` | Java 21 | `com.geno1024.ai.freezip.cli.Main` | `java/build/libs/free-zip-java-all.jar` |
| `:kotlin` | Kotlin 2.4 / JVM 21 | `com.geno1024.ai.freezip.cli.MainKt` | `kotlin/build/libs/free-zip-kotlin-all.jar` |

两个模块 API 与产物格式完全一致、可互相混用：Java `pack` 出来的条目可以被 Kotlin `assemble`，反之亦然。

## 构建与运行

```bash
./gradlew build                 # 编译 + 测试两个模块
./gradlew :java:fatJar :kotlin:fatJar   # 生成自包含可执行 jar
./gradlew :java:test :kotlin:test       # 仅测试
```

GitHub Actions 工作流（`.github/workflows/ci.yml`）在每次 push 与 PR 上运行 `./gradlew build`。

运行（以 Java 版为例，Kotlin 版把 jar 名换成 `free-zip-kotlin-all.jar`）：

```bash
java -jar java/build/libs/free-zip-java-all.jar \
  pack hello.txt store/hello.txt --method deflate
java -jar java/build/libs/free-zip-java-all.jar \
  edit store/hello.txt --comment "greeting"
java -jar java/build/libs/free-zip-java-all.jar verify store
java -jar java/build/libs/free-zip-java-all.jar info store
java -jar java/build/libs/free-zip-java-all.jar \
  assemble out.zip store --zip64 auto
java -jar java/build/libs/free-zip-java-all.jar size store
```

### CLI

```
free-zip pack     <input-file> <output-prefix> [--name N] [--method store|deflate] [--level L]
free-zip edit     <entry>                     [--name N] [--comment C] [--time ISO8601] [--mode OCTAL]
free-zip verify   <entry|dir>...
free-zip info     <entry|dir>...
free-zip assemble <output.zip> <entry|dir>...  [--zip64 auto|always|never] [--comment C]
free-zip size     <entry|dir>...               [--zip64 auto|always|never] [--comment C]
free-zip repl
```

- `assemble` / `size` / `verify` / `info` 接受条目 key，或包含 `*.lfh` 的目录（目录内按文件名排序）。
- `size` 精确计算 `assemble` 将产生的字节数，可用于 HTTP `Content-Length`，且不读取任何载荷。
- `verify` 逐条做完整性检查：先核对 `<key>.data` 字节数与头部“压缩大小”一致；再核对 CRC-32（存储条目直接计算，deflate 条目边解压边计算——载荷是原始 deflate 流，§4.3.8）；任何失败都以非零退出码收尾。POST 出去前先 `verify` 一轮，就能拦住损坏的 `.data`。
- `info` 只读元数据（名字、方法、大小、CRC、修改时间、host、Unix 模式、注释），不做完整校验，适合快速看字段。
- `repl` 进入交互会话：逐行执行同样的子命令（参数支持引号包裹），`exit` / `quit` 退出，`help` 查看用法；出错会打印错误并继续，方便反复“编辑一下、看一眼、再核对”。
- `--zip64`：`auto`（仅在字段溢出时写 Zip64 记录）、`always`（总是写，并使经典 EOCD 字段饱和）、`never`。
- `--comment`（`assemble` / `size`）：写入**归档注释**（End of Central Directory record，§4.3.16），以 UTF-8 编码，规范上限 65535 字节。**条目注释**另走 `edit --comment`（见下），不放在 `pack` 里，因为注释与压缩无关、属于纯元数据改动。

### 编辑条目（`edit`）

`edit` 原地修改一个已打包条目的元数据：读取 `<key>.lfh` 与 `<key>.cdfh`，套用若干**操作**，再把两个头写回。它**从不触碰 `<key>.data`**，因此不需要重新压缩，sizes 与 CRC 保持不变。

| 操作 | CLI | 影响范围 | 规范章节 |
| --- | --- | --- | --- |
| `SetComment` | `--comment C` | 仅中央目录 | §4.3.12 |
| `Rename` | `--name N` | 本地头 + 中央目录 | §4.3.7 / §4.3.12 |
| `SetModificationTime` | `--time ISO8601` | 本地头 + 中央目录 | §4.4.6 |
| `SetUnixMode` | `--mode OCTAL` | 仅中央目录（external attributes，同时置 host 为 UNIX） | §4.4.15 |

操作在代码里是 `StoredEntry -> StoredEntry` 的纯函数（`EntryOperation`），`EntryEditor` 负责加载、按顺序折叠操作、写回；`.cdfh` 始终以本地头偏移 0 的模板形式保存，真实偏移在 assemble 时回填。

```bash
java -jar .../free-zip-java-all.jar edit store/hello.txt \
  --name greeting.txt --comment "hi" --time 2001-02-03T04:05:06 --mode 755
```

## Zip64 说明

APPNOTE.TXT §4.5.3 的 Zip64 扩展信息字段（header id `0x0001`）按 16/32 位字段各自溢出独立判定：

- 本地头中原始大小与压缩大小**必须成对**出现在 Zip64 extra 中（§4.5.3 规定）。
- 中央目录中，原始大小、压缩大小、本地头偏移、磁盘号各自按需出现。
- 当任一字段溢出时，条目的“version needed to extract”提升到 4.5（`VERSION_NEEDED_ZIP64`）。

结尾记录的字段定义见 §4.3.14（Zip64 EOCD record）、§4.3.15（Zip64 EOCD locator）、§4.3.16（经典 EOCD）。

`--zip64 never` 遇到**必须**写 Zip64 的归档（条目数达到 65535，或中央目录大小 / 偏移超过 4 GiB）会直接报错并拒绝组装，而不是静默产出读不出来的文件；`size` 在相同参数下同样报错，让调用方在拼装前就能发现。

## 输入校验

名字、注释在头部里都是 16 位长度字段，所以条目名字、条目注释、归档注释都受 **65535 UTF-8 字节**上限约束。这些校验在构造 `StoredEntry` / `AssembleOptions` 时抛出 `IllegalArgumentException`，把“拼出读不动的归档”拖到最早一刻。另外，`assemble` 会拒绝**重名条目**——ZIP 里重名条目会让解压器的查找行为未定义，徒增成本。

相关加固（`assemble` / `size` / `verify` / 长度校验）在两个模块各有测试覆盖（`HardeningTest`）。

## 作为库使用

直接依赖模块的源码即可（`:java` 或 `:kotlin`，产物完全一致）：

Java：

```java
import com.geno1024.ai.freezip.Assembler;
import com.geno1024.ai.freezip.AssembleOptions;
import com.geno1024.ai.freezip.EntryEditor;
import com.geno1024.ai.freezip.EntryOperation;
import com.geno1024.ai.freezip.PackOptions;
import com.geno1024.ai.freezip.Packer;
import com.geno1024.ai.freezip.Verifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

Path prefix = Path.of("store/hello.txt");
Packer.pack(Path.of("hello.txt"), prefix, PackOptions.defaults("hello.txt"));
EntryEditor.edit(prefix, List.of(new EntryOperation.SetComment("hi")));
Verifier.verify(prefix);

Path zip = Path.of("out.zip");
try (var out = Files.newOutputStream(zip)) {
    Assembler.assemble(List.of(prefix), out, AssembleOptions.defaults());
}
long bytes = Assembler.estimateSize(List.of(prefix), AssembleOptions.defaults());
```

Kotlin：

```kotlin
import com.geno1024.ai.freezip.AssembleOptions
import com.geno1024.ai.freezip.Assembler
import com.geno1024.ai.freezip.EntryEditor
import com.geno1024.ai.freezip.EntryOperation
import com.geno1024.ai.freezip.PackOptions
import com.geno1024.ai.freezip.Packer
import com.geno1024.ai.freezip.Verifier
import java.nio.file.Files
import java.nio.file.Path

val prefix = Path.of("store/hello.txt")
Packer.pack(Path.of("hello.txt"), prefix, PackOptions.defaults("hello.txt"))
EntryEditor.edit(prefix, listOf(EntryOperation.SetComment("hi")))
Verifier.verify(prefix)

Files.newOutputStream(Path.of("out.zip")).use { Assembler.assemble(listOf(prefix), it, AssembleOptions.defaults()) }
val bytes = Assembler.estimateSize(listOf(prefix), AssembleOptions.defaults())
```

## 代码约定

`--zip64 never` 遇到**必须**写 Zip64 的归档（条目数达到 65535，或中央目录大小 / 偏移超过 4 GiB）会直接报错并拒绝组装，而不是静默产出读不出来的文件；`size` 在相同参数下同样报错，让调用方在拼装前就能发现。

## 代码约定

- 代码文件名与类名尽量贴近 APPNOTE.TXT 术语（如 `LocalFileHeader`、`CentralDirectoryFileHeader`、`GeneralPurposeBitFlag`、`MsDosDateTime`、`Zip64ExtendedInformationExtraField`）。
- 与 ZIP 结构相关的代码带简短注释指向规范章节（如 `§4.3.7`）。
- 所有整数字段按 §4.4.1.1 以小端序编码，统一由 `LittleEndian` 处理。
- 不设置通用位标志第 3 位（data descriptor），因此无需尾随描述符。

## 目录

```
build.gradle.kts        根脚本（含 build_count.txt 构建计数）
build_count.txt         每次 Gradle 配置阶段 +1，随提交一起提交
.github/workflows/ci.yml  CI：每次 push / PR 运行 ./gradlew build
java/                   Java 实现与测试
kotlin/                 Kotlin 实现与测试
```
