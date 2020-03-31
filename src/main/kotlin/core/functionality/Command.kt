package core.functionality

import core.computeAllIfAbsent
import core.emptyConsumer
import core.mutableCopy
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.requests.restaction.MessageAction
import net.dv8tion.jda.internal.utils.tuple.MutablePair
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
    protected val jda by lazy { message.jda }
    protected var parameters: HashMap<AppendableParameter, Pair<Boolean, String?>> = HashMap()

    //Starting point of execution, called by command executor
    abstract fun exec()
    protected fun parseParameters() {
        parseParameters(Collections.emptyMap())
    }

    protected fun parseParameters(vararg pars: AppendableParameter) {
        val map = HashMap<AppendableParameter, MutablePair<Boolean, String?>>(pars.size)
        for (par in pars) {
            map[par] = MutablePair<Boolean, String?>(false, null)
        }
        parseParameters(map)
    }

    private fun parseParameters(pars: Map<AppendableParameter, MutablePair<Boolean, String?>>) {

        //Add all pars from parameter
        val allParameters = LinkedHashMap<AppendableParameter, MutablePair<Boolean, String?>>(pars)
        //Add all already existing parameters
        allParameters.computeAllIfAbsent(parameters) { it.mutableCopy() }


        //Stores all message parts split by " " (whitespace)
        val messageArguments = ArrayList<String>()

        val splitMsg = message.contentRaw.split(" ")
        for (str in splitMsg) {
            //Only consider an argument if it is not empty or blank, to allow multiple whitespaces between args and save iterations
            if (str != " " && str != "") {
                messageArguments.add(str)
            }
        }
        //Remove first index, since it cannot be a parameter
        messageArguments.removeAt(0)

        //Parsed Structure = HashMap -> ParameterObject , Pair -> Presence, Value (if default -> default else null)
        val parsedParameters = HashMap<AppendableParameter, Pair<Boolean, String?>>()

        //Scan message and match against parameters to find out what need to be parsed
        //Parameter to parse structure = Pair -> The parameter object , the message arg it was matched to
        val parametersToParse = ArrayList<Pair<AppendableParameter, String>>()

        outer@ for (parameter in allParameters.keys) {
            val prefSuff = "${parameter.prefix}${parameter.name}"
            val pattern = Pattern.compile("$prefSuff|$prefSuff\\(([^(?!)]*)\\)").toRegex()
            for (arg in messageArguments) {
                if (pattern.matches(arg)) {
                    parametersToParse.add(Pair(parameter, arg))
                    continue@outer
                }
            }
            parsedParameters[parameter] = Pair(false, parameter.defaultPar)
        }

        //Parse the present ones
        for (match in parametersToParse) {
            //First = Parameter
            //Second = string matched to
            val parameter = match.first
            val argument = match.second

            val lparenthesis = argument.indexOf("(")
            val rparenthesis = argument.indexOf(")")

            if (parameter.isSubpar && lparenthesis != -1 && rparenthesis != -1) {
                //Find substring representing the value in between parenthesis
                val value = argument.substring(lparenthesis + 1, rparenthesis)
                //Add the parsed value to parameters
                parsedParameters[parameter] = Pair(true, value)
            } else {
                //If the parameter is not marked as needing a subparameter, mark as true and value to default
                parsedParameters[parameter] = Pair(true, parameter.defaultPar)
            }
        }
        parameters = parsedParameters
    }

    protected fun appendParameters(pars: Collection<AppendableParameter>) {
        for (par in pars) {
            parameters[par] = Pair<Boolean, String?>(false, null)
        }
    }

    protected fun appendParameters(vararg pars: AppendableParameter) {
        for (par in pars) {
            parameters[par] = Pair<Boolean, String?>(false, null)
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
        require(par.isSubpar)
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