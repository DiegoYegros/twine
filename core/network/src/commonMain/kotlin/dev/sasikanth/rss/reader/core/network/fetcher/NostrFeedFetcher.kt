package dev.sasikanth.rss.reader.core.network.fetcher

import co.touchlab.kermit.Logger
import dev.sasikanth.rss.reader.core.model.remote.FeedPayload
import dev.sasikanth.rss.reader.core.model.remote.PostPayload
import dev.sasikanth.rss.reader.core.network.nostr.NostrClient
import dev.sasikanth.rss.reader.core.network.nostr.NostrEvent
import dev.sasikanth.rss.reader.core.network.parser.FeedParser
import dev.sasikanth.rss.reader.util.decodeHTMLString
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.toList
import me.tatarka.inject.annotations.Inject

@Inject
class NostrFeedFetcher(
    private val httpClient: HttpClient,
    private val feedParser: FeedParser,
    private val nostrClient: NostrClient
) : IFeedFetcher {

    override suspend fun fetch(url: String, transformUrl: Boolean): FeedFetchResult {
        return try {
            if (!isNostrUrl(url)) {
                return FeedFetchResult.Error(UnsupportedOperationException("Not a Nostr URL"))
            }

            val pubkey = extractPubKey(url)
                ?: return FeedFetchResult.Error(IllegalArgumentException("Invalid Nostr identifier"))

            val events = nostrClient.fetchLongFormContent(pubkey).toList()
            if (events.isEmpty()) {
                return FeedFetchResult.Error(Exception("No articles found"))
            }

            val feed = convertNostrToFeed(events, url, pubkey)
            FeedFetchResult.Success(feed)
        } catch (e: Exception) {
            Logger.e(throwable = e) { "Failed to fetch Nostr feed" }
            FeedFetchResult.Error(e)
        }
    }

    private fun isNostrUrl(url: String): Boolean {
        return url.startsWith("nostr:") ||
                url.startsWith("npub") ||
                url.contains("@") ||
                url.startsWith("nprofile")
    }

    private fun extractPubKey(url: String): String? {
        return when {
            url.startsWith("npub") -> url
            url.contains("@") -> url
            url.startsWith("nprofile") -> url.substringAfter("nprofile1").takeIf { it.isNotEmpty() }
            url.startsWith("nostr:") -> url.substringAfter("nostr:")
            else -> null
        }
    }

    private fun convertNostrToFeed(
        events: List<NostrEvent>,
        feedUrl: String,
        pubkey: String
    ): FeedPayload {
        // Get author display info if available from first event
        val authorInfo = events.firstOrNull()?.let { event ->
            event.tags.find { it.getOrNull(0) == "p" }?.getOrNull(1)
        } ?: pubkey.take(8)

        return FeedPayload(
            name = "Nostr: $authorInfo".decodeHTMLString(),
            description = "Long-form content from Nostr author".decodeHTMLString(),
            icon = FeedParser.feedIcon("nos.lol"), // Using nos.lol as default icon source
            homepageLink = "nostr:$pubkey",
            link = feedUrl,
            posts = events.map { event -> convertNostrEventToPost(event) }
        )
    }

    private fun convertNostrEventToPost(event: NostrEvent): PostPayload {
        val title = event.tags.find { it.getOrNull(0) == "title" }?.getOrNull(1)
            ?: event.content.lineSequence().firstOrNull()
            ?: "Untitled"

        val summary = event.tags.find { it.getOrNull(0) == "summary" }?.getOrNull(1)
        val imageUrl = event.tags.find { it.getOrNull(0) == "image" }?.getOrNull(1)
        val publishedAt = event.tags.find { it.getOrNull(0) == "published_at" }
            ?.getOrNull(1)?.toLongOrNull()
            ?: event.createdAt

        return PostPayload(
            link = "nostr:${event.id}",
            title = FeedParser.cleanText(title)!!.decodeHTMLString(),
            description = FeedParser.cleanText(summary ?: event.content.take(500))
                .orEmpty()
                .decodeHTMLString(),
            rawContent = event.content,
            imageUrl = imageUrl,
            date = publishedAt,
            commentsLink = null // Nostr doesn't have a direct comments concept yet
        )
    }

    companion object {
        private const val PREVIEW_LENGTH = 500
    }
}