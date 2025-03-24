package com.example.compose.jetchat.videochat.data

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.ntt.skyway.core.content.Stream
import com.ntt.skyway.core.content.remote.RemoteAudioStream
import com.ntt.skyway.core.content.remote.RemoteVideoStream

data class Member(
    var name: String?,
    var id: String,
    var isMe: Boolean = false,
    /**
     * NOTICE!
     * UI logicとmodel混在するため避けるべき設計です。
     * 便宜上サンプルアプリでは実装の軽い方を採用していますが、
     * Productionコードではそうしないようにご注意ください。
     */
    var videoStream: MutableState<Stream?> = mutableStateOf(null),
    var audioStream: MutableState<Stream?> = mutableStateOf(null),
)

