package com.example.senseclockv1

import android.content.res.AssetManager
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class IconFont(val family: FontFamily, val codes: Map<String, String>)

private object QWeatherFont {
    private var cached: IconFont? = null

    @Synchronized
    fun load(assets: AssetManager): IconFont = cached ?: run {
        val mapping = assets.open("qweather/qweather-icons.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        IconFont(
            FontFamily(Typeface.createFromAsset(assets, "qweather/qweather-icons.ttf")),
            mapping.keys().asSequence().associateWith {
                String(Character.toChars(mapping.getInt(it)))
            }
        )
    }.also { cached = it }
}

@Composable
internal fun rememberQWeatherIconFont(): IconFont? {
    val assets = LocalContext.current.applicationContext.assets
    val font by produceState<IconFont?>(null, assets) {
        value = withContext(Dispatchers.IO) { QWeatherFont.load(assets) }
    }
    return font
}

@Composable
internal fun QWeatherIcon(
    font: IconFont?, code: String, description: String, size: TextUnit = 32.sp
) {
    Text(
        text = font?.let { it.codes[code] ?: it.codes["999"] }.orEmpty(),
        fontFamily = font?.family,
        fontSize = size,
        lineHeight = size,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
    )
}
