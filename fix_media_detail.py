import re

path = 'app/src/main/java/com/enigma/tv/ui/MediaDetailScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix TvDetailContent
old_tv = '''    onRemoveFromHistory: () -> Unit,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelect: (Int) -> Unit,'''
new_tv = '''    onRemoveFromHistory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelect: (Int) -> Unit,'''
content = content.replace(old_tv, new_tv)

# Fix MobileDetailContent
old_mobile = '''    onPlayTrailer: (String) -> Unit,
    onAccentColorExtracted: ((Int) -> Unit)? = null
) {'''
new_mobile = '''    onPlayTrailer: (String) -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAccentColorExtracted: ((Int) -> Unit)? = null
) {'''
content = content.replace(old_mobile, new_mobile)

# Fix PlaylistAdd import
content = content.replace('import androidx.compose.material.icons.filled.PlayCircle', 'import androidx.compose.material.icons.filled.PlayCircle\nimport androidx.compose.material.icons.filled.PlaylistAdd')

# Fix PlaylistAdd usage (if it uses PlaylistAdd directly instead of Icons.Default.PlaylistAdd)
content = content.replace('Icon(PlaylistAdd,', 'Icon(androidx.compose.material.icons.filled.PlaylistAdd,')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
