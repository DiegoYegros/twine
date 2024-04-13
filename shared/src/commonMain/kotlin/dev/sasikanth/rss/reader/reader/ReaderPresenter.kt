/*
 * Copyright 2024 Sasikanth Miriyampalli
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

package dev.sasikanth.rss.reader.reader

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import dev.sasikanth.readability.Readability
import dev.sasikanth.rss.reader.core.network.post.PostSourceFetcher
import dev.sasikanth.rss.reader.reader.ReaderState.PostMode.Idle
import dev.sasikanth.rss.reader.reader.ReaderState.PostMode.InProgress
import dev.sasikanth.rss.reader.reader.ReaderState.PostMode.RssContent
import dev.sasikanth.rss.reader.reader.ReaderState.PostMode.Source
import dev.sasikanth.rss.reader.repository.RssRepository
import dev.sasikanth.rss.reader.util.DispatchersProvider
import dev.sasikanth.rss.reader.util.relativeDurationString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

internal typealias ReaderPresenterFactory =
  (
    postId: String,
    ComponentContext,
    goBack: () -> Unit,
  ) -> ReaderPresenter

@Inject
class ReaderPresenter(
  dispatchersProvider: DispatchersProvider,
  private val rssRepository: RssRepository,
  private val postSourceFetcher: PostSourceFetcher,
  @Assisted private val postId: String,
  @Assisted componentContext: ComponentContext,
  @Assisted private val goBack: () -> Unit
) : ComponentContext by componentContext {

  private val presenterInstance =
    instanceKeeper.getOrCreate {
      PresenterInstance(
        dispatchersProvider = dispatchersProvider,
        rssRepository = rssRepository,
        postId = postId,
        postSourceFetcher = postSourceFetcher
      )
    }

  init {
    lifecycle.doOnCreate { presenterInstance.dispatch(ReaderEvent.Init(postId)) }
    lifecycle.doOnDestroy { presenterInstance.dispatch(ReaderEvent.MarkPostAsRead) }
  }

  internal val state = presenterInstance.state

  fun dispatch(event: ReaderEvent) {
    when (event) {
      ReaderEvent.BackClicked -> goBack()
      else -> {
        // no-op
      }
    }

    presenterInstance.dispatch(event)
  }

  private class PresenterInstance(
    private val dispatchersProvider: DispatchersProvider,
    private val rssRepository: RssRepository,
    private val postId: String,
    private val postSourceFetcher: PostSourceFetcher,
  ) : InstanceKeeper.Instance {

    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatchersProvider.main)

    private val _state = MutableStateFlow(ReaderState.default(postId))
    val state: StateFlow<ReaderState> =
      _state.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderState.default(postId)
      )

    fun dispatch(event: ReaderEvent) {
      when (event) {
        is ReaderEvent.Init -> init(event.postId)
        ReaderEvent.BackClicked -> {
          /* no-op */
        }
        ReaderEvent.TogglePostBookmark -> togglePostBookmark(postId)
        ReaderEvent.ArticleShortcutClicked -> articleShortcutClicked()
        ReaderEvent.MarkPostAsRead -> markPostAsRead(postId)
      }
    }

    private fun markPostAsRead(postId: String) {
      coroutineScope.launch { rssRepository.updatePostReadStatus(read = true, id = postId) }
    }

    private fun togglePostBookmark(postId: String) {
      coroutineScope.launch {
        val isBookmarked = state.value.isBookmarked ?: false
        rssRepository.updateBookmarkStatus(bookmarked = !isBookmarked, id = postId)
        _state.update { it.copy(isBookmarked = !isBookmarked) }
      }
    }

    private fun init(postId: String) {
      coroutineScope.launch {
        val post = rssRepository.post(postId)
        val feed = rssRepository.feedBlocking(post.feedLink)

        _state.update {
          it.copy(
            title = post.title,
            publishedAt = post.date.relativeDurationString(),
            isBookmarked = post.bookmarked,
            feed = feed,
            postImage = post.imageUrl
          )
        }

        if (feed.alwaysFetchSourceArticle) {
          loadSourceArticle()
        } else {
          loadRssContent()
        }
      }
    }

    private suspend fun extractArticleHtmlContent(postId: String, content: String): String {
      val post = rssRepository.post(postId)
      val article =
        withContext(dispatchersProvider.io) { Readability(post.id, content).parse() }
          ?: return content
      val articleContent = article.content

      if (articleContent.isNullOrBlank()) return content

      return articleContent
    }

    private fun articleShortcutClicked() {
      coroutineScope.launch {
        val currentPostMode = _state.value.postMode
        when (currentPostMode) {
          RssContent -> loadSourceArticle()
          Source -> loadRssContent()
          InProgress,
          Idle -> {
            // no-op
          }
        }
      }
    }

    private suspend fun loadRssContent() {
      _state.update { it.copy(postMode = InProgress) }
      val post = rssRepository.post(postId)
      val postContent = post.rawContent ?: post.description
      val htmlContent = extractArticleHtmlContent(postId, postContent)
      _state.update { it.copy(content = htmlContent, postMode = RssContent) }
    }

    private suspend fun loadSourceArticle() {
      _state.update { it.copy(postMode = InProgress) }
      val content = postSourceFetcher.fetch(postId)

      if (content.isSuccess) {
        val htmlContent = extractArticleHtmlContent(postId, content.getOrThrow())
        _state.update { it.copy(content = htmlContent) }
      } else {
        loadRssContent()
      }

      _state.update { it.copy(postMode = Source) }
    }
  }
}
