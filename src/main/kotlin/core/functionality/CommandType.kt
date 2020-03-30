package core.functionality

import core.functionality.commands.Avatar
import net.dv8tion.jda.api.entities.Message
import kotlin.reflect.KClass
import kotlin.reflect.cast

enum class CommandType(private val command: KClass<out Command>) {

    @Suppress("UNUSED")
    AVATAR(Avatar::class);

    @ExperimentalStdlibApi
    fun instance(message: Message): Command {
        return command.cast(command.constructors.firstOrNull()?.call(message))
    }

    override fun toString(): String {
        return this.name
    }

    companion object {

        fun checkExists(raw: String): CommandType? {
            //Strip Prefix
            val formatted = raw.split(" ")[0].substring(2).toUpperCase()
            for (value in values()) {
                if (value.name == formatted)
                    return value
            }
            return null
        }

    }


}