package core.functionality.commands

import core.functionality.AppendableParameter
import core.functionality.Command
import core.getAsMember
import core.getMentionedUsers
import core.isPresent
import core.logger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import java.awt.Color
import java.util.*
import kotlin.collections.ArrayList

class Avatar(message: Message) : Command(message) {

    private val logger = logger<Avatar>()

    companion object {
        val small = AppendableParameter("--", "small")
        val noembed = AppendableParameter("--", "noembed")
        val size = AppendableParameter("--", "size", true, "2048")
        val execTime = AppendableParameter("--", "exectime", true, "millis")
    }

    override fun exec() {

        parseParameters(small, noembed, size, execTime)

        val mentions = getMentionedUsers(message)
        val embeds = getAvatars(mentions)

        deliverAvatars(embeds)
    }

    //Retrieve all Avatars
    private fun getAvatars(users: List<String>): List<Message> {
        //If no one is mentioned, return the author's avatar
        if (users.isEmpty()) {

            return Collections.singletonList(
                    if (parameters present noembed)
                        asText(author)
                    else
                        (asEmbed(author)))

        }


        val messages = ArrayList<Message>(users.size)
        for (id in users) {
            val user: User? = jda.getUserById(id)
            user?.let {

                val msg = if (parameters present noembed)
                    asText(user)
                else
                    asEmbed(user)

                messages.add(msg)
            }
        }
        return messages
    }

    private fun asText(user: User): Message {
        val mb = MessageBuilder()
        mb.append("${user.name} avatar: ")
        mb.append(avatarSizeSubroutine(user.effectiveAvatarUrl))
        return mb.build()
    }

    //Build Embeds
    private fun asEmbed(user: User): Message {
        val mb = MessageBuilder()
        val eb = EmbedBuilder()

        val effAvatarRaw = user.effectiveAvatarUrl

        eb.setAuthor(user.name, effAvatarRaw, jda.selfUser.effectiveAvatarUrl)

        eb.setImage(avatarSizeSubroutine(effAvatarRaw))

        if (isPresent(user, super.guild))
            eb.setColor(getAsMember(user, super.guild).colorRaw)
        else
            eb.setColor(Color(0xFF0000))

        mb.setEmbed(eb.build())
        return mb.build()
    }

    private fun avatarSizeSubroutine(avatarRaw: String): String {
        var avatarToSet = avatarRaw.plus("?size=2048")
        val isSizePresent = parameters present size
        //Size parameters overrides small parameter

        if (!isSizePresent) {
            //Only check for small if size is not present
            if (parameters present small) {
                avatarToSet = avatarRaw.plus("?size=128")
            }
        } else {
            //Only affect if size is not null, due to errors
            val sizePar = parameters subpar size
            sizePar?.let {
                avatarToSet = "$avatarRaw?size=$sizePar"
            }
        }
        return avatarToSet
    }

    //Deliver to channel
    private fun deliverAvatars(messages: List<Message>) {
        for (message in messages) {
            //Empty success
            simpleRespondAndQueue(message, logger)
        }
        sendExecutionTime(execTime, logger)
    }
}