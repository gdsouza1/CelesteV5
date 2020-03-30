package core.functionality

import core.emptyConsumer
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.requests.restaction.MessageAction
import org.slf4j.Logger
import java.io.File
import java.math.BigDecimal
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

@Suppress("unused")
abstract class Command(val message: Message) {

    //Register nano time at start
    private val startNanos: Long = System.nanoTime()

    private val getTimeFromStart = {
        val nanos = System.nanoTime() - startNanos

        var timeToSet: Long = nanos
        var labelToSet = "nanoseconds"

        if (nanos > 10000) {

            val millis = nanos / 1000000
            if (millis > 1000) {

                val sec = millis / 1000
                timeToSet = sec
                labelToSet = "seconds"
            } else {
                timeToSet = millis
                labelToSet = "milliseconds"
            }
        }
        Pair(timeToSet, labelToSet)
    }.invoke()

    private val getNanosFromStart: Long = { System.nanoTime() - startNanos }.invoke()
    private val getMillisFromStart: Double = { getNanosFromStart / 1000000.0 }.invoke()
    private val getSecondsFromStart: BigDecimal = { BigDecimal(getMillisFromStart / 1000.0) }.invoke()

    protected val guild by lazy { message.guild }
    private val channel by lazy { message.channel }
    protected val author by lazy { message.author }
    protected var parameters: HashMap<AppendableParameter, Pair<Boolean, String?>> = HashMap()

    //Starting point of execution, called by command executor
    abstract fun exec()
    protected fun parseParameters() {
        parseParameters(Collections.emptyMap())
    }

    protected fun parseParameters(vararg pars: AppendableParameter) {
        val map = HashMap<AppendableParameter, Pair<Boolean, String?>>(pars.size)
        for (par in pars) {
            map[par] = Pair(false, null)
        }
        parseParameters(map)
    }

    private fun parseParameters(pars: Map<AppendableParameter, Pair<Boolean, String?>>) {
        val parameterMap = LinkedHashMap<AppendableParameter, Pair<Boolean, String?>>(pars)
        parameterMap.putAll(parameters)

        val parsed = HashMap<AppendableParameter, Pair<Boolean, String?>>()

        val formattedArr = ArrayList<String>()
        val splitMsg = message.contentRaw.split(" ")
        for (str in splitMsg) {
            if (str != " " && str != "") {
                formattedArr.add(str)
            }
        }
        for (formatted in formattedArr) {
            for (parameter in parameterMap) {

                val par = parameter.key
                val simplePattern = Pattern.compile("${par.prefix}${par.name}").toRegex()
                val subParPattern = Pattern.compile("${par.prefix}${par.name}\\(([^(?!)]*)\\)")

                if (formatted.matches(simplePattern) || formatted.matches(subParPattern.toRegex())) {

                    if (par.isSubpar) {
                        val matcher = subParPattern.matcher(formatted)
                        var subparameter: String? = null
                        while (matcher.find()) {
                            subparameter = matcher.group(1)
                        }

                        if (subparameter == null) {
                            val defaultPar: String? = par.defaultPar
                            if (defaultPar != null) {
                                subparameter = defaultPar
                            }
                        }

                        parsed[par] = Pair(true, subparameter)
                    } else
                        parsed[par] = Pair(true, null)
                }
            }
        }
        parameters = parsed
    }

    protected fun appendParameters(pars: Collection<AppendableParameter>) {
        for (par in pars) {
            parameters[par] = Pair(false, null)
        }
    }

    protected fun appendParameters(vararg pars: AppendableParameter) {
        for (par in pars) {
            parameters[par] = Pair(false, null)
        }
    }

    protected infix fun HashMap<AppendableParameter, Pair<Boolean, String?>>.present(par: AppendableParameter): Boolean {
        //Parse entries from map
        for (entry in this.entries) {
            //if key == parameter only then
            if (entry.key == par) {
                //get presence from pair
                if (entry.value.first) {
                    return true
                }
            }
        }
        return false
    }

    protected infix fun HashMap<AppendableParameter, Pair<Boolean, String?>>.subpar(par: AppendableParameter): String? {
        require(par.isSubpar && this present par)
        return parameters[par]?.second
    }

    //Simple send message, returns Message action for queueing and consumer passing

    //Simplify feedback sending
    protected fun respond(msg: Message): MessageAction {
        return channel.sendMessage(msg)
    }

    protected fun respond(str: String): MessageAction {
        return channel.sendMessage(str)
    }

    protected fun respond(embed: MessageEmbed): MessageAction {
        return channel.sendMessage(embed)
    }

    protected fun respond(file: File): MessageAction {
        return channel.sendFile(file)
    }

    //Simple standard send message, log error and do nothing on success
    protected fun simpleRespondAndQueue(msg: Message, logger: Logger) {
        val stdFail = Consumer<Throwable> {
            logger.error("Failed to send message ${msg.contentRaw} in ${channel.name} of guild ${guild.name} ", it)
        }
        channel.sendMessage(msg).queue(emptyConsumer(), stdFail)
    }

    private fun simpleRespondAndQueue(str: String, logger: Logger) {
        val stdFail = Consumer<Throwable> {
            logger.error("Failed to send message $str in ${channel.name} of guild ${guild.name} ", it)
        }
        channel.sendMessage(str).queue(emptyConsumer(), stdFail)
    }

    protected fun simpleRespondAndQueue(embed: MessageEmbed, logger: Logger) {
        val stdFail = Consumer<Throwable> {
            logger.error("Failed to send message $embed in ${channel.name} of guild ${guild.name} ", it)
        }
        channel.sendMessage(embed).queue(emptyConsumer(), stdFail)
    }

    protected fun simpleRespondAndQueue(file: File, logger: Logger) {
        val stdFail = Consumer<Throwable> {
            logger.error("Failed to send message ${file.name} in ${channel.name} of guild ${guild.name} ", it)
        }
        channel.sendFile(file).queue(emptyConsumer(), stdFail)
    }

    protected fun sendExecutionTime(logger: Logger) {
        val time = getTimeFromStart
        simpleRespondAndQueue("```Execution time: ${time.first} ${time.second}", logger)
    }

    protected fun sendExecutionTime(timeParameter: AppendableParameter, logger: Logger) {
        if (parameters present timeParameter) {
            val pair = when (parameters subpar timeParameter) {
                "n", "ns", "nanos", "nano", "nanoseconds", "nanosecond" -> Pair(getNanosFromStart, "nanoseconds")
                "m", "ms", "millis", "milli", "milliseconds", "millisecond" -> Pair(getMillisFromStart, "milliseconds")
                "s", "secs", "sec", "seconds", "second" -> Pair(getSecondsFromStart, "seconds")
                else -> Pair(getMillisFromStart, "milliseconds (Invalid exectime parameter default to millis)")
            }
            simpleRespondAndQueue("```Execution time: ${pair.first} ${pair.second}```", logger)
        }
    }


}