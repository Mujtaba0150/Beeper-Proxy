package com.beeperproxy

import android.util.JsonWriter
import java.io.StringWriter

object JsonHelper {

    fun toJsonArray(list: List<Map<String, Any?>>): String {
        val sw = StringWriter()
        val writer = JsonWriter(sw)
        writer.beginArray()
        for (item in list) {
            writeObject(writer, item)
        }
        writer.endArray()
        writer.close()
        return sw.toString()
    }

    fun toJson(obj: Map<String, Any?>): String {
        val sw = StringWriter()
        val writer = JsonWriter(sw)
        writeObject(writer, obj)
        writer.close()
        return sw.toString()
    }

    private fun writeObject(writer: JsonWriter, obj: Map<String, Any?>) {
        writer.beginObject()
        for ((key, value) in obj) {
            writer.name(key)
            writeValue(writer, value)
        }
        writer.endObject()
    }

    private fun writeValue(writer: JsonWriter, value: Any?) {
        when (value) {
            null -> writer.nullValue()
            is String -> writer.value(value)
            is Number -> writer.value(value)
            is Boolean -> writer.value(value)
            is List<*> -> {
                writer.beginArray()
                for (item in value) writeValue(writer, item)
                writer.endArray()
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeObject(writer, value as Map<String, Any?>)
            }
            else -> writer.value(value.toString())
        }
    }
}
