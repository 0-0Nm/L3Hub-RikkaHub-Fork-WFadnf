package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
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
            ExportSerializer.DefaultJson.decodeFromJsonElement<PaletteSetting>(packed.data)
        } else {
            ExportSerializer.DefaultJson.decodeFromString<PaletteSetting>(text)
        }
    }
}
