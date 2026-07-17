import re

path = 'app/src/main/java/com/enigma/tv/ui/EnigmaViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix getDownloads
old_get_downloads = '''    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getDownloads(context: android.content.Context): List<androidx.media3.exoplayer.offline.Download> {
        return EnigmaApplication.getDownloadManager(context).currentDownloads
    }'''

new_get_downloads = '''    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getDownloads(context: android.content.Context): List<androidx.media3.exoplayer.offline.Download> {
        val dm = EnigmaApplication.getDownloadManager(context)
        val list = mutableListOf<androidx.media3.exoplayer.offline.Download>()
        val cursor = dm.downloadIndex.getDownloads()
        try {
            while (cursor.moveToNext()) {
                list.add(cursor.download)
            }
        } catch (e: Exception) {
        } finally {
            cursor.close()
        }
        return list
    }'''
content = content.replace(old_get_downloads, new_get_downloads)

# Fix enqueueDownload totalBytes check
old_enqueue = '''            val dm = EnigmaApplication.getDownloadManager(context)
            val totalBytes = dm.currentDownloads.sumOf { it.bytesDownloaded }'''
new_enqueue = '''            val downloads = getDownloads(context)
            val totalBytes = downloads.sumOf { it.bytesDownloaded }'''
content = content.replace(old_enqueue, new_enqueue)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")
