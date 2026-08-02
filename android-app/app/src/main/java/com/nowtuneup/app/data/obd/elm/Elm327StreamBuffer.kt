package com.nowtuneup.app.data.obd.elm

class Elm327StreamBuffer(
    private val maximumCharacters: Int = 65_536,
) {
    private val buffer = StringBuilder()

    @Synchronized
    fun append(chunk: String) {
        require(chunk.length + buffer.length <= maximumCharacters) {
            "ELM327 response buffer exceeded safe limit"
        }
        buffer.append(chunk)
    }

    @Synchronized
    fun pollResponse(): String? {
        val promptIndex = buffer.indexOf(">")
        if (promptIndex < 0) return null
        val response = buffer.substring(0, promptIndex + 1)
        buffer.delete(0, promptIndex + 1)
        return response
    }

    @Synchronized
    fun clear() {
        buffer.clear()
    }

    @Synchronized
    fun pendingCharacters(): Int = buffer.length
}
