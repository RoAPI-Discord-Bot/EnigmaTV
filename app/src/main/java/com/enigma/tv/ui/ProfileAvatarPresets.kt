package com.enigma.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Netflix-style colored icon avatars (not photos). */
data class ProfileAvatarPreset(
    val label: String,
    val color: Color,
    val icon: ImageVector
)

object ProfileAvatarPresets {
    val all: List<ProfileAvatarPreset> = listOf(
        ProfileAvatarPreset("Person", Color(0xFF546E7A), Icons.Default.Person),
        ProfileAvatarPreset("Movies", Color(0xFF5C6BC0), Icons.Default.Movie),
        ProfileAvatarPreset("TV", Color(0xFF00897B), Icons.Default.LiveTv),
        ProfileAvatarPreset("Popcorn", Color(0xFFFF8F00), Icons.Default.LocalMovies),
        ProfileAvatarPreset("Hero", Color(0xFF1565C0), Icons.Default.Shield),
        ProfileAvatarPreset("Shield", Color(0xFF0D47A1), Icons.Default.Security),
        ProfileAvatarPreset("Web", Color(0xFFC62828), Icons.Default.Bolt),
        ProfileAvatarPreset("Hammer", Color(0xFF6A1B9A), Icons.Default.Thunderstorm),
        ProfileAvatarPreset("Arc", Color(0xFFE65100), Icons.Default.FlashOn),
        ProfileAvatarPreset("Claw", Color(0xFFAD1457), Icons.Default.SportsMartialArts),
        ProfileAvatarPreset("Panther", Color(0xFF212121), Icons.Default.Masks),
        ProfileAvatarPreset("Spider", Color(0xFFB71C1C), Icons.Default.Whatshot),
        ProfileAvatarPreset("Strange", Color(0xFF4527A0), Icons.Default.AutoAwesome),
        ProfileAvatarPreset("Widow", Color(0xFF880E4F), Icons.Default.Visibility),
        ProfileAvatarPreset("Hulk", Color(0xFF2E7D32), Icons.Default.LocalFireDepartment),
        ProfileAvatarPreset("Marvel", Color(0xFFD32F2F), Icons.Default.Star),
        ProfileAvatarPreset("Star", Color(0xFFF9A825), Icons.Default.Star),
        ProfileAvatarPreset("Rocket", Color(0xFF00838F), Icons.Default.RocketLaunch),
        ProfileAvatarPreset("Fan", Color(0xFFE91E63), Icons.Default.Favorite),
        ProfileAvatarPreset("Kid", Color(0xFF43A047), Icons.Default.Person),
        ProfileAvatarPreset("Guest", Color(0xFF78909C), Icons.Default.Person),
        ProfileAvatarPreset("Night", Color(0xFF263238), Icons.Default.Masks),
        ProfileAvatarPreset("Storm", Color(0xFF0277BD), Icons.Default.Thunderstorm),
        ProfileAvatarPreset("Fire", Color(0xFFFF5722), Icons.Default.LocalFireDepartment),
        ProfileAvatarPreset("Ice", Color(0xFF00ACC1), Icons.Default.Shield),
        ProfileAvatarPreset("Power", Color(0xFF7B1FA2), Icons.Default.Bolt),
        ProfileAvatarPreset("Cosmic", Color(0xFF3949AB), Icons.Default.AutoAwesome)
    )

    fun count(): Int = all.size

    fun preset(index: Int): ProfileAvatarPreset = all[index.mod(count())]

    fun color(index: Int): Color = preset(index).color

    fun icon(index: Int): ImageVector = preset(index).icon

    fun label(index: Int): String = preset(index).label
}
