package com.cquilez.arch.application.port

import java.nio.file.Path
import java.util.stream.Stream

interface FilesystemPort {
    fun exists(path: Path): Boolean
    fun isDirectory(path: Path): Boolean
    fun isRegularFile(path: Path): Boolean
    fun walk(path: Path): Stream<Path>
    fun readAllLines(path: Path): List<String>
    fun readString(path: Path): String
}
