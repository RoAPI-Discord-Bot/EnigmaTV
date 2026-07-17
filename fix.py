import re

path = 'app/src/main/java/com/enigma/tv/ui/PlayerFullscreenHost.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('    liveWaitingMessage: String? = null,', '    liveWaitingMessage: String? = null,\n    streamPlaying: Boolean = false,')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
