package cn.a10miaomiao.bilimiao.compose.pages.message.commponents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.a10miaomiao.bilimiao.comm.utils.NumberUtil
import com.a10miaomiao.bilimiao.comm.utils.UrlUtil
import com.skydoves.landscapist.glide.GlideImage

@Composable
internal fun MessageItemBox(
    avatar: String,
    nickname: String,
    actionText: String,
    title: String,
    sourceContent: String,
    time: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
    ) {
        GlideImage(
            imageModel = UrlUtil.autoHttps(avatar) + "@200w_200h",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(nickname)
                        }
                        append(actionText)
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            if (sourceContent.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(bottom = 5.dp),
                    text = sourceContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row {
                Text(
                    text = NumberUtil.converCTime(time),
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            modifier = Modifier.width(60.dp),
            text = title,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}