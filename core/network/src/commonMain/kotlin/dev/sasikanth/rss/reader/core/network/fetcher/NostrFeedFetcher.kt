package dev.sasikanth.rss.reader.core.network.fetcher

import dev.sasikanth.rss.reader.core.network.parser.FeedParser
import io.ktor.client.HttpClient
import me.tatarka.inject.annotations.Inject

@Inject
class NostrFeedFetcher(private val httpClient: HttpClient, private val feedParser: FeedParser) :
    IFeedFetcher {
    override suspend fun fetch(url: String, transformUrl: Boolean): FeedFetchResult {
        TODO("Not yet implemented")
    }
}
