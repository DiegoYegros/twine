package dev.sasikanth.rss.reader.core.network.nostr

import Bech32
import co.touchlab.kermit.Logger
import dev.sasikanth.rss.reader.util.DispatchersProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.tatarka.inject.annotations.Inject

@Serializable
data class NostrEvent(
    val id: String = "",
    val pubkey: String = "",
    @SerialName("created_at")
    val createdAt: Long = 0,
    val kind: Int = 0,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
    val sig: String = ""
) {
    val title: String
        get() = tags.find { it.getOrNull(0) == "title" }?.getOrNull(1)
            ?: content.lineSequence().firstOrNull()
            ?: "Untitled"

    val summary: String?
        get() = tags.find { it.getOrNull(0) == "summary" }?.getOrNull(1)

}

@Serializable
data class NostrProfile(
    val name: String? = null,
    val display_name: String? = null,
    val about: String? = null,
    val picture: String? = null,
    val nip05: String? = null
) {
    val displayName: String
        get() = display_name ?: name ?: "Unknown"
}

@Inject
class NostrClient(
    httpClient: HttpClient,
    private val dispatchersProvider: DispatchersProvider
) {
    private var subscriptionCounter = 0

    private val client = httpClient.config {
        install(WebSockets) {
            pingInterval = 30_000
            maxFrameSize = Long.MAX_VALUE

        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    fun fetchLongFormContent(pubkey: String): Flow<NostrEvent> = channelFlow {
        Logger.i { "Starting NostrClient.fetchLongFormContent with pubkey: $pubkey" }
        val hexPubkey = Bech32.decode(pubkey).getOrNull()
        val seenEvents = mutableSetOf<String>()
        withContext(dispatchersProvider.io) {
            coroutineScope {
                val relayJobs = RELAYS.map { relay ->
                    async {
                        try {
                            client.webSocket(relay) {
                                subscriptionCounter = subscriptionCounter + 1;
                                val subId = "sub${subscriptionCounter}"
                                val filter = buildJsonObject {
                                    put("kinds", buildJsonArray {
                                        add(KIND_LONG_FORM)
                                    })
                                    put("authors", buildJsonArray {
                                        add(hexPubkey)
                                    })
                                }

                                val subscribeMessage = buildJsonArray {
                                    add("REQ")  // Command type
                                    add(subId)  // Subscription ID
                                    add(filter)
                                }
                                Logger.i { "[$relay] Starting subscription $subId: $subscribeMessage" }
                                send(Frame.Text(subscribeMessage.toString()))
                                try {
                                    for (frame in incoming) {
                                        when (frame) {
                                            is Frame.Text -> {
                                                val text = frame.readText()
                                                try {
                                                    val element =
                                                        json.parseToJsonElement(text).jsonArray
                                                    when (val type =
                                                        element[0].jsonPrimitive.content) {
                                                        "EVENT" -> {
                                                            if (element[1].jsonPrimitive.content == subId) {
                                                                val event =
                                                                    json.decodeFromJsonElement<NostrEvent>(
                                                                        element[2]
                                                                    )
                                                                if (event.kind == KIND_LONG_FORM) {
                                                                    // Only emit if we haven't seen this event before
                                                                    if (seenEvents.add(event.id)) {
                                                                        Logger.i { "[$relay:$subId] New event received: $event" }
                                                                        send(event)
                                                                    } else {
                                                                        Logger.i { "[$relay:$subId] Duplicate event ignored: $event" }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        "EOSE" -> {
                                                            if (element[1].jsonPrimitive.content == subId) {
                                                                Logger.i { "[$relay:$subId] End of stored events" }
                                                                // Send CLOSE after receiving EOSE
                                                                val closeMessage = buildJsonArray {
                                                                    add("CLOSE")
                                                                    add(subId)
                                                                }
                                                                send(Frame.Text(closeMessage.toString()))
                                                                break
                                                            }
                                                        }

                                                        "NOTICE" -> {
                                                            Logger.w {
                                                                "[$relay:$subId] Notice: ${
                                                                    element.getOrNull(
                                                                        1
                                                                    )?.jsonPrimitive?.content
                                                                }"
                                                            }
                                                        }

                                                        else -> {
                                                            Logger.i { "[$relay:$subId] Unknown message type: $type" }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Logger.e(throwable = e) { "[$relay:$subId] Error parsing message: $text" }
                                                }
                                            }

                                            else -> {
                                                Logger.i { "[$relay:$subId] Received non-text frame: $frame" }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Logger.e(throwable = e) { "[$relay] Error in WebSocket message loop: ${e.message}" }
                                    throw e
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e(throwable = e) { "[$relay] Connection failed: ${e.message}" }
                        }
                    }
                }

                relayJobs.awaitAll()
            }
        }
    }
    suspend fun fetchNostrProfile(pubkey: String): NostrProfile {
        Logger.i { "Starting NostrClient.fetchNostrProfile with pubkey: $pubkey" }
        val hexPubkey = Bech32.decode(pubkey).getOrNull()

        var latestProfile: NostrProfile = NostrProfile()
        var latestTimestamp = 0L

        withContext(dispatchersProvider.io) {
            coroutineScope {
                val relayJobs = RELAYS.map { relay ->
                    async {
                        try {
                            client.webSocket(relay) {
                                subscriptionCounter = subscriptionCounter + 1
                                val subId = "profile${subscriptionCounter}"
                                val filter = buildJsonObject {
                                    put("kinds", buildJsonArray {
                                        add(KIND_METADATA)
                                    })
                                    put("authors", buildJsonArray {
                                        add(hexPubkey)
                                    })
                                }

                                val subscribeMessage = buildJsonArray {
                                    add("REQ")
                                    add(subId)
                                    add(filter)
                                }

                                Logger.i { "[$relay] Starting profile subscription $subId: $subscribeMessage" }
                                send(Frame.Text(subscribeMessage.toString()))

                                try {
                                    for (frame in incoming) {
                                        when (frame) {
                                            is Frame.Text -> {
                                                val text = frame.readText()
                                                try {
                                                    val element = json.parseToJsonElement(text).jsonArray
                                                    when (val type = element[0].jsonPrimitive.content) {
                                                        "EVENT" -> {
                                                            if (element[1].jsonPrimitive.content == subId) {
                                                                val event = json.decodeFromJsonElement<NostrEvent>(element[2])
                                                                if (event.kind == KIND_METADATA && event.createdAt > latestTimestamp) {
                                                                    try {
                                                                        val profile = json.decodeFromString<NostrProfile>(event.content)
                                                                        Logger.i { "[$relay:$subId] New profile received with timestamp ${event.createdAt}" }
                                                                        latestProfile = profile
                                                                    } catch (e: Exception) {
                                                                        Logger.e(throwable = e) { "[$relay:$subId] Error parsing profile content: ${event.content}" }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        "EOSE" -> {
                                                            if (element[1].jsonPrimitive.content == subId) {
                                                                Logger.i { "[$relay:$subId] End of stored events" }
                                                                val closeMessage = buildJsonArray {
                                                                    add("CLOSE")
                                                                    add(subId)
                                                                }
                                                                send(Frame.Text(closeMessage.toString()))
                                                                break
                                                            }
                                                        }

                                                        "NOTICE" -> {
                                                            Logger.w {
                                                                "[$relay:$subId] Notice: ${element.getOrNull(1)?.jsonPrimitive?.content}"
                                                            }
                                                        }

                                                        else -> {
                                                            Logger.i { "[$relay:$subId] Unknown message type: $type" }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Logger.e(throwable = e) { "[$relay:$subId] Error parsing message: $text" }
                                                }
                                            }

                                            else -> {
                                                Logger.i { "[$relay:$subId] Received non-text frame: $frame" }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Logger.e(throwable = e) { "[$relay] Error in WebSocket message loop: ${e.message}" }
                                    throw e
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e(throwable = e) { "[$relay] Connection failed: ${e.message}" }
                        }
                    }
                }

                relayJobs.awaitAll()
            }
        }
        Logger.i("Returning profile: $latestProfile")
        return latestProfile
    }
    companion object {
        const val KIND_LONG_FORM = 30023
        const val KIND_METADATA = 0
        private val RELAYS = listOf(
            "wss://nos.lol",
            "wss://relay.damus.io",
            "wss://relay.primal.net",
            "wss://relay.nostr.band"
        )
    }
}
