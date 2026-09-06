package com.regtho.musicor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform