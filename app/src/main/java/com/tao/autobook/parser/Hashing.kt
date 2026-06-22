package com.tao.autobook.parser

import java.security.MessageDigest

fun stableSha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
