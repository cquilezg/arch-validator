package com.cquilez.arch.infrastructure.adapter

import com.cquilez.arch.application.port.FilesystemPort
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Named
@Singleton
class FilesystemAdapter @Inject constructor() : FilesystemPort {
    override fun exists(path: Path): Boolean = Files.exists(path)

    override fun isDirectory(path: Path): Boolean = Files.isDirectory(path)

    override fun isRegularFile(path: Path): Boolean = Files.isRegularFile(path)

    override fun walk(path: Path): Stream<Path> = Files.walk(path)

    override fun readAllLines(path: Path): List<String> = Files.readAllLines(path)

    override fun readString(path: Path): String = Files.readString(path)
}
