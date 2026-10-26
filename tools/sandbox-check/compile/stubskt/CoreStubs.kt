@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.core.content

object ContextCompat {
    @JvmStatic
    fun checkSelfPermission(context: android.content.Context, permission: String): Int = 0
}

open class FileProvider : android.content.ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(uri: android.net.Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor? = null
    override fun getType(uri: android.net.Uri): String? = null
    override fun insert(uri: android.net.Uri, values: android.content.ContentValues?): android.net.Uri? = null
    override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: android.net.Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        @JvmStatic
        fun getUriForFile(context: android.content.Context, authority: String, file: java.io.File): android.net.Uri =
            android.net.Uri.fromFile(file)
    }
}
