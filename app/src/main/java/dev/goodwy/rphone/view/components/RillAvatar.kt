package dev.goodwy.rphone.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.isEmoji
import dev.goodwy.rphone.controller.util.isLetter
import org.koin.compose.koinInject
import kotlin.math.abs

//private val avatarColors3 = listOf(
//    Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2),
//    Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF29B6F6), Color(0xFF26C6DA),
//    Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
//    Color(0xFFFFEE58), Color(0xFFFFCA28), Color(0xFFFFA726), Color(0xFFFF7043)
//)
//
//private val avatarColors2 = listOf(
//    Color(0xFFFF8C42), Color(0xFFFFB7C5), Color(0xFF1E5631), Color(0xFF4A148C),
//    Color(0xFF90CAF9), Color(0xFFC0CA33), Color(0xFFE1BEE7), Color(0xFFCE93D8),
//    Color(0xFF1A237E), Color(0xFFFFB74D), Color(0xFFF48FB1), Color(0xFFE53935),
//    Color(0xFFE64A19), Color(0xFF3949AB), Color(0xFFA5D6A7), Color(0xFF00BCD4)
//)

// Google Message
private val avatarColors = listOf(
    Color(0xFF00D0EA), Color(0xFFFF5B55), Color(0xFFFFC600), Color(0xFFBC56FF),
    Color(0xFFFF891D), Color(0xFF26BC6D), Color(0xFFFF54BB)
)

val LocalAvatarPreferences = androidx.compose.runtime.compositionLocalOf { AvatarPreferences() }

@Composable
fun RillAvatar(
    name: String,
    photoUri: String? = null,
    icon: ImageVector? = null,
    /** Optional explicit tint colour for vector icon tiles. */
    iconContainerColor: Color? = null,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val avatarPreferences = LocalAvatarPreferences.current
    val showPicture = avatarPreferences.showPicture
    val showFirstLetter = avatarPreferences.showFirstLetter
    val colorfulAvatars = avatarPreferences.colorful
    val secondaryColorAvatars = avatarPreferences.secondaryColor
    val googleContactColorAvatars = avatarPreferences.googleContactColor
    val avatarFrame = avatarPreferences.frame

    val hasName  = name.trim().isNotEmpty()
    val isLetter  = name.trim().take(1).isLetter() || name.take(2).isEmoji()
    val colorKey = if (hasName) name else "unknown_caller"

    val (backgroundColor, contentColor) = when {
        iconContainerColor != null -> iconContainerColor.copy(alpha = 0.18f) to iconContainerColor
        colorfulAvatars -> avatarColors[abs(colorKey.hashCode()) % avatarColors.size] to if (avatarFrame) MaterialTheme.colorScheme.onSurface else Color.White
        googleContactColorAvatars -> Color(0xFFFFAFAB) to Color(0xFF690A08)
        secondaryColorAvatars -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = modifier
            .then(
                if (avatarFrame) Modifier
                    .drawBehind {
                        val borderWidth = size.width * 0.09f
                        drawOutline(
                            outline = shape.createOutline(size, layoutDirection, this),
                            color = contentColor,
                            style = Stroke(width = borderWidth)
                        )
                    }
                else Modifier
            )
            .background(backgroundColor, shape)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        val letterFontSize  = 22.sp
        val iconSize        = 28.dp
        val placeholderSize = 38.dp

        when {
            showPicture && !photoUri.isNullOrEmpty() -> {
                AsyncImage(
                    model = photoUri,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            icon != null -> {
                Icon(imageVector = icon, contentDescription = name, tint = contentColor, modifier = Modifier.size(iconSize))
            }
            showFirstLetter && hasName && isLetter -> {
                val emoji = name.take(2)
                val letter = if (emoji.isEmoji()) emoji else name.trim().take(1).uppercase()
                Text(
                    text = letter,
                    fontSize = letterFontSize,
                    color = contentColor,
                    lineHeight = letterFontSize
                )
            }
            else -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_person),
                    contentDescription = name, tint = contentColor,
                    modifier = Modifier.size(placeholderSize)
                )
            }
        }
    }
}

data class AvatarPreferences(
    val showPicture: Boolean = true,
    val showFirstLetter: Boolean = true,
    val colorful: Boolean = true,
    val secondaryColor: Boolean = false,
    val googleContactColor: Boolean = false,
    val frame: Boolean = false
)
