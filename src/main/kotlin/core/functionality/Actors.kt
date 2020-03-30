package core.functionality

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.actor
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.util.concurrent.Executors

@ExperimentalStdlibApi
@ObsoleteCoroutinesApi
object Actors {

    val actorExecutors = Executors.newWorkStealingPool().asCoroutineDispatcher()

    val messageActor = CoroutineScope(actorExecutors).messageActor()
    val selfGuildJoinActor = CoroutineScope(actorExecutors).selfGuildJoin()

    fun CoroutineScope.messageActor() = actor<MessageReceivedEvent> {
        for (event in channel) {
            CommandProducer.filter(event)
        }
    }

    fun CoroutineScope.selfGuildJoin() = actor<GuildJoinEvent> {
        for (event in channel) {
            OnGuildJoinScanner.acceptEvent(event)
        }
    }
}

