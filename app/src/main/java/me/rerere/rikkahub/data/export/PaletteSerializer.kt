package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.ui.theme.PaletteSetting

/**
 * 调色板的导出与导入。
 *
 * 外面套一层项目里既有的 [ExportData]（version + type + data），以后加字段不用换格式；
 * 导入时也接受裸的 [PaletteSetting] 对象，方便从剪贴板粘一段手写的配置。
 */
object PaletteSerializer : ExportSerializer<PaletteSetting> {
    override val type = "palette"

    override fun getExportFileName(data: PaletteSetting): String = "l3hub-palette.json"

    override fun export(data: PaletteSetting): ExportData = ExportData(
        type = type,
        data = ExportSerializer.DefaultJson.encodeToJsonElement(data),
    )

    override fun import(context: Context, uri: Uri): Result<PaletteSetting> =
        runCatching { importFromJson(readUri(context, uri)).getOrThrow() }

    /** 从字符串导入，剪贴板和单测共用这一条路径。 */
    fun importFromJson(json: String): Result<PaletteSetting> = runCatching {
        val text = json.trim()
        require(text.isNotEmpty()) { "empty palette json" }
        val packed = runCatching {
            ExportSerializer.DefaultJson.decodeFromString(ExportData.serializer(), text)
        }.getOrNull()
        if (packed != null) {
            require(packed.type == type) { "not a palette: ${packed.type}" }
            // 信封对得上但 data 是空的，同样会解成默认值，一并拦掉
            requirePaletteKeys(packed.data.jsonObject.keys)
            ExportSerializer.DefaultJson.decodeFromJsonElement<PaletteSetting>(packed.data)
        } else {
            requirePaletteKeys(
                ExportSerializer.DefaultJson.decodeFromString<Map<String, JsonElement>>(text).keys
            )
            ExportSerializer.DefaultJson.decodeFromString<PaletteSetting>(text)
        }
    }
}

/**
 * 确认这份 JSON 里确实有调色板的字段。
 *
 * [PaletteSetting] 三个字段全有默认值，加上 `ignoreUnknownKeys`，任何一份别的 JSON
 * 都能"成功"解析成默认值——那会把用户的调色板静默关掉。
 */
private fun requirePaletteKeys(keys: Set<String>) {
    require(keys.any { it == "presetId" || it == "light" || it == "dark" }) {
        "not a palette json, keys=$keys"
    }
}
