package com.renovation.ledger.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Deck
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.renovation.ledger.domain.taxonomy.TaxonomyIconRef

data class TaxonomyPresetIconEntry(
    val key: String,
    val icon: ImageVector,
    val label: String,
)

/** 预置标签图标：约 16 个常用施工/家装场景图形，覆盖阶段/分类/空间三类标签。 */
val TaxonomyPresetIcons: List<TaxonomyPresetIconEntry> = listOf(
    TaxonomyPresetIconEntry("water_drop", Icons.Outlined.WaterDrop, "水电"),
    TaxonomyPresetIconEntry("construction", Icons.Outlined.Construction, "泥木"),
    TaxonomyPresetIconEntry("format_paint", Icons.Outlined.FormatPaint, "油漆"),
    TaxonomyPresetIconEntry("chair", Icons.Outlined.Chair, "家具"),
    TaxonomyPresetIconEntry("weekend", Icons.Outlined.Weekend, "软装"),
    TaxonomyPresetIconEntry("kitchen", Icons.Outlined.Kitchen, "厨房"),
    TaxonomyPresetIconEntry("bathtub", Icons.Outlined.Bathtub, "卫浴"),
    TaxonomyPresetIconEntry("electric_bolt", Icons.Outlined.ElectricBolt, "电器"),
    TaxonomyPresetIconEntry("home", Icons.Outlined.Home, "全屋"),
    TaxonomyPresetIconEntry("bed", Icons.Outlined.Bed, "卧室"),
    TaxonomyPresetIconEntry("deck", Icons.Outlined.Deck, "阳台"),
    TaxonomyPresetIconEntry("meeting_room", Icons.Outlined.MeetingRoom, "玄关"),
    TaxonomyPresetIconEntry("verified", Icons.Outlined.Verified, "验收"),
    TaxonomyPresetIconEntry("local_shipping", Icons.Outlined.LocalShipping, "主材"),
    TaxonomyPresetIconEntry("lightbulb", Icons.Outlined.Lightbulb, "智能"),
    TaxonomyPresetIconEntry("category", Icons.Outlined.Category, "其他"),
)

fun taxonomyPresetIcon(key: String?): ImageVector? =
    TaxonomyPresetIcons.firstOrNull { it.key == key }?.icon

/** 按 [icon] 渲染：自定义相册图片优先，其次预置图标；都没有则不画任何东西。 */
@Composable
fun TaxonomyIconView(
    icon: TaxonomyIconRef?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val customPath = icon?.iconPath
    if (!customPath.isNullOrBlank()) {
        val bitmap = remember(customPath) {
            runCatching { BitmapFactory.decodeFile(customPath) }.getOrNull()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
            )
            return
        }
    }
    val vector = taxonomyPresetIcon(icon?.iconKey)
    if (vector != null) {
        Icon(imageVector = vector, contentDescription = null, modifier = modifier, tint = tint)
    }
}
