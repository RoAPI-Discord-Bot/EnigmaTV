import androidx.media3.common.Tracks
fun main() {
    val clazz = Tracks.Group::class.java
    clazz.methods.forEach { println(it.name + ' ' + it.returnType.name) }
}
