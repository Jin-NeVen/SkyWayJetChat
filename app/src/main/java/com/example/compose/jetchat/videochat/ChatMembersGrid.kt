package com.example.compose.jetchat.videochat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.compose.jetchat.videochat.data.Member

@Composable
fun ChatMembersGrid(
    members: List<Member>,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .height(312.dp)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = {
            items(members.size) { index ->
                ChatMemberCard(
                    member = members[index],
                    modifier = Modifier
                        .height(150.dp),
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ChatMembersGridPreview() {
    val dummyMembers = listOf(
        Member(name = "Alice", id = "1"),
        Member(name = "Bob", id = "2", true),
        Member(name = "Charlie", id = "3"),
        Member(name = "Diana", id = "4"),
        Member(name = "Encore", id = "5"),
    )

    ChatMembersGrid(
        members = dummyMembers,
        modifier = Modifier.fillMaxSize()
    )
}
