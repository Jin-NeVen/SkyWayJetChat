package com.example.compose.jetchat.videochat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.compose.jetchat.videochat.data.Member

@Composable
fun VideoChatRoom(
    viewModel: VideoChatViewModel,
    navController: NavController
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatMembersGrid(viewModel.members)
            Button(
                onClick = {
                    viewModel.leaveChatRoom()
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("退室")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VideoChatRoomPreview() {
    val dummyApp = Application()
    val mockViewModel = remember {
        object : VideoChatViewModel(dummyApp) {
            init {
                members = listOf(
                    Member(name = "Alice", id = "1"),
                    Member(name = "Bob", id = "2"),
                    Member(name = "Charlie", id = "3"),
                    Member(name = "Diana", id = "4"),
                    Member(name = "Jin", id = "5")
                )
            }
        }
    }
    // drawable リソースが必要なため、適当な仮画像リソース名にしてください
    VideoChatRoom(
        viewModel = mockViewModel,
        navController = NavController(dummyApp)
    )
}