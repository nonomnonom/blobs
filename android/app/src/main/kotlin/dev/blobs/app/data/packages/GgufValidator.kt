package dev.blobs.app.data.packages

import java.io.File
import java.io.RandomAccessFile

object GgufValidator {
    fun validate(file: File) = RandomAccessFile(file, "r").use { input ->
        fun u32(): Long = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()))
        fun u64(): Long = java.lang.Long.reverseBytes(input.readLong()).also { require(it >= 0) }
        fun skip(bytes: Long) {
            require(bytes >= 0 && bytes <= input.length() - input.filePointer)
            input.seek(input.filePointer + bytes)
        }
        fun text(max: Int): String {
            val length = u64()
            require(length <= max)
            val bytes = ByteArray(length.toInt())
            input.readFully(bytes)
            return bytes.toString(Charsets.UTF_8)
        }
        fun value(type: Long, depth: Int = 0) {
            require(depth <= 2)
            when (type.toInt()) {
                0, 1, 7 -> skip(1)

                2, 3 -> skip(2)

                4, 5, 6 -> skip(4)

                10, 11, 12 -> skip(8)

                8 -> skip(u64())

                9 -> {
                    val element = u32()
                    val count = u64()
                    require(count <= 10_000_000)
                    repeat(count.toInt()) { value(element, depth + 1) }
                }

                else -> error("GGUF_METADATA_TYPE")
            }
            require(input.filePointer <= 256L * 1024 * 1024)
        }
        require(u32() == 0x46554747L && u32() in 2..3)
        val tensors = u64()
        val metadata = u64()
        require(tensors in 1..100_000 && metadata in 1..100_000)
        var alignment = 32L
        var architecture = ""
        var template = false
        repeat(metadata.toInt()) {
            val key = text(1024)
            val type = u32()
            when (key) {
                "general.architecture" -> {
                    require(type == 8L)
                    architecture = text(128)
                }

                "general.alignment" -> {
                    require(type == 4L)
                    alignment = u32()
                }

                "tokenizer.chat_template" -> {
                    require(type == 8L)
                    skip(u64())
                    template = true
                }

                else -> value(type)
            }
        }
        require(architecture.isNotBlank() && template)
        require(alignment in 1..4096 && alignment and (alignment - 1) == 0L)
        val extents = ArrayList<Pair<Long, Long>>(tensors.toInt())
        repeat(tensors.toInt()) {
            text(1024)
            val dimensions = u32()
            require(dimensions in 1..4)
            var elements = 1L
            repeat(dimensions.toInt()) {
                val dimension = u64()
                require(dimension in 1..10_000_000)
                elements = Math.multiplyExact(elements, dimension)
            }
            val type = u32()
            val offset = u64()
            val (block, bytes) = when (type.toInt()) {
                0 -> 1 to 4
                1, 30 -> 1 to 2
                2 -> 32 to 18
                3 -> 32 to 20
                6 -> 32 to 22
                7 -> 32 to 24
                8 -> 32 to 34
                10 -> 256 to 84
                11 -> 256 to 110
                12 -> 256 to 144
                13 -> 256 to 176
                14 -> 256 to 210
                else -> error("GGUF_QUANTIZATION")
            }
            require(elements % block == 0L && offset % alignment == 0L)
            extents += offset to Math.multiplyExact(elements / block, bytes.toLong())
        }
        val dataStart = (input.filePointer + alignment - 1) / alignment * alignment
        var previousEnd = 0L
        for ((offset, bytes) in extents.sortedBy { it.first }) {
            require(offset >= previousEnd)
            val end = Math.addExact(offset, bytes)
            require(end <= input.length() - dataStart)
            previousEnd = end
        }
    }
}
