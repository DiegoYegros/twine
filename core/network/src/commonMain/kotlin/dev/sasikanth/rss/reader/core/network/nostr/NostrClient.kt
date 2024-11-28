package dev.sasikanth.rss.reader.core.network.nostr

import co.touchlab.kermit.Logger
import dev.sasikanth.rss.reader.util.DispatchersProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
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
}

@Inject
class NostrClient(
    httpClient: HttpClient,
    private val dispatchersProvider: DispatchersProvider
) {
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

    fun fetchLongFormContent(pubkey: String? = null): Flow<NostrEvent> = channelFlow {
        withContext(dispatchersProvider.io) {
            coroutineScope {
                val relayJobs = RELAYS.map { relay ->
                    async {
                        try {
                            client.webSocket(relay) {
                                val filter = buildJsonObject {
                                    put("kinds", buildJsonArray { add(KIND_LONG_FORM) })
                                    put("limit", 10)
                                    pubkey?.let {
                                        put("authors", buildJsonArray { add(it) })
                                    }
                                }

                                val subscribeMessage = buildJsonArray {
                                    add("REQ")
                                    add("articles-${relay.hashCode()}")
                                    add(filter)
                                }

                                Logger.d { "Connecting to relay: $relay" }
                                send(Frame.Text(subscribeMessage.toString()))

                                for (frame in incoming) {
                                    when (frame) {
                                        is Frame.Text -> {
                                            val text = frame.readText()
                                            Logger.d { "Received from $relay: $text" }

                                            try {
                                                val element = json.parseToJsonElement(text).jsonArray
                                                when (element[0].jsonPrimitive.content) {
                                                    "EVENT" -> {
                                                        val event = json.decodeFromJsonElement<NostrEvent>(element[2])
                                                        if (event.kind == KIND_LONG_FORM) {
                                                            send(event)
                                                        }
                                                    }
                                                    "EOSE" -> {
                                                        Logger.d { "End of stored events from $relay" }
                                                    }
                                                    "NOTICE" -> {
                                                        Logger.w { "Relay $relay notice: ${element.getOrNull(1)?.jsonPrimitive?.content}" }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Logger.e(throwable = e) { "Error parsing Nostr event from $relay" }
                                            }
                                        }
                                        is Frame.Close -> {
                                            Logger.w { "WebSocket closed for $relay: ${frame.readReason()}" }
                                            break
                                        }
                                        else -> {
                                            Logger.d { "Received other frame type from $relay: $frame" }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e(throwable = e) { "Failed to connect to relay: $relay" }
                        }
                    }
                }

                relayJobs.awaitAll()
            }
        }
    }

    companion object {
        const val KIND_LONG_FORM = 30023
        private val RELAYS = listOf(
            "wss://nos.lol",
            "wss://relay.damus.io",
            "wss://relay.primal.net"
        )
    }
}