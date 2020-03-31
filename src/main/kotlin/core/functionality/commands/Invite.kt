package core.functionality.commands

import core.functionality.AppendableParameter
import core.functionality.Command
import core.getUserRoleColor
import core.logger
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import java.lang.IllegalArgumentException

class Invite(message: Message) : Command(message) {

    val logger = logger<Invite>()

    companion object {
        val perm = AppendableParameter("--", "perm", true, "8")
        val noembed = AppendableParameter("--", "noembed", false)
    }

    override fun exec() {
        parseParameters(perm, noembed)


        val inviteMessage = buildInvite()
        sendInvite(inviteMessage)
    }

    private fun sendInvite(message: Message) {
        simpleRespondAndQueue(message, logger)
    }

    private fun buildUrl(permLevel: String) = jda.getInviteUrl(Permission.getPermissions(asLong(permLevel)))

    private fun asLong(perm: String): Long {
        val asInt = java.lang.Long.parseLong(perm)
        require(asInt in 1..2147483126)
        return asInt
    }

    private fun buildInvite(): Message {

        val mb = MessageBuilder()

        val permLevel = parameters subpar perm

        permLevel?.let {
            if (parameters present noembed) {
                mb.append(buildText(permLevel))
                return mb.build()
            }
            val withEmbed = buildEmbed(permLevel)
            mb.setEmbed(withEmbed.second)
            mb.append(withEmbed.first)

            return mb.build()
        }
        throw IllegalArgumentException("Invalid permission parameter")
    }

    private fun buildEmbed(perm: String): Pair<String, MessageEmbed> {
        val eb = EmbedBuilder()
        val url = buildUrl(perm)

        eb.setColor(getUserRoleColor(author, guild))
        eb.setAuthor("Add me to your server using this link", url, jda.selfUser.effectiveAvatarUrl)

        return Pair(url, eb.build())
    }

    private fun buildText(perm: String): String {
        val url = buildUrl(perm)
        return "```Add me to your server using the link below!``` \n$url"
    }

}