package com.app.nisisiafrica.data.Model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Community(
    @DocumentId var id: String = "",
    var name: String = "",
    var description: String = "",
    var createdBy: String = "",
    var iconUrl: String = "",
    var bannerUrl: String = "",
    var memberCount: Long = 0,
    var postCount: Long = 0,
    var recentMemberAvatars: List<String> = emptyList(),
    @ServerTimestamp var createdAt: Date? = null
)

data class CommunityEvent(
    @DocumentId var id: String = "",
    var title: String = "",
    var description: String = "",
    var location: String = "",
    var meetingLink: String = "",
    var mode: String = "physical",
    var startsAt: Long = 0,
    var endTime: String = "",
    var program: String = "",
    var seats: Int = 0,
    var price: String = "",
    var createdBy: String = "",
    var createdByName: String = "",
    @ServerTimestamp var createdAt: Date? = null
)

data class CommunityPost(
    @DocumentId var id: String = "",
    var title: String = "",
    var body: String = "",
    var imageUrl: String = "",
    var authorId: String = "",
    var authorName: String = "",
    var upvoteCount: Long = 0,
    var commentCount: Long = 0,
    @ServerTimestamp var createdAt: Date? = null
)

data class PostComment(
    @DocumentId var id: String = "",
    var body: String = "",
    var authorId: String = "",
    var authorName: String = "",
    var parentId: String = "",
    var likeCount: Long = 0,
    @ServerTimestamp var createdAt: Date? = null
)
