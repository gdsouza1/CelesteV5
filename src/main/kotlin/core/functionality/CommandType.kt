package core.functionality

import com.google.common.reflect.ClassPath
import net.dv8tion.jda.api.entities.Message
import java.util.stream.Collectors

object CommandType {

    @Suppress("UNCHECKED_CAST")

    private val commandKlasses by lazy {
        val path = ClassPath.from(Thread.currentThread().contextClassLoader)
        val classInfos = path.getTopLevelClasses("core.functionality.commands")

        classInfos.parallelStream().map { t -> t.load() as Class<out Command> }.collect(Collectors.toUnmodifiableList())
    }

    fun checkExists(request: String): Class<out Command>? {
        val formattedRequest = request.split(" ")[0].substring(2).toUpperCase()
        for (klass in commandKlasses) {
            if (klass.simpleName.toUpperCase() == formattedRequest.toUpperCase()) {
                return klass as Class<out Command>
            }
        }
        return null
    }

    @ExperimentalStdlibApi
    fun instance(klass: Class<out Command>, message: Message): Command {
        return klass.cast(klass.constructors.firstOrNull()?.newInstance(message))
    }
}