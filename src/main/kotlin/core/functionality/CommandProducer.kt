package core.functionality

import core.logger
import core.parallelism
import core.prefix
import core.shouldAttentBots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.concurrent.Executors
import kotlin.system.measureNanoTime

object CommandProducer : Filter<MessageReceivedEvent> {

    private val commandExecutor = Executors.newWorkStealingPool(parallelism).asCoroutineDispatcher()

    private val logger = logger<CommandProducer>()

    @ExperimentalStdlibApi
    override fun filter(obj: MessageReceivedEvent) {
        //Cast to message received, can be either guild or private
        val message = obj.message
        val content = message.contentDisplay

        //if not null send for processing else end
        val execTime = measureNanoTime {
            isCommand(message)?.let {
                logger.trace("Starting processing of command {$content")

                //Retrieve instance TYPE is command
                //Dispatch with custom Work Stealing pool, dynamic parallelism
                CoroutineScope(commandExecutor).launch {
                    val cmd = CommandType.instance(it, message)
                    cmd.exec()
                }
            }
        }
        logger.trace("Finished processing command $content in $execTime ns")
    }

    private fun isCommand(message: Message): Class<out Command>? {

        val raw = message.contentRaw

        //eliminates gradually using the least possible
        if (raw.length >= prefix.length)
            if (raw.startsWith(prefix))
                if (shouldAttentBots || !shouldAttentBots && !message.author.isBot)
                //check exists returns the CommandType ? exists : null
                    return CommandType.checkExists(raw)

        return null
    }
}