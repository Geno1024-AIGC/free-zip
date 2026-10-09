// Root build script. Module-specific configuration lives in each module.
//
// The only global side effect is the build counter: every time Gradle
// configures this project (i.e. every build invocation), `build_count.txt`
// is incremented by one. The file is tracked by Git so the count survives
// across checkouts and is baked into each release.
//
// Mirrors the convention used across the sibling projects: the value read
// during configuration is the number of *previously* completed builds; the
// file is then bumped so the next build sees a higher number.

val buildCountFile = layout.projectDirectory.file("build_count.txt").asFile
val completedBuilds = buildCountFile
    .takeIf { it.exists() }
    ?.readText()
    ?.trim()
    ?.toIntOrNull()
    ?: 0
buildCountFile.writeText("${completedBuilds + 1}\n")

extra["buildCount"] = completedBuilds
