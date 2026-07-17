import re

path = 'app/src/main/java/com/enigma/tv/ui/ExoLivePlayer.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix conflicting imports
lines = content.split('\n')
unique_lines = []
for line in lines:
    if line.startswith('import '):
        if line not in unique_lines:
            unique_lines.append(line)
    else:
        unique_lines.append(line)

content = '\n'.join(unique_lines)

# Fix TimeBar
content = content.replace('view.findViewById<androidx.media3.ui.TimeBar>', 'view.findViewById<androidx.media3.ui.DefaultTimeBar>')

# Fix .androidx.compose.ui.draw.clip
content = content.replace('.androidx.compose.ui.draw.clip', '.clip')
content = content.replace('.androidx.compose.foundation.layout.offset', '.offset')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
