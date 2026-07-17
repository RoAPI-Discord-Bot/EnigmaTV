import re

path = 'app/src/main/java/com/enigma/tv/data/TvLauncherSyncWorker.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('.setTitle(entry.title)', '.setTitle(entry.name)')
content = content.replace('.setDescription(entry.metaLine)', '.setDescription("Resume watching " + entry.name)')
content = content.replace('.setPosterArtUri(Uri.parse(entry.posterUrl ?: ""))', '.setPosterArtUri(Uri.parse(entry.poster))')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
