import android.net.Uri
fun main() {
    println(Uri.parse("https://example.com/?h={\"a\":1}").getQueryParameter("h"))
}
