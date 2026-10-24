package dev.blobs.app.data.packages

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class GgufValidatorTest {
    @Test
    fun readsArchitectureContextLengthFromGgufMetadata() {
        val file = File.createTempFile("context", ".gguf")
        try {
            file.writeBytes(ggufFixture(contextLength = 262144))
            assertEquals(262144, GgufValidator.contextLength(file))
        } finally {
            file.delete()
        }
    }

    private fun ggufFixture(contextLength: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        fun u32(value: Int) = output.writeInt(Integer.reverseBytes(value))
        fun u64(value: Long) = output.writeLong(java.lang.Long.reverseBytes(value))
        fun text(value: String) {
            val encoded = value.toByteArray()
            u64(encoded.size.toLong())
            output.write(encoded)
        }

        output.write("GGUF".toByteArray())
        u32(3)
        u64(1)
        u64(4)
        text("general.architecture")
        u32(8)
        text("qwen35")
        text("general.alignment")
        u32(4)
        u32(32)
        text("tokenizer.chat_template")
        u32(8)
        text("chatml")
        text("qwen35.context_length")
        u32(4)
        u32(contextLength)
        text("test.weight")
        u32(1)
        u64(32)
        u32(0)
        u64(0)
        while (bytes.size() % 32 != 0) output.writeByte(0)
        output.write(ByteArray(128))
        return bytes.toByteArray()
    }
}
