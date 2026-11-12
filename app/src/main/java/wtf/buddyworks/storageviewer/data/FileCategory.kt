package wtf.buddyworks.storageviewer.data

/**
 * File type buckets used for icons and the storage summary. The declaration order is also
 * the order of the summary bar, which keeps neighbouring bar segments colour-distinct.
 */
enum class FileCategory {
    Images, Videos, Documents, Audio, Archives, Other;

    companion object {
        private val byExtension: Map<String, FileCategory> = buildMap {
            listOf(
                "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw", "svg",
                "tif", "tiff", "avif", "arw", "cr2", "nef",
            ).forEach { put(it, Images) }
            listOf(
                "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts", "wmv", "flv", "mpg", "mpeg",
            ).forEach { put(it, Videos) }
            listOf(
                "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "mid", "midi", "wma", "m4b",
            ).forEach { put(it, Audio) }
            listOf(
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "odt", "ods", "odp",
                "rtf", "csv", "epub", "hwp", "pages", "numbers", "key",
            ).forEach { put(it, Documents) }
            listOf(
                "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "apk", "apks", "xapk",
                "apkm", "obb", "iso", "img",
            ).forEach { put(it, Archives) }
        }

        fun of(fileName: String): FileCategory {
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0 || dot == fileName.lastIndex) return Other
            return byExtension[fileName.substring(dot + 1).lowercase()] ?: Other
        }
    }
}
