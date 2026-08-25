package com.renovation.ledger.ui.common

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.renovation.ledger.data.profile.AvatarUrls
import com.renovation.ledger.di.ServerEndpoint
import dagger.hilt.EntryPoints
import java.io.File

@Composable
fun ProfileAvatar(
    avatarPath: String?,
    size: Dp = 56.dp,
    contentDescription: String = "头像",
) {
    val context = LocalContext.current
    val baseUrl = remember {
        EntryPoints.get(
            context.applicationContext as Application,
            ServerEndpointEntryPoint::class.java,
        ).serverEndpoint().baseUrl
    }
    val model = remember(avatarPath, baseUrl) {
        val path = avatarPath?.trim()?.takeIf { it.isNotEmpty() } ?: return@remember null
        AvatarUrls.absoluteUrl(path, baseUrl)
            ?: path.takeIf { File(it).isFile }
    }
    if (model != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = "默认头像",
            modifier = Modifier.size(size * 0.45f),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ServerEndpointEntryPoint {
    fun serverEndpoint(): ServerEndpoint
}
