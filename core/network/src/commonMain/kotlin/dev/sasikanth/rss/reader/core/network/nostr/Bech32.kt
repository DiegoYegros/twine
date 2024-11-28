/*
 * Copyright 2024 Sasikanth Miriyampalli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val CHARSET_MAP = CHARSET.withIndex().associate { it.value to it.index }

    fun decode(bech32: String): Result<String> = runCatching {
        val lastOne = bech32.lastIndexOf('1')
        require(lastOne > 0) { "Invalid bech32 string: no separator" }
        val data = bech32.substring(lastOne + 1).lowercase()
            .map { CHARSET_MAP[it] ?: throw IllegalArgumentException("Invalid character: $it") }
        var acc = 0
        var bits = 0
        val result = mutableListOf<Int>()
        for (value in data) {
            acc = (acc shl 5) or value
            bits += 5
            while (bits >= 8) {
                bits -= 8
                result.add((acc shr bits) and 0xFF)
            }
        }
        require(result.size >= 32) { "Invalid data length" }
        result.take(32).joinToString("") {
            it.toString(16).padStart(2, '0')
        }
    }

    fun encode(prefix: String, hex: String): Result<String> = runCatching {
        require(hex.length == 64) { "Hex string must be 32 bytes (64 characters)" }
        val data = hex.chunked(2)
            .map { it.toInt(16) }
        var acc = 0
        var bits = 0
        val converted = mutableListOf<Int>()
        for (value in data) {
            acc = (acc shl 8) or value
            bits += 8
            while (bits >= 5) {
                bits -= 5
                converted.add((acc shr bits) and 31)
            }
        }
        if (bits > 0) {
            converted.add((acc shl (5 - bits)) and 31)
        }
        buildString {
            append(prefix)
            append('1')
            converted.forEach { append(CHARSET[it]) }
        }
    }
}