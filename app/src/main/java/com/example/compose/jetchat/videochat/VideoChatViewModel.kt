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
    var memberMe: LocalSFURoomMember? = null
    open var members by mutableStateOf(emptyList<Member>())

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

    private suspend fun captureMyVideoSteam(context: Context) {
        val me = members.find { it.id == MemberRepository.memberMeId }

        me?.let {
            Log.d(TAG, "captureLocalVideoSteam")
            val cameraList = CameraSource.getCameras(context).toList()
            cameraList.forEach {
                Log.d(TAG, "camera list: $it")
            }
            val cameraOption = CameraSource.CapturingOptions(400,300)
            if (cameraList.size >= 2) {
                //Front cameraを使う
                CameraSource.startCapturing(context, cameraList[1], cameraOption)
            } else {
                CameraSource.startCapturing(context, cameraList[0], cameraOption)
            }

            withContext(Dispatchers.Main) {
                it.videoStream.value = CameraSource.createStream()
            }
        }

    }
    private fun captureMyAudioStream() {
        val me = members.find { it.id == MemberRepository.memberMeId }
        me?.let {
            Log.d(TAG, "captureLocalAudioStream")
            AudioSource.start()
            it.audioStream.value = AudioSource.createStream()
        }
    }

    fun publishMyAVStream() {
        viewModelScope.launch {
            publishMyAVStreamInternal()
        }
    }

    private suspend fun publishMyAVStreamInternal() {
        val me = members.find { it.id == MemberRepository.memberMeId }
        if (memberMe == null || me == null) {
            Log.d(TAG, "member me is null")
            return
        }
        if (me.videoStream.value != null) {
            Log.d(TAG, "member me publish video")
            val publication = memberMe!!.publish(me.videoStream.value as LocalVideoStream)
            if (publication == null) {
                Log.d(TAG, "me publish video failed")
            } else {
                Log.d(TAG, "me publish video succeed")
                publication.onConnectionStateChangedHandler = {
                    Log.d(TAG, "me publish video connection state changed: $it")
                }
                publication.onSubscribedHandler = {
                    Log.d(TAG, "me publish video subscribed")
                }
                publication.onUnsubscribedHandler = {
                    Log.d(TAG, "publication onUnsubscribedHandler: me publish video unsubscribed")
                }
            }
        }
        if (me.audioStream != null) {
            Log.d(TAG, "me publish audio")
            val publication = memberMe!!.publish(me.audioStream.value as LocalAudioStream)
            if (publication == null) {
                Log.d(TAG, "me publish audio failed")
            } else {
                Log.d(TAG, "me publish audio succeed")
                publication.onConnectionStateChangedHandler = {
                    Log.d(TAG, "me publish audio connection state changed: $it")
                }
                publication.onSubscribedHandler = {
                    Log.d(TAG, "me publish audio subscribed")
                }
                publication.onUnsubscribedHandler = {
                    Log.d(TAG, "me publish audio unsubscribed")
                }
            }
        }
    }

    private var streamPublishedHandler: ((publication: RoomPublication) -> Unit)? = {
        Log.d(TAG, "gonna to subscribe stream by P2PRoom's streamPublishedHandler): publication id:${it.id}, publisher name: ${it.publisher?.name}")
        subscribeRoomMembersAVStreamInternal(it)
    }

    private fun subscribeRoomMembersAVStreamInternal(publication: RoomPublication) {
        viewModelScope.launch {
            if (chatRoom == null || memberMe == null) {
                return@launch
            }
            if (publication.publisher?.id == memberMe!!.id) {
                Log.d(TAG, "cancel this subscription since it is local publication.publisher name: ${publication.publisher?.name}")
                return@launch
            }
            val subscription = memberMe!!.subscribe(publication)
            if (subscription == null) {
                Log.d(TAG, "subscription is null")
                return@launch
            }
            if (subscription.stream == null) {
                Log.d(TAG, "subscription stream is null")
                return@launch
            }
            subscription.stream?.let { stream ->
                Log.d(TAG, "localP2PRoomMember subscription finished. subscription id: ${subscription.id}, subscription stream id: ${stream.id}, steam type: ${stream.contentType}")
                val targetMember = members.find { it.id == publication.publisher?.id }
                targetMember?.let { groupMember ->
                    if (stream.contentType == Stream.ContentType.VIDEO) {
                        withContext(Dispatchers.Main) {
                            groupMember.videoStream.value = subscription.stream as RemoteVideoStream
                        }
                    }
                    if (stream.contentType == Stream.ContentType.AUDIO) {
                        groupMember.audioStream.value = subscription.stream as RemoteAudioStream
                    }
                }
            }
        }
    }

    private fun subscribeRoomMembersAVStream() {
        if (chatRoom == null) {
            Log.d(TAG, "p2p room not created/found")
            return
        }

        chatRoom?.let { room ->
            room.publications.forEach { publication ->
                Log.d(TAG, "gonna to subscribe  ${publication.publisher?.name} 's ${publication.stream?.contentType} stream directly by p2pRoom publications id: ${publication.id},")
                subscribeRoomMembersAVStreamInternal(publication)
            }
            room.onStreamPublishedHandler = streamPublishedHandler
        }

        memberMe?.let { me ->
            me.onStreamUnpublishedHandler = { publication ->
                Log.d(TAG, "localP2PRoomMember streamUnpublishedHandler stream unpublished: ${publication.id}")

                val targetMember = members.find { it.id == publication.publisher?.id }

                //TODO confirm whether if stream.dispose is necessary
                targetMember?.let { groupMember ->
                    publication.stream?.let { stream ->
                        if (stream.contentType == Stream.ContentType.VIDEO) {
                            (groupMember.videoStream.value as RemoteVideoStream).removeAllRenderer()
                            groupMember.videoStream.value?.dispose()
                            groupMember.videoStream.value = null
                            Log.d(TAG, "remoteVideoStream disposed")
                        } else if (stream.contentType == Stream.ContentType.AUDIO) {
                            groupMember.audioStream.value?.dispose()
                            groupMember.audioStream.value = null
                            Log.d(TAG, "remoteAudioStream disposed")
                        }
                    }
                }
            }
        }
    }
}