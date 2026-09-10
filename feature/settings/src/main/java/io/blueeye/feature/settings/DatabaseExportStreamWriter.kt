package io.blueeye.feature.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.Writer

internal object DatabaseExportStreamWriter {
    fun writeExport(
        data: DatabaseExportData,
        writer: Writer,
        json: Json,
        trailingRootFragmentProvider: () -> String = { "" },
    ) {
        val metadata = json.encodeToString(DatabaseExportJsonMapper.buildExportMetadata(data))
        val closingBrace = metadata.lastIndexOf('}')
        check(closingBrace >= 0) { "Export metadata is not a JSON object" }

        var prefixEnd = closingBrace
        while (prefixEnd > 0 && metadata[prefixEnd - 1].isWhitespace()) {
            prefixEnd -= 1
        }

        writer.write(metadata, 0, prefixEnd)
        writer.write(",\n  \"devices\": [")
        writeJsonArray(writer, data.devices, json, DatabaseExportJsonMapper::mapDevice)
        writer.write("\n  ],\n  \"signalSamples\": [")
        writeJsonArray(writer, data.samples, json, DatabaseExportJsonMapper::mapSample)
        writer.write("\n  ]")

        val trailingRootFragment = trailingRootFragmentProvider()
        if (trailingRootFragment.isNotBlank()) {
            writer.write(trailingRootFragment)
        }
        writer.write("\n}")
    }

    private fun <T> writeJsonArray(
        writer: Writer,
        values: List<T>,
        json: Json,
        mapper: (T) -> JsonObject,
    ) {
        values.forEachIndexed { index, value ->
            if (index > 0) writer.write(",")
            writer.write("\n    ")
            writer.write(json.encodeToString(mapper(value)))
        }
    }
}
