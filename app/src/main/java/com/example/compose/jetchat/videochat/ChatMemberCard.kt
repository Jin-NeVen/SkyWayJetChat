package com.example.compose.jetchat.videochat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.compose.jetchat.R
import com.example.compose.jetchat.videochat.data.Member
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.core.content.sink.SurfaceViewRenderer

@Composable
fun ChatMemberCard(
    member: Member,
    modifier: Modifier = Modifier
) {

    var videoRenderView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val videoStream = member.videoStream.value
    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box {
            if ((member.videoStream != null) && (videoStream is LocalVideoStream || videoStream is RemoteVideoStream)) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize(),
                    factory = { context ->
                        videoRenderView = SurfaceViewRenderer(context)
                        videoRenderView!!.apply {
                            setup()
                            if (videoStream is LocalVideoStream) {
                                videoStream.addRenderer(this)
                            } else if (videoStream is RemoteVideoStream) {
                                videoStream.addRenderer(this)
                            }
                        }
                    },
                    update = {
                        if (videoStream is LocalVideoStream) {
                            videoStream.removeRenderer(videoRenderView!!)
                            videoStream.addRenderer(videoRenderView!!)
                        } else if (videoStream is RemoteVideoStream) {
                            videoStream.removeRenderer(videoRenderView!!)
                            videoStream.addRenderer(videoRenderView!!)
                        }
                    }
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x99000000)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                member.name?.let {
                    Text(
                        text = if (member.isMe) "${it}(me)" else it,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatMemberCardPreview() {
    // drawable リソースが必要なため、適当な仮画像リソース名にしてください
    ChatMemberCard(
        member = Member("Alice", "12345", true),
        modifier = Modifier
            .width(400.dp)
            .height(300.dp),
    )
}