package com.enigma.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.vector.ImageVector

/** Colored Material icons — labels match the icon shown. */
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
        ProfileAvatarPreset("Star", Color(0xFFF9A825), Icons.Default.Star),
        ProfileAvatarPreset("Heart", Color(0xFFE91E63), Icons.Default.Favorite),
        ProfileAvatarPreset("Smile", Color(0xFFAD1457), Icons.Default.Face),
        ProfileAvatarPreset("Fun", Color(0xFF827717), Icons.Default.EmojiEmotions),
        ProfileAvatarPreset("Kid", Color(0xFF43A047), Icons.Default.ChildCare),
        ProfileAvatarPreset("Pet", Color(0xFF5D4037), Icons.Default.Pets),
        ProfileAvatarPreset("Account", Color(0xFF78909C), Icons.Default.AccountCircle),
        ProfileAvatarPreset("Hero", Color(0xFF1565C0), Icons.Default.Shield),
        ProfileAvatarPreset("Secure", Color(0xFF0D47A1), Icons.Default.Security),
        ProfileAvatarPreset("Bolt", Color(0xFF6A1B9A), Icons.Default.Bolt),
        ProfileAvatarPreset("Fire", Color(0xFFFF5722), Icons.Default.LocalFireDepartment),
        ProfileAvatarPreset("Sun", Color(0xFFFF8F00), Icons.Default.WbSunny),
        ProfileAvatarPreset("Cloud", Color(0xFF0277BD), Icons.Default.Cloud),
        ProfileAvatarPreset("Spa", Color(0xFF00897B), Icons.Default.Spa),
        ProfileAvatarPreset("Magic", Color(0xFF4527A0), Icons.Default.AutoAwesome),
        ProfileAvatarPreset("Rocket", Color(0xFF00838F), Icons.Default.RocketLaunch),
        ProfileAvatarPreset("Game", Color(0xFF3949AB), Icons.Default.SportsEsports),
        ProfileAvatarPreset("Music", Color(0xFF6A1B9A), Icons.Default.MusicNote),
        ProfileAvatarPreset("Book", Color(0xFF5C6BC0), Icons.Default.MenuBook),
        ProfileAvatarPreset("Food", Color(0xFFE65100), Icons.Default.Restaurant),
        ProfileAvatarPreset("Travel", Color(0xFF00695C), Icons.Default.Flight),
        ProfileAvatarPreset("Art", Color(0xFFD32F2F), Icons.Default.Palette),
        ProfileAvatarPreset("Code", Color(0xFF263238), Icons.Default.Code)
    )

    fun count(): Int = all.size

    fun preset(index: Int): ProfileAvatarPreset = all[index.mod(count())]

    fun color(index: Int): Color = preset(index).color

    fun icon(index: Int): ImageVector = preset(index).icon

    fun label(index: Int): String = preset(index).label
}
