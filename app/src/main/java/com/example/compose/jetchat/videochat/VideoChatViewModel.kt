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

    var chatRoom: SFURoom? = null

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

    private suspend fun createRoom() {
        Log.d(TAG, "createRoom")
        /**
         * 必要に応じてチャットRoomの名前を変えてください
         */
        val directChatRoomName = "VideoChatRoom"
        chatRoom = SFURoom.findOrCreate(directChatRoomName)
        if (chatRoom == null) {
            Log.d(TAG, "failed to create/find chat room")
            return
        }
        Log.d(TAG, "chat room created/found")
        chatRoom?.let { room ->
            room.onMemberListChangedHandler = {
                Log.d(TAG, "member list changed")
                //ここでmemberを取得してlistを更新する
                syncMembers(room.members)

                room.onMemberJoinedHandler = { member ->
                    Log.d(TAG, " member ${member.name} joined")
                }
                room.onMemberLeftHandler = { member ->
                    Log.d(TAG, "member ${member.name} left")
                }
                room.onPublicationListChangedHandler = {
                    Log.d(TAG, "publication list changed")
                }
                room.onSubscriptionListChangedHandler = {
                    Log.d(TAG, "subscription list changed")
                }
                room.onStreamUnpublishedHandler = {
                    Log.d(TAG, "p2pRoom streamUnpublishedHandler stream unpublished: ${it.id}")
                }
            }
        }
    }

    private fun syncMembers(newMembers: Set<RoomMember>) {
        // 既存メンバーをマップ化しておく
        val existingMap = members.associateBy { it.id }

        // 新しいリストのIDだけ抽出
        val newIds = newMembers.map { it.id }.toSet()

        // 1. 削除対象
        val toDelete = members.filter { it.id !in newIds }

        // 2. 追加対象
        val toAdd = newMembers
            .filter { it.id !in existingMap }
            .map { newMember ->
                Member(
                    name = newMember.name,
                    id = newMember.id,
                    isMe = false,
                    videoStream = mutableStateOf(null),
                    audioStream = mutableStateOf(null)
                )
            }

        // 3. 残すメンバー
        val toKeep = members.filter { it.id in newIds }

        // 4. 更新後のリストを作成（追加 + 残す）
        members = (toKeep + toAdd)
    }

    private suspend fun createMemberMeAndJoinChatRoom() {
        if (chatRoom == null) {
            Log.d(TAG, "p2p room not created/found")
            return
        }

        chatRoom?.let {
            memberMe = it.join(RoomMember.Init(MemberRepository.memberMeName))
            if (memberMe == null) {
                Log.d(TAG, "member me join chat room failed")
            } else {
                MemberRepository.memberMeId = memberMe!!.id
                if (members.find { it.id == MemberRepository.memberMeId } == null) {
                    members = members + Member(name = memberMe!!.name, id = memberMe!!.id, isMe = true)
                    Log.d(TAG, "member me(${MemberRepository.memberMeName}) join chat room succeed")
                } else {
                    Log.d(TAG, "member me already joined chat room")
                }
            }
        }
    }
}