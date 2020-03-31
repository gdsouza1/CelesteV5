package core.functionality

import core.DatabaseControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import java.sql.Connection

object OnGuildJoinScanner {

    fun acceptEvent(event: GuildJoinEvent) {
        CoroutineScope(Dispatchers.Default).launch {
            updateUserBase(event.guild)
        }
    }

    private lateinit var connection: Connection

    private suspend fun updateUserBase(guild: Guild) {
        if (!::connection.isInitialized || connection.isClosed) {
            connection = DatabaseControl.getConnection()
        }

        val sb = StringBuilder("INSERT INTO userbase(user_id) VALUES ")

        for (member in guild.memberCache) {
            sb.append("(${member.id}),")
        }

        sb.setLength(sb.length - 1)
        sb.append(" ON CONFLICT DO NOTHING")

        val query = sb.toString()
        val statement = connection.createStatement()
        synchronized(this) {
            statement.execute(query)
            statement.closeOnCompletion()
        }
        while (!statement.isClosed) {
            delay(500)
        }
        connection.close()
    }


}