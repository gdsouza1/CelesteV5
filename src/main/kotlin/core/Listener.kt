package core

import core.functionality.Actors.messageActor
import core.functionality.Actors.selfGuildJoinActor
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

@ExperimentalStdlibApi
@ObsoleteCoroutinesApi
@Suppress("UNCHECKED_CAST")
object Listener : ListenerAdapter() {

    private val logger = logger<Listener>()


    override fun onMessageReceived(event: MessageReceivedEvent) {
        dispatch(event, messageActor as SendChannel<Event>)
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        dispatch(event, selfGuildJoinActor as SendChannel<Event>)
    }


    private fun dispatch(event: Event, channel: SendChannel<Event>) = runBlocking {
        channel.send(event)
        logger.trace("Dispatched event $event")
    }

}

