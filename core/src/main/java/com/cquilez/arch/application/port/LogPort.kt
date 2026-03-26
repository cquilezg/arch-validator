package com.cquilez.arch.application.port

interface LogPort {
    fun debug(msg: String)
    fun info(msg: String)
    fun warn(msg: String)
    fun error(msg: String)
}