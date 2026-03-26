package com.cquilez.arch.infrastructure.adapter

import com.cquilez.arch.application.port.LogPort
import org.apache.maven.plugin.logging.Log
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Named
@Singleton
class LogAdapter @Inject constructor() : LogPort {
    private var log: Log? = null

    fun bind(log: Log) {
        this.log = log
    }

    private fun requireLog(): Log {
        return log ?: throw IllegalStateException("LogAdapter not bound to a Maven Log")
    }

    override fun debug(msg: String) {
        requireLog().debug(msg)
    }

    override fun info(msg: String) {
        requireLog().info(msg)
    }

    override fun warn(msg: String) {
        requireLog().warn(msg)
    }

    override fun error(msg: String) {
        requireLog().error(msg)
    }
}
