package com.example.compose.jetchat.videochat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compose.jetchat.videochat.data.Member
import com.example.compose.jetchat.videochat.data.MemberRepository
import com.ntt.skyway.core.SkyWayContext
import com.ntt.skyway.core.content.Stream
import com.ntt.skyway.core.content.local.LocalAudioStream
import com.ntt.skyway.core.content.local.LocalStream
import com.ntt.skyway.core.content.local.LocalVideoStream
import com.ntt.skyway.core.content.local.source.AudioSource
import com.ntt.skyway.core.util.Logger
import com.ntt.skyway.room.sfu.LocalSFURoomMember
import com.ntt.skyway.room.sfu.SFURoom
import com.ntt.skyway.core.content.local.source.CameraSource
import com.ntt.skyway.core.content.remote.RemoteAudioStream
import com.ntt.skyway.core.content.remote.RemoteVideoStream
import com.ntt.skyway.room.RoomPublication
import com.ntt.skyway.room.member.RoomMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class VideoChatViewModel(
    private val applicationContext: Application
): AndroidViewModel(applicationContext) {
    companion object {
        val TAG = "VideoChatViewModel"
    }


    init {
        viewModelScope.launch {
            initializeSkyWay(applicationContext)
        }
    }

    private suspend fun initializeSkyWay(applicationContext: Context) {
        // ServerよりSkyWay Auth Tokenを取得し、SkyWayContext.Optionsにセット
        val option = SkyWayContext.Options(
            authToken = TODO("SkyWay初期化するにはAuthTokenが必要です、AuthTokenの生成方法はこちらご覧ください：https://skyway.ntt.com/ja/docs/user-guide/android-sdk/quickstart-compose/#68"),
            logLevel = Logger.LogLevel.VERBOSE
        )

        SkyWayContext.onErrorHandler = { error ->
            Log.d(TAG, "skyway setup failed: ${error.message}")
        }
        if (SkyWayContext.setup(applicationContext, option)) {
            Log.d(TAG, "skyway setup succeed")
            createRoom()
            createMemberMeAndJoinChatRoom()
            captureMyVideoSteam(applicationContext)
            captureMyAudioStream()
            publishMyAVStream()
            subscribeRoomMembersAVStream()
        }
    }
}