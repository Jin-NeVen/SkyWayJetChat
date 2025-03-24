package com.example.compose.jetchat.videochat.data

import java.util.Random

object MemberRepository {
    private val names = listOf(
        "Alice", "Bob", "Charlie", "Diana", "Eve",
        "Frank", "Grace", "Hank", "Ivy", "Jack",
        "Karen", "Leo", "Mona", "Nate", "Olivia",
        "Paul", "Quinn", "Rachel", "Steve", "Tina",
        "Uma", "Victor", "Wendy", "Xander", "Yara",
        "Zack", "Aaron", "Bella", "Cody", "Daisy"
    )

    val memberMeName = "${names.random()}_${Random().nextInt(1000)}"
    var memberMeId = ""
}