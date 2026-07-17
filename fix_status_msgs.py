path = 'app/src/main/java/com/enigma/tv/data/EmbedProvidersResolver.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Remove source-specific messages, replace with generic ones
content = content.replace(
    'if (r != null) onStatus("Found stream from VidLink API...")',
    'if (r != null) onStatus("Source found. Preparing playback...")'
)
content = content.replace(
    'if (r != null) onStatus("Found stream from $srcName...")',
    'if (r != null) onStatus("Source found. Preparing playback...")'
)
content = content.replace(
    'onStatus("Starting 6 concurrent scrapers...")',
    'onStatus("Searching for stream...")'
)
content = content.replace(
    'if (collected.isNotEmpty()) onStatus("Scoring ${collected.size} streams...")',
    'if (collected.isNotEmpty()) onStatus("Optimizing playback quality...")'
)
content = content.replace(
    'onStatus("No streams yet. Checking fallback servers...")',
    'onStatus("Trying alternate sources...")'
)
content = content.replace(
    'onStatus("Fallback stream found. Starting playback...")',
    'onStatus("Source found. Starting playback...")'
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
