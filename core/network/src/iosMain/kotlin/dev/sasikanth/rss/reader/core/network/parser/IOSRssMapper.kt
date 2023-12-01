/*
 * Copyright 2023 Sasikanth Miriyampalli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.sasikanth.rss.reader.core.network.parser

import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlOptions
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import dev.sasikanth.rss.reader.core.model.remote.FeedPayload
import dev.sasikanth.rss.reader.core.model.remote.PostPayload
import dev.sasikanth.rss.reader.core.network.parser.FeedParser.Companion.TAG_COMMENTS
import dev.sasikanth.rss.reader.core.network.parser.FeedParser.Companion.TAG_CONTENT_ENCODED
import dev.sasikanth.rss.reader.core.network.parser.FeedParser.Companion.TAG_DESCRIPTION
import dev.sasikanth.rss.reader.core.network.parser.FeedParser.Companion.TAG_IMAGE_URL
import dev.sasikanth.rss.reader.core.network.parser.FeedParser.Companion.TAG_LINK
import dev.sasikanth.rss.reader.core.network.parser.FeedParser.Companion.TAG_PUB_DATE
import dev.sasikanth.rss.reader.core.network.parser.FeedParser.Companion.TAG_TITLE
import io.ktor.http.Url
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.datetime.Clock

internal fun PostPayload.Companion.mapRssPost(
  rssMap: Map<String, String>,
  hostLink: String
): PostPayload? {
  val title = rssMap[TAG_TITLE]
  val pubDate = rssMap[TAG_PUB_DATE]
  val link = rssMap[TAG_LINK]
  var description = rssMap[TAG_DESCRIPTION]
  val encodedContent = rssMap[TAG_CONTENT_ENCODED]
  var imageUrl: String? = rssMap[TAG_IMAGE_URL]
  val commentsLink: String? = rssMap[TAG_COMMENTS]

  val descriptionToParse =
    if (encodedContent.isNullOrBlank()) {
      description
    } else {
      encodedContent
    }

  KsoupHtmlParser(
      handler =
        HtmlContentParser {
          if (imageUrl.isNullOrBlank()) imageUrl = it.imageUrl
          description = it.content.ifBlank { descriptionToParse?.trim() }
        },
      options = KsoupHtmlOptions(decodeEntities = false)
    )
    .parseComplete(descriptionToParse.orEmpty())

  if (title.isNullOrBlank() && description.isNullOrBlank()) {
    return null
  }

  val postPubDateInMillis =
    pubDate?.let { dateString -> dateString.dateStringToEpochMillis() }
      ?: run {
        Sentry.captureMessage("Failed to parse date: $pubDate")
        null
      }

  return PostPayload(
    title = FeedParser.cleanText(title, decodeUrlEncoding = true).orEmpty(),
    description = FeedParser.cleanTextCompact(description, decodeUrlEncoding = true).orEmpty(),
    link = FeedParser.cleanText(link)!!,
    imageUrl = FeedParser.safeUrl(hostLink, imageUrl),
    date = postPubDateInMillis ?: Clock.System.now().toEpochMilliseconds(),
    commentsLink = commentsLink?.trim()
  )
}

internal fun FeedPayload.Companion.mapRssFeed(
  feedUrl: String,
  rssMap: Map<String, String>,
  posts: List<PostPayload>
): FeedPayload {
  val link = rssMap[TAG_LINK]!!.trim()
  val domain = Url(link)
  val host =
    if (domain.host != "localhost") {
      domain.host
    } else {
      throw NullPointerException("Unable to get host domain")
    }
  val iconUrl = FeedParser.feedIcon(host)

  return FeedPayload(
    name = FeedParser.cleanText(rssMap[TAG_TITLE] ?: link, decodeUrlEncoding = true)!!,
    description = FeedParser.cleanText(rssMap[TAG_DESCRIPTION], decodeUrlEncoding = true).orEmpty(),
    homepageLink = link,
    link = feedUrl,
    icon = iconUrl,
    posts = posts
  )
}
