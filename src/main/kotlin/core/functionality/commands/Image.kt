package core.functionality.commands

import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.JsonNode
import com.mashape.unirest.http.Unirest
import core.ConfigurationAccessor
import core.avgColor
import core.functionality.AppendableParameter
import core.functionality.Command
import core.logger
import core.memoryFormat
import kotlinx.coroutines.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.json.JSONObject
import java.awt.Color
import java.awt.Dimension
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern
import javax.naming.ServiceUnavailableException

class Image(message: Message) : Command(message) {

    //Companion holds configuration for image cyclers
    companion object {
        //Mutual objects
        val logger = logger<Image>()
        val cyclerExecutor = Executors.newCachedThreadPool().asCoroutineDispatcher()
        val activeInstances = CopyOnWriteArrayList<Pair<ImageCycler, String>>()

        //Search engine and google api
        var searchEngineId: String? = null //memoized after first request
        var googleApiKey: String? = null //memoized after first request
        var csApiVersion = "v1"
        const val googleLogoUrl = "https://assets.materialup.com/uploads/82eae29e-33b7-4ff7-be10-df432402b2b6/preview"

        //Reaction loading and configuration
        private val controlReactions = arrayOf("\u2B05", "\uD83D\uDD00", "\u27A1", "\u21A9", "\u21AA", "\u274C")
        private lateinit var reactionCache: LinkedHashMap<MovementType, ReactionEmote>
        private lateinit var reactionNames: ArrayList<String>

        private fun loadReactions(jda: JDA) {
            val values = MovementType.values()
            val names = ArrayList<String>(controlReactions.size)
            val cache = LinkedHashMap<MovementType, ReactionEmote>(controlReactions.size)
            for (i in controlReactions.indices) {
                val emote = ReactionEmote.fromUnicode(controlReactions[i], jda)
                names.add(emote.name)
                cache[values[i]] = emote
            }
            reactionCache = cache
            reactionNames = names
        }

        //Appendable parameters

        val noColorAvg = AppendableParameter("--", "noavg", false)
        val noBrighter = AppendableParameter("--", "nobright", false)
        val noReactions = AppendableParameter("--", "noreac", false)
        val noType = AppendableParameter("--", "notype", false)
        val compact = AppendableParameter("--", "compact", false)
        val noEmbed = AppendableParameter("--", "noembed", false)
        val exectime = AppendableParameter("--", "exectime", true, "millis")


        //Config constants
        const val messageQueueCapacity = 15
        const val reactionQueueCapacity = 15
        const val initExpirationTime = 60000L
        const val lastMessageMaxThreshold = 25000L
        const val remainingTimeRefreshTreshold = 25000L
        const val remaningTimeAdditionOnRefresh = 25000L
        const val timerCheckDelay = 5000L
        const val deleteOnManualExit = true
        const val deleteOnExpirationExit = false
        const val maxMessageDistanceForResend = 15

    }

    override fun exec() {

        parseParameters(noColorAvg, noBrighter, noReactions, noType, compact, noEmbed, exectime)

        //Look for query in message
        val request = parseQuery(message.contentDisplay)

        request?.let { r ->
            //Format it and prevent parameter injection
            val validatedRequest = validateQuery(r)
            //Build the request url
            val url = buildUrl(validatedRequest)
            //Make the request
            val response = request(url)
            //Parse the request's response
            val result = parseReturn(response)

            //If the response is not null, deploy cycler
            result?.let {
                loadReactions(jda)
                ImageCycler(it.first, author.id, it.second).start()
            }
        }
    }


    //Return Structure Array -> Pair (Container) , Search time
    private fun parseReturn(response: HttpResponse<JsonNode>): Pair<ArrayList<ItemContainer>, Double>? {
        val json = response.body.`object`
        val items = json.optJSONArray("items")
        val searchResult = json.optJSONObject("searchInformation")

        val totalResults = searchResult.optLong("totalResults")
        val searchTime = searchResult.optDouble("formattedSearchTime")

        if (totalResults > 0) {
            val list = ArrayList<ItemContainer>(items.length())

            items?.let {
                for (item in items) {
                    val jobj = item as JSONObject
                    val imageObj = jobj.optJSONObject("image")


                    val url = jobj.optString("link")
                    val title = jobj.optString("title")

                    val contextUrl = imageObj.optString("contextLink")

                    val height = imageObj.optInt("height")
                    val width = imageObj.optInt("width")

                    val byteSize = imageObj.optInt("byteSize")

                    val container = ItemContainer(url, contextUrl, title, Dimension(width, height), byteSize)
                    list.add(container)
                }
            }
            return Pair(list, searchTime)
        }
        noResultsError(searchTime, totalResults)
        return null
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    //Errors
    private fun noResultsError(searchTime: Double, totalResults: Long) {
        simpleRespondAndQueue("\u26A0 - Searched for ***$searchTime*** second(s) and found ***$totalResults*** results.", logger)
    }

    //////////////////////////////////////////////////////////////////////////////////////////////

    private fun request(url: String): HttpResponse<JsonNode> = Unirest.get(url).header("Accept", "application/json").asJson()

    private fun parseQuery(content: String): String? {
        val nonParameters = getAllNonParameters(content)

        val sb = StringBuilder()
        for (i in nonParameters.indices) {
            if (i != nonParameters.size - 1) {
                sb.append("${nonParameters[i]} ")
            } else {
                sb.append(nonParameters[i])
            }
        }

        val query = sb.toString()

        return if (query.isNotEmpty() && query.isNotBlank()) {
            query
        } else {
            simpleRespondAndQueue("\u26A0 - Invalid query ' '", logger)
            null
        }
    }

    //Validates and formats query
    private fun validateQuery(toVal: String): String {
        return toVal.replace(" ", "%20").replace("&", "").replace("?", "")
    }

    //Build request url
    @Throws(ServiceUnavailableException::class)
    private fun buildUrl(request: String): String {
        if (searchEngineId != null && googleApiKey != null) {
            val urlb = StringBuilder("https://www.googleapis.com/customsearch/")
            urlb.append(csApiVersion).append("?key=")
            urlb.append(googleApiKey).append("&cx=")
            urlb.append(searchEngineId).append("&searchType=image&q=")
            urlb.append(request).append("&alt=json&start=1")

            return urlb.toString()
        } else {
            try {
                searchEngineId = ConfigurationAccessor.getConfig("SEARCH_ENGINE")
                googleApiKey = ConfigurationAccessor.getConfig("GOOGLE_API_KEY_1")
                return buildUrl(request)
            } catch (unavailable: ServiceUnavailableException) {
                throw ServiceUnavailableException("Cannot request images right now")
            }
        }
    }

    inner class ImageCycler(private val containers: ArrayList<ItemContainer>, private val authorId: String, private val searchTime: Double) {

        private lateinit var cycler: Message

        private lateinit var job: Job
        private lateinit var listener: ListenerAdapter

        //Containers
        private lateinit var messageQueue: LinkedBlockingQueue<Message>
        private lateinit var reactionQueue: LinkedBlockingQueue<ReactionTime>
        private val memoizationContainer = HashMap<Int, Message>(containers.size)

        //Controls during execution
        private var currentIndex = 1
        private var lastActionMillis: Long = 0L
        private var messageDistance = 0

        //Parameters
        private val isNoReaction = parameters present noReactions
        private val isNoAverage = parameters present noColorAvg
        private val isNoBrighter = parameters present noBrighter
        private val isNoType = parameters present noType
        private val isCompact = parameters present compact
        private val isNoEmbed = parameters present noEmbed

        //////////////////////////////////////////////////////////////////////////////////////////////
        //Message retrieval and parsing


        //Build a new listener instance and append it to the responsible jda instance
        private fun getNewListenerInstance(): ListenerBundle {

            val mQueue = LinkedBlockingQueue<Message>(messageQueueCapacity)
            val rQueue = LinkedBlockingQueue<ReactionTime>(reactionQueueCapacity)

            val listener = object : ListenerAdapter() {
                override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
                    if (!isNoType) {
                        if (event.guild == guild && event.channel == channel) {
                            messageQueue.put(event.message)
                        }
                    }
                }

                override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent) {
                    if (!isNoReaction) {
                        val time = System.currentTimeMillis()
                        if (event.messageId == cycler.id && event.userId == authorId) {
                            if (reactionNames.contains(event.reactionEmote.name)) {
                                if (event.userId != jda.selfUser.id) {
                                    val reaction = event.reactionEmote
                                    val rt = ReactionTime(reaction, time, event.reaction)
                                    reactionQueue.put(rt)
                                }
                            }
                        }
                    }
                }
            }
            return ListenerBundle(listener, mQueue, rQueue)
        }


        private fun reactionReceiver() {
            while (job.isActive) {
                val reaction = reactionQueue.take()
                parseReaction(reaction)
            }
        }

        //Loop retrieves and delegates messages to be parsed
        private fun messageReceiver() {
            while (job.isActive) {
                val message = messageQueue.take()
                parseMessage(message)
            }
        }

        private fun parseReaction(event: ReactionTime) {
            var type: MovementType? = null
            for (cached in reactionCache) {
                val name = cached.value.name
                if (name == event.emote.name) {
                    type = cached.key
                }
            }

            val handleActualReaction: (ReactionTime) -> Unit = {
                if (type != MovementType.EXIT) {
                    it.reaction.removeReaction(author).complete()
                }
                lastActionMillis = event.time
            }

            when (type) {
                MovementType.PREVIOUS -> {
                    previous()
                    handleActualReaction(event)
                }
                MovementType.NEXT -> {
                    next()
                    handleActualReaction(event)
                }
                MovementType.FIRST -> {
                    first()
                    handleActualReaction(event)
                }
                MovementType.LAST -> {
                    last()
                    handleActualReaction(event)
                }
                MovementType.RANDOM -> {
                    random()
                    handleActualReaction(event)
                }
                MovementType.EXIT -> {
                    exit()
                    handleActualReaction(event)
                }
            }
        }

        //Parses received messages for commands
        private fun parseMessage(message: Message) {
            val raw = message.contentRaw

            messageDistance++

            val handleActualMessage: (Message) -> Unit = {
                message.delete().queue()
                lastActionMillis = message.timeCreated.toInstant().toEpochMilli()
                messageDistance--
            }

            if (message.author.id == authorId) {
                if (raw.isNotBlank() && raw.isNotEmpty()) {
                    if (raw.first() != 'p') {
                        when (raw) {
                            "n", "next" -> {
                                handleActualMessage.invoke(message)
                                next()
                            }
                            "b", "previous" -> {
                                handleActualMessage.invoke(message)
                                previous()
                            }
                            "l", "last" -> {
                                handleActualMessage.invoke(message)
                                last()
                            }
                            "f", "first" -> {
                                handleActualMessage.invoke(message)
                                first()
                            }
                            "r", "random" -> {
                                handleActualMessage.invoke(message)
                                random()
                            }
                            "e", "exit" -> {
                                handleActualMessage.invoke(message)
                                exit()
                            }
                        }
                    } else {
                        val pattern = Pattern.compile("(p|page)[ ]*([0-9]+)")
                        val matcher = pattern.matcher(raw)
                        if (matcher.matches()) {
                            val page = matcher.group(2)
                            toPage(Integer.parseInt(page))
                            handleActualMessage.invoke(message)
                        }
                    }
                }
            }
        }

        //////////////////////////////////////////////////////////////////////////////////////////////

        //Index movement

        //Exit -> exit the cycler
        private fun exit() {
            terminateJob(this, true)
        }

        //Page -> goto specified page
        private fun toPage(index: Int) {

            if (index != currentIndex) {
                currentIndex = index
                if (index < 1) {
                    currentIndex = 1
                } else if (index > containers.size) {
                    currentIndex = containers.size
                }
                editCycler(currentIndex)
            }
        }

        //Random -> goto a random index
        private fun random() {
            val random = ThreadLocalRandom.current().nextInt(1, containers.size)
            if (random != currentIndex) {
                currentIndex = random
                editCycler(currentIndex)
            } else {
                random()
            }
        }

        //First -> goto the first index
        private fun first() {
            if (currentIndex != 1) {
                currentIndex = 1
                editCycler(1)
            }
        }

        //Last -> goto the last index
        private fun last() {
            if (currentIndex != containers.size) {
                currentIndex = containers.size
                editCycler(containers.size)

            }
        }

        //Next -> jump 1 index forward
        private fun next() {
            currentIndex++
            if (currentIndex > containers.size) {
                currentIndex = 1
            }
            editCycler(currentIndex)
        }

        //Previous -> jump 1 index backwards
        private fun previous() {
            currentIndex--
            if (currentIndex < 1) {
                currentIndex = containers.size
            }
            editCycler(currentIndex)
        }

        //////////////////////////////////////////////////////////////////////////////////////////////
        //Coroutine control

        //Termination criteria = Same author && Same channel, meaning that 2 people can run an embed at the same time
        private fun stopOldRunning() {
            val it = activeInstances.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                val instance = entry.first
                val entryId = entry.second
                if (instance != this && instance.authorId == authorId && this@Image.channel.id == entryId) {
                    terminateJob(instance, false)
                    activeInstances.remove(entry)
                }
            }
        }

        //Handle expiration
        private suspend fun handleTime() {
            var remainingTime = initExpirationTime
            while (job.isActive) {
                if (remainingTime <= 0) {
                    cycler.clearReactions().queue()
                    terminateJob(this, false)
                    break
                }

                if (remainingTime < remainingTimeRefreshTreshold && (System.currentTimeMillis() - lastActionMillis) < lastMessageMaxThreshold) {
                    remainingTime += remaningTimeAdditionOnRefresh
                }
                remainingTime -= timerCheckDelay
                delay(timerCheckDelay)
            }
        }

        //Actually cancels a cycler, and deletes according to settings, removes the appended listener
        private fun terminateJob(cycler: ImageCycler, manual: Boolean) {
            if (manual) {
                if (deleteOnManualExit) {
                    message.delete().queue()
                    cycler.cycler.delete().queue()
                }
            } else {
                if (deleteOnExpirationExit) {
                    cycler.cycler.delete().queue()
                }
            }

            jda.removeEventListener(cycler.listener)
            cycler.job.cancelChildren()
            cycler.job.cancel()
            sendExecutionTime(exectime, logger)
        }

        //////////////////////////////////////////////////////////////////////////////////////////////
        //CYCLER HANDLING

        private fun appendReactions(message: Message) {
            for (emote in reactionCache.values) {
                message.addReaction(emote.emoji).complete()
            }
        }

        private fun editCycler(index: Int) {
            val buildNew: (Int) -> Any = {
                if (isNoEmbed) {
                    buildMessage(index)
                } else {
                    buildEmbed(index)
                }
            }

            val gotten = memoizationContainer.getOrElse(index - 1) { buildNew.invoke(index) }

            if (isNoEmbed) {
                if (messageDistance >= maxMessageDistanceForResend) {
                    cycler.delete().queue()
                    respond(gotten as String).queue()
                    messageDistance = 0
                } else {
                    cycler.editMessage(gotten as String).queue()
                }
            } else {
                if (messageDistance >= maxMessageDistanceForResend) {
                    cycler.delete().queue()
                    respond(gotten as MessageEmbed).queue()
                    messageDistance = 0
                } else {
                    cycler.editMessage(gotten as MessageEmbed).queue()
                }
            }
        }


        //Build cycler as a MessageEmbed
        private fun buildEmbed(index: Int): MessageEmbed {
            val container = containers[index - 1]
            val title = container.title
            val url = container.url
            val contextLink = container.contextUrl

            val formattedByteSize = memoryFormat(container.byteSize.toLong(), true)

            val eb = EmbedBuilder()

            eb.setImage(url)

            if (!isCompact) {
                eb.setAuthor("Google - Took $searchTime s", contextLink, googleLogoUrl)
                eb.setTitle(title)
                eb.setFooter(" Image $currentIndex of ${containers.size} - ${container.size.width} x ${container.size.height} - $formattedByteSize")
            }

            val color = if (isNoAverage) {
                Color.pink
            } else {
                val avg = avgColor(url)
                if (isNoBrighter) {
                    avg.brighter()
                } else {
                    avg
                }
            }

            eb.setColor(color)

            return eb.build()
        }

        //Build cycler as a Message (String)
        private fun buildMessage(index: Int): String {
            val container = containers[index - 1]
            val title = container.title

            val formattedByteSize = memoryFormat(container.byteSize.toLong(), true)

            val top = "```$title\nImage $currentIndex of ${containers.size} - ${container.size.width} x ${container.size.height} - $formattedByteSize - Took $searchTime s```\n"
            val url = container.url
            return StringBuilder().append(top).append(url).toString()
        }

        //Gets cycler as embed or message depending on parameters and deals with memoization
        private fun getCycler(): Message {

            val buildCycler: (Int) -> Message = { i ->
                val cycler = if (isNoEmbed) {
                    val message = buildMessage(i)
                    respond(message).complete()
                } else {
                    val embed = buildEmbed(i)
                    respond(embed).complete()
                }
                cycler
            }

            return memoizationContainer.getOrElse(0) { buildCycler.invoke(1) }
        }


        //////////////////////////////////////////////////////////////////////////////////////////////

        //Build first cycler and assign it to holder for further edition
        private fun deployCycler() {
            this.cycler = getCycler()
            if (!isNoReaction) {
                appendReactions(cycler)
            }
        }

        fun start() {

            //Build cycler with first index of containers
            deployCycler()

            val bundle = getNewListenerInstance()
            val adapter = bundle.adapter
            jda.addEventListener(adapter)

            listener = adapter
            messageQueue = bundle.messageQueue
            reactionQueue = bundle.reactionQueue

            job = CoroutineScope(cyclerExecutor).launch {
                if (!isNoType) {
                    launch { messageReceiver() }
                }
                if (!isNoReaction) {
                    launch { reactionReceiver() }
                }
                launch { handleTime() }
            }


            stopOldRunning()
            activeInstances.add(Pair(this, channel.id))
        }
    }

    data class ReactionTime(val emote: ReactionEmote, val time: Long, val reaction: MessageReaction)
    data class ListenerBundle(val adapter: ListenerAdapter, val messageQueue: LinkedBlockingQueue<Message>, val reactionQueue: LinkedBlockingQueue<ReactionTime>)
    data class ItemContainer(val url: String, val contextUrl: String, val title: String, val size: Dimension, val byteSize: Int)
    enum class MovementType { PREVIOUS, RANDOM, NEXT, FIRST, LAST, EXIT }

}




