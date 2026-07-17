import re

path = 'app/src/main/java/com/enigma/tv/ui/EnigmaViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('enqueueDownload(context, detail.title, result.streamUrl)', 'enqueueDownload(context, detail.title, result.url)')
content = content.replace('loadEpisodesInternal(showId, season, keepEpisode, play = true)', 'loadEpisodesInternal(showId, seasonNumber, keepEpisode, play = true)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
