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
package dev.sasikanth.rss.reader.home.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.sasikanth.rss.reader.resources.IconResources
import dev.sasikanth.rss.reader.resources.strings.LocalStrings

@Composable
internal actual fun PostOptionShareIconButton(postLink: String) {
  val context = LocalContext.current
  PostOptionIconButton(
    iconRes = IconResources.share,
    contentDescription = LocalStrings.current.share,
    onClick = {
      val sendIntent =
        Intent().apply {
          action = Intent.ACTION_SEND
          putExtra(Intent.EXTRA_TEXT, postLink)
          type = "text/plain"
        }
      val shareIntent = Intent.createChooser(sendIntent, null)
      context.startActivity(shareIntent)
    }
  )
}
