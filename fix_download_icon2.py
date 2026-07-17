import re

path = 'app/src/main/java/com/enigma/tv/ui/EnigmaShell.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('DrawerEntry(androidx.compose.material.icons.filled.Download, NavSection.DOWNLOADS', 'DrawerEntry(androidx.compose.material.icons.Icons.Default.Download, NavSection.DOWNLOADS')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
