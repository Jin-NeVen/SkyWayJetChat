package com.example.compose.jetchat.videochat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.GlobalScope
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
        val option = SkyWayContext.Options(
            logLevel = Logger.LogLevel.VERBOSE,
            webRTCLog = false,
            enableHardwareCodec = true,
        )

        SkyWayContext.onErrorHandler = { error ->
            Log.d(TAG, "skyway setup failed: ${error.message}")
        }
        if (SkyWayContext.setupForDev(applicationContext, TODO("AppID"), TODO("SecretKey"), option)) {
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
        Log.d(TAG, "create video chat room")
        /**
         * NOTICE
         * 必要に応じてチャットRoomの名前を変えてください
         */
        val chatRoomName = "VideoChatRoom"
        chatRoom = SFURoom.findOrCreate(chatRoomName)
        if (chatRoom == null) {
            Log.d(TAG, "failed to create/find video chat room")
            return
        }
        Log.d(TAG, "video chat room created/found")
        chatRoom?.let { room ->
            room.onMemberListChangedHandler = {
                Log.d(TAG, "member list changed")
                //ここでmemberを取得してlistを更新する
                syncMembers(room.members)
            }
            room.onMemberJoinedHandler = { member ->
                Log.d(TAG, " member ${member.name} joined")
            }
            room.onMemberLeftHandler = { member ->
                Log.d(TAG, "member ${member.name} left")
            }
        }
    }

    private fun syncMembers(newMembers: Set<RoomMember>) {
        // 既存メンバーをマップ化
        val existingMap = members.associateBy { it.id }

        // 新しいmember list のIDだけ抽出
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

        // 3. 残すmember
        val toKeep = members.filter { it.id in newIds }

        // 4. 更新後のmember listを作成（追加 + 残す）
        members = (toKeep + toAdd)
    }

    private suspend fun createMemberMeAndJoinChatRoom() {
        if (chatRoom == null) {
            Log.d(TAG, "video chat room not created/found")
            return
        }

        chatRoom?.let {
            memberMe = it.join(RoomMember.Init(MemberRepository.memberMeName))
            if (memberMe == null) {
                Log.d(TAG, "member me join video chat room failed")
            } else {
                MemberRepository.memberMeId = memberMe!!.id
                if (members.find { it.id == MemberRepository.memberMeId } == null) {
                    members = members + Member(name = memberMe!!.name, id = memberMe!!.id, isMe = true)
                    Log.d(TAG, "member me(${MemberRepository.memberMeName}) join video chat room succeed")
                } else {
                    Log.d(TAG, "member me already joined video chat room")
                }
            }
        }
    }

    private suspend fun captureMyVideoSteam(context: Context) {
        val me = members.find { it.id == MemberRepository.memberMeId }

        me?.let {
            Log.d(TAG, "capture my video stream")
            val cameraList = CameraSource.getCameras(context).toList()
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
            Log.d(TAG, "capture my audio stream")
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
                Log.d(TAG, "member me publish video failed")
            } else {
                Log.d(TAG, "member me publish video succeed")
            }
        }
        if (me.audioStream.value != null) {
            Log.d(TAG, "member me publish audio")
            val publication = memberMe!!.publish(me.audioStream.value as LocalAudioStream)
            if (publication == null) {
                Log.d(TAG, "member me publish audio failed")
            } else {
                Log.d(TAG, "member me publish audio succeed")
            }
        }
    }

    private var streamPublishedHandler: ((publication: RoomPublication) -> Unit)? = {
        Log.d(TAG, "subscribe stream: publication id:${it.id}, publisher name: ${it.publisher?.name}")
        subscribeRoomMembersAVStreamInternal(it)
    }

    private fun subscribeRoomMembersAVStreamInternal(publication: RoomPublication) {
        viewModelScope.launch {
            if (chatRoom == null || memberMe == null) {
                return@launch
            }
            if (publication.publisher?.id == memberMe!!.id) {
                Log.d(TAG, "ignore my own publication")
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
                Log.d(TAG, "subscription finished. subscription id: ${subscription.id}, subscription stream id: ${stream.id}, steam type: ${stream.contentType}")
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
            Log.d(TAG, "video chat room not created/found")
            return
        }

        chatRoom?.let { room ->
            room.publications.forEach { publication ->
                Log.d(TAG, "subscribe  ${publication.publisher?.name} 's ${publication.stream?.contentType} stream directly by publications id: ${publication.id},")
                subscribeRoomMembersAVStreamInternal(publication)
            }
            room.onStreamPublishedHandler = streamPublishedHandler
        }
    }
    fun leaveChatRoom() {
        /**
         * [NOTICE]
         * SkyWayの退室処理は suspend 関数であり、完了までに時間がかかる可能性があります。
         * 一方で、ViewModelScope の終了とともに VideoModelScope もキャンセルされるため、
         * VideoModelScope 内で suspend 処理を実行すると、その処理が途中でキャンセルされてしまう場合があります。
         *
         * そのため、ViewModelScope よりも長く生存する CoroutineScope の利用が必要です。
         *
         * ここでは便宜上 GlobalScope を使用していますが、GlobalScope の利用は推奨されておらず、
         * 本番環境では適切なスコープ設計（例：アプリケーションスコープ等）を検討してください。
         */
        GlobalScope.launch {
            if (memberMe != null) {
                memberMe!!.leave()
            }
            members = emptyList()
            chatRoom?.dispose()
            SkyWayContext.dispose()
            Log.d(TAG, "leaveChatRoom succeed")
        }
    }
}