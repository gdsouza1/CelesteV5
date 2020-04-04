package core

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.internal.utils.tuple.MutablePair
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.IOException
import java.net.URL
import java.util.function.Consumer
import javax.imageio.ImageIO
import kotlin.math.ln
import kotlin.math.pow

//Whitespace characters  Credit: tchrist | https://stackoverflow.com/questions/4731055/whitespace-matching-regex-java
var whitespace_chars: String = ("" /* dummy empty string for homogeneity */
        + "\\u0009" // CHARACTER TABULATION
        + "\\u000A" // LINE FEED (LF)
        + "\\u000B" // LINE TABULATION
        + "\\u000C" // FORM FEED (FF)
        + "\\u000D" // CARRIAGE RETURN (CR)
        + "\\u0020" // SPACE
        + "\\u0085" // NEXT LINE (NEL)
        + "\\u00A0" // NO-BREAK SPACE
        + "\\u1680" // OGHAM SPACE MARK
        + "\\u180E" // MONGOLIAN VOWEL SEPARATOR
        + "\\u2000" // EN QUAD
        + "\\u2001" // EM QUAD
        + "\\u2002" // EN SPACE
        + "\\u2003" // EM SPACE
        + "\\u2004" // THREE-PER-EM SPACE
        + "\\u2005" // FOUR-PER-EM SPACE
        + "\\u2006" // SIX-PER-EM SPACE
        + "\\u2007" // FIGURE SPACE
        + "\\u2008" // PUNCTUATION SPACE
        + "\\u2009" // THIN SPACE
        + "\\u200A" // HAIR SPACE
        + "\\u2028" // LINE SEPARATOR
        + "\\u2029" // PARAGRAPH SEPARATOR
        + "\\u202F" // NARROW NO-BREAK SPACE
        + "\\u205F" // MEDIUM MATHEMATICAL SPACE
        + "\\u3000") // IDEOGRAPHIC SPACE

@Suppress("UNUSED")
/* A \s that actually works for Java’s native character set: Unicode */
var whitespace_charclass = "[$whitespace_chars]"

@Suppress("UNUSED")
/* A \S that actually works for  Java’s native character set: Unicode */
var not_whitespace_charclass = "[^$whitespace_chars]"

//Simplify getting logger from LoggerFactory
inline fun <reified T> logger(): org.slf4j.Logger {
    return LoggerFactory.getLogger(T::class.java)
}

/* Checks only if the string is 18 in length and all numbers
* Might still not actually be a snowflake */
fun isSnowflake(str: String): Boolean {
    if (str.length == 18) {
        for (c in str)
            if (!c.isDigit())
                return false
        return true
    }
    return false
}

/* Checks for 18 length, all numbers and if the first 64 bytes are possible
* according to discord epoch, call might be more expensive */
fun isSnowflakeStrict(str: String): Boolean {
    if (isSnowflake(str)) {
        val binString = java.lang.Long.toBinaryString(java.lang.Long.parseLong(str))
        val sb = StringBuilder(binString)
        repeat(64 - binString.length) {
            sb.insert(0, "0")
        }
        val dec: Long = java.lang.Long.parseLong(sb.toString().substring(0, 42), 2)
        val millis = dec + 1420070400000L

        if (millis <= System.currentTimeMillis()) {
            return true
        }
    }
    return false
}

// Get all mentioned users in a message //
fun getMentionedUsers(message: Message, strict: Boolean = true): List<String> {
    val arr = ArrayList<String>()
    for (user in message.mentionedUsers) {
        arr.add(user.id)
    }

    val split = message.contentRaw.split(" ")
    for (spl in split)
        if (spl != "" && spl != " ")
            if (spl[0] != '@')
                if (if (strict) isSnowflakeStrict(spl) else isSnowflake(spl))
                    arr.add(spl)
    return arr.distinct()
}

// Convert user to member and check if member is present in guild //
fun getAsMember(id: String, guild: Guild): Member {
    val member = guild.getMemberById(id)
    require(member != null)
    return member
}

fun isPresent(id: String, guild: Guild): Boolean {
    return guild.memberCache.getElementById(id) != null
}

fun getAsMember(user: User, guild: Guild) = getAsMember(user.id, guild)
fun isPresent(user: User, guild: Guild) = isPresent(user.id, guild)

// Get an empty consumer (for jda queue simplify) //
fun <T> emptyConsumer(): Consumer<T> {
    return Consumer {}
}

// Check if internet, no parameter == google //
fun checkInternetAccess(url: URL): Boolean {
    return try {
        url.openConnection()
        true
    } catch (ioex: IOException) {
        false
    }
}

fun checkInternetAccess(url: String) = checkInternetAccess(URL(url))
fun checkInternetAccess() = checkInternetAccess(URL("https://www.google.com"))


//Get a user's role color as color
fun getUserRoleColor(user: User, guild: Guild): Color {
    require(isPresent(user, guild))

    val member = guild.memberCache.getElementById(user.id)
    return member?.color ?: Color.PINK
}

//Convert immutable pair to mutable pair
fun <F, S> Pair<F, S>.mutableCopy(): MutablePair<F, S> {
    return MutablePair(this.first, this.second)
}

//Convert mutable pair into immutable pair
fun <F, S> MutablePair<F, S>.immutableCopy(): Pair<F, S> {
    val a = LinkedHashMap<String, Pair<String, String>>()
    val b = LinkedHashMap<String, MutablePair<String, String>>()
    a.computeAllIfAbsent(b) { s -> s.immutableCopy() }
    return Pair(this.left, this.right)
}

//Compute function to all values in map and add all to receiver if absent
fun <K, V, M> HashMap<K, V>.computeAllIfAbsent(map: HashMap<K, M>, mappingFunction: (M) -> V) {
    for (entry in map.entries) {
        val key = entry.key
        val value = entry.value
        val mappedValue = mappingFunction.invoke(value)

        this.putIfAbsent(key, mappedValue)
    }
}

fun avgColor(image: Image): Color {
    val width = image.getWidth(null)
    val height = image.getHeight(null)
    //Draw image to buffered image
    val buffImage = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
    val graphics = buffImage.graphics
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()

    val alphaChannel = buffImage.alphaRaster != null
    //Byte array
    val pixels = ((buffImage.raster.dataBuffer) as DataBufferByte).data

    val result: Array<IntArray> = Array(height) { IntArray(width) }

    if (alphaChannel) {
        val pixelLength = 4
        var pixel = 0
        var row = 0
        var col = 0
        while (pixel + 3 < pixels.size) {
            var argb = 0
            argb += pixels[pixel].toInt() and 0xff shl 24 // alpha
            argb += pixels[pixel + 1].toInt() and 0xff // blue
            argb += pixels[pixel + 2].toInt() and 0xff shl 8 // green
            argb += pixels[pixel + 3].toInt() and 0xff shl 16 // red
            result[row][col] = argb
            col++
            if (col == width) {
                col = 0
                row++
            }
            pixel += pixelLength
        }
    } else {
        val pixelLength = 3
        var pixel = 0
        var row = 0
        var col = 0
        while (pixel + 2 < pixels.size) {
            var argb = 0
            argb += -16777216 // 255 alpha
            argb += pixels[pixel].toInt() and 0xff // blue
            argb += pixels[pixel + 1].toInt() and 0xff shl 8 // green
            argb += pixels[pixel + 2].toInt() and 0xff shl 16 // red
            result[row][col] = argb
            col++
            if (col == width) {
                col = 0
                row++
            }
            pixel += pixelLength
        }
    }

    var ba = 0
    var ga = 0
    var ra = 0
    var aa = 0
    for (arr in result) {
        for (byte in arr) {
            ra += byte and 0xFF
            ga += byte shr 8 and 0xFF
            ba += byte shr 16 and 0xFF
            aa += byte shr 24 and 0xFF
        }
    }

    val area = width * height
    val r: Int = ba / area
    val g: Int = ga / area
    val b: Int = ra / area
    val a: Int = aa / area

    return Color(r, g, b, if (a < 0) 255 else a)
}


fun avgColor(url: String): Color {
    return try {
        val image = ImageIO.read(URL(url))
        avgColor(image)
    } catch (ex: Exception) {
        Color.pink
    }
}

fun memoryFormat(bytes: Long, si: Boolean): String? {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString() + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
}