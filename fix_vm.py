import re

path = 'app/src/main/java/com/enigma/tv/ui/EnigmaViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add imports
imports = '''import com.enigma.tv.data.TmdbRepository
import com.enigma.tv.data.TvItem
import com.enigma.tv.data.UserSessionStore
import com.enigma.tv.EnigmaApplication
import androidx.media3.exoplayer.offline.Download'''
content = content.replace('import com.enigma.tv.data.TmdbRepository\nimport com.enigma.tv.data.TvItem\nimport com.enigma.tv.data.UserSessionStore', imports)

# Remove old onPlayerPlaybackReady/Loading
old_funcs = '''    fun onPlayerPlaybackReady() {
        _state.update { it.copy(playerLoading = false, playerStreamPlaying = true, playerLoadingMessage = null) }
    }

    fun onPlayerPageLoading(loading: Boolean) {
        _state.update { it.copy(playerLoading = loading) }
    }'''
content = content.replace(old_funcs, '')

# Fix getTvEpisodes -> tvSeason map
old_repo = 'repo.getTvEpisodes(st.currentShowId!!, st.selectedSeason + 1)'
new_repo = 'repo.tvSeason(st.currentShowId!!, st.selectedSeason + 1).map { it.episodeNumber to it.name }.sortedBy { it.first }'
content = content.replace(old_repo, new_repo)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

path_exo = 'app/src/main/java/com/enigma/tv/ui/ExoLivePlayer.kt'
with open(path_exo, 'r', encoding='utf-8') as f:
    content_exo = f.read()

# Fix duplicates
content_exo = content_exo.replace('    val lifecycleOwner = LocalLifecycleOwner.current\n    val lifecycleOwner = LocalLifecycleOwner.current', '    val lifecycleOwner = LocalLifecycleOwner.current')
content_exo = content_exo.replace('    var bingeCountdown by remember(playUrl, playToken) { mutableStateOf<Int?>(null) }\n    var bingeCountdown by remember(playUrl, playToken) { mutableStateOf<Int?>(null) }', '    var bingeCountdown by remember(playUrl, playToken) { mutableStateOf<Int?>(null) }')
content_exo = content_exo.replace('    val lifecycleOwner = LocalLifecycleOwner.current\n    DisposableEffect(lifecycleOwner, player)', '    DisposableEffect(lifecycleOwner, player)')

# Add compose imports
exo_imports = '''import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.enigma.tv.ui.theme.EnigmaPink
import androidx.compose.material3.*'''
content_exo = content_exo.replace('import androidx.compose.ui.platform.LocalLifecycleOwner\nimport androidx.compose.foundation.border', exo_imports)

# Fix view.findViewById<android.view.View>
content_exo = content_exo.replace('view.findViewById<android.view.View>', 'view.findViewById<android.view.View>')

with open(path_exo, 'w', encoding='utf-8') as f:
    f.write(content_exo)

print("Done")
