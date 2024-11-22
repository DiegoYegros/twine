package dev.sasikanth.rss.reader.resources.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.EvenOdd
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TwineIcons.Nostr: ImageVector
    get() {
        if (nostr != null) {
            return nostr!!
        }
        nostr = Builder(
            name = "Nostr",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 150.0f,
            viewportHeight = 150.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = EvenOdd
            ) {
                moveTo(68.5f, 4.0f)
                lineTo(88.5f, 5.0f)
                lineTo(101.5f, 9.0f)
                quadToRelative(17.5f, 7.5f, 28.5f, 21.5f)
                quadToRelative(15.6f, 16.4f, 16.0f, 48.0f)
                lineTo(143.0f, 95.5f)
                lineTo(137.0f, 109.5f)
                quadToRelative(-6.8f, 12.2f, -17.5f, 20.5f)
                quadToRelative(-16.4f, 15.6f, -48.0f, 16.0f)
                lineTo(54.5f, 143.0f)
                lineTo(40.5f, 137.0f)
                quadToRelative(-12.5f, -7.5f, -21.5f, -18.5f)
                quadToRelative(-8.6f, -10.9f, -13.0f, -26.0f)
                lineTo(4.0f, 71.5f)
                lineTo(7.0f, 54.5f)
                lineTo(13.0f, 40.5f)
                quadToRelative(7.3f, -12.7f, 18.5f, -21.5f)
                lineTo(48.5f, 9.0f)
                lineTo(68.5f, 4.0f)
                close()
                moveTo(104.0f, 18.0f)
                lineTo(102.0f, 19.0f)
                lineTo(99.0f, 23.0f)
                lineTo(99.0f, 34.0f)
                lineTo(105.0f, 46.0f)
                quadToRelative(2.0f, 7.0f, -1.0f, 11.0f)
                lineTo(96.0f, 64.0f)
                lineTo(81.0f, 61.0f)
                lineTo(64.0f, 65.0f)
                lineTo(57.0f, 70.0f)
                lineTo(48.0f, 67.0f)
                quadToRelative(-9.0f, 0.0f, -13.0f, 6.0f)
                lineTo(37.0f, 76.0f)
                quadToRelative(2.0f, -1.0f, 1.0f, 2.0f)
                lineTo(50.0f, 81.0f)
                lineTo(52.0f, 80.0f)
                lineTo(55.0f, 87.0f)
                lineTo(57.0f, 86.0f)
                lineTo(60.0f, 91.0f)
                quadToRelative(1.0f, 2.0f, -1.0f, 1.0f)
                lineTo(56.0f, 95.0f)
                lineTo(55.0f, 102.0f)
                lineTo(49.0f, 115.0f)
                lineTo(47.0f, 117.0f)
                lineTo(37.0f, 121.0f)
                lineTo(33.0f, 126.0f)
                lineTo(51.0f, 124.0f)
                lineTo(62.0f, 101.0f)
                quadToRelative(2.0f, 1.0f, 1.0f, -2.0f)
                lineTo(73.0f, 95.0f)
                lineTo(77.0f, 95.0f)
                quadToRelative(4.0f, 4.0f, 2.0f, 14.0f)
                lineTo(84.0f, 112.0f)
                lineTo(87.0f, 111.0f)
                lineTo(102.0f, 115.0f)
                lineTo(103.0f, 119.0f)
                lineTo(107.0f, 121.0f)
                lineTo(109.0f, 120.0f)
                lineTo(117.0f, 121.0f)
                lineTo(116.0f, 118.0f)
                lineTo(110.0f, 112.0f)
                quadToRelative(-3.0f, 1.0f, -2.0f, -1.0f)
                lineTo(105.0f, 108.0f)
                lineTo(94.0f, 107.0f)
                lineTo(87.0f, 104.0f)
                lineTo(85.0f, 103.0f)
                lineTo(85.0f, 93.0f)
                lineTo(88.0f, 92.0f)
                quadToRelative(8.0f, 2.0f, 12.0f, -1.0f)
                quadToRelative(5.0f, -4.0f, 7.0f, -11.0f)
                lineTo(104.0f, 72.0f)
                lineTo(112.0f, 56.0f)
                lineTo(111.0f, 45.0f)
                lineTo(106.0f, 36.0f)
                lineTo(105.0f, 29.0f)
                lineTo(110.0f, 25.0f)
                quadToRelative(4.0f, 1.0f, 3.0f, -1.0f)
                lineTo(104.0f, 18.0f)
                close()
            }
        }.build()
        return nostr!!
    }

private var nostr: ImageVector? = null