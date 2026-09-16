package com.zhou.kebiao.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 课表持久化：整个课表存成一个 JSON 文件。
 * 不用 Room —— 数据量只有几十条，JSON 更容易排查问题。
 */
class TimetableStore(private val file: File) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun load(): Timetable? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Timetable>(file.readText()) }.getOrNull()
    }

    fun save(timetable: Timetable) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(timetable))
    }

    fun exists(): Boolean = file.exists()

    fun delete() {
        file.delete()
    }
}
