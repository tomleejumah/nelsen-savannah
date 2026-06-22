package com.app.nisisiafrica.data.Repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Firestore-backed repository for the Reddit-style communities feature.
 *
 * Layout:
 *   communities/{id}
 *     members/{uid}
 *     posts/{postId}
 *       votes/{uid}
 *       comments/{commentId}
 */
class CommunityRepository {

    // Java-friendly callback interfaces.
    fun interface ResultCallback {
        fun onResult(success: Boolean, idOrError: String?)
    }

    fun interface BoolCallback {
        fun onResult(value: Boolean)
    }

    fun interface ToggleCallback {
        fun onResult(success: Boolean, nowUpvoted: Boolean)
    }

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun communities() = db.collection("communities")

    fun communitiesQuery(): Query =
        communities().orderBy("memberCount", Query.Direction.DESCENDING)

    fun postsQuery(communityId: String): Query =
        communities().document(communityId).collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)

    fun commentsQuery(communityId: String, postId: String): Query =
        communities().document(communityId).collection("posts").document(postId)
            .collection("comments").orderBy("createdAt", Query.Direction.ASCENDING)

    fun communityRef(communityId: String): DocumentReference =
        communities().document(communityId)

    fun postRef(communityId: String, postId: String): DocumentReference =
        communities().document(communityId).collection("posts").document(postId)

    fun createCommunity(
        name: String,
        description: String,
        iconUrl: String,
        callback: ResultCallback
    ) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false, "Not signed in")
        val data = hashMapOf(
            "name" to name,
            "description" to description,
            "createdBy" to uid,
            "iconUrl" to iconUrl,
            "memberCount" to 0L,
            "postCount" to 0L,
            "recentMemberAvatars" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        communities().add(data)
            .addOnSuccessListener { callback.onResult(true, it.id) }
            .addOnFailureListener { callback.onResult(false, it.message) }
    }

    fun createPost(
        communityId: String,
        title: String,
        body: String,
        authorName: String,
        imageUrl: String,
        callback: ResultCallback
    ) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false, "Not signed in")
        val postData = hashMapOf(
            "title" to title,
            "body" to body,
            "imageUrl" to imageUrl,
            "authorId" to uid,
            "authorName" to authorName,
            "upvoteCount" to 0L,
            "commentCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val postDoc = communities().document(communityId).collection("posts").document()
        db.runBatch { batch ->
            batch.set(postDoc, postData)
            batch.update(communityRef(communityId), "postCount", FieldValue.increment(1))
        }.addOnSuccessListener { callback.onResult(true, postDoc.id) }
            .addOnFailureListener { callback.onResult(false, it.message) }
    }

    fun addComment(
        communityId: String,
        postId: String,
        body: String,
        authorName: String,
        parentId: String,
        callback: ResultCallback
    ) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false, "Not signed in")
        val commentData = hashMapOf(
            "body" to body,
            "authorId" to uid,
            "authorName" to authorName,
            "parentId" to parentId,
            "likeCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val commentDoc = postRef(communityId, postId).collection("comments").document()
        db.runBatch { batch ->
            batch.set(commentDoc, commentData)
            batch.update(postRef(communityId, postId), "commentCount", FieldValue.increment(1))
        }.addOnSuccessListener { callback.onResult(true, commentDoc.id) }
            .addOnFailureListener { callback.onResult(false, it.message) }
    }

    private fun commentRef(communityId: String, postId: String, commentId: String) =
        postRef(communityId, postId).collection("comments").document(commentId)

    /** Toggle the current user's like on a comment. */
    fun toggleCommentLike(
        communityId: String,
        postId: String,
        commentId: String,
        callback: ToggleCallback
    ) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false, false)
        val comment = commentRef(communityId, postId, commentId)
        val likeDoc = comment.collection("likes").document(uid)
        db.runTransaction { txn ->
            val exists = txn.get(likeDoc).exists()
            if (exists) {
                txn.delete(likeDoc)
                txn.update(comment, "likeCount", FieldValue.increment(-1))
                false
            } else {
                txn.set(likeDoc, hashMapOf("createdAt" to FieldValue.serverTimestamp()))
                txn.update(comment, "likeCount", FieldValue.increment(1))
                true
            }
        }.addOnSuccessListener { nowLiked -> callback.onResult(true, nowLiked) }
            .addOnFailureListener { callback.onResult(false, false) }
    }

    fun hasLikedComment(
        communityId: String,
        postId: String,
        commentId: String,
        callback: BoolCallback
    ) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false)
        commentRef(communityId, postId, commentId).collection("likes").document(uid).get()
            .addOnSuccessListener { callback.onResult(it.exists()) }
            .addOnFailureListener { callback.onResult(false) }
    }

    /** Toggle an upvote for the current user. */
    fun toggleUpvote(communityId: String, postId: String, callback: ToggleCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false, false)
        val voteDoc = postRef(communityId, postId).collection("votes").document(uid)
        db.runTransaction { txn ->
            val exists = txn.get(voteDoc).exists()
            if (exists) {
                txn.delete(voteDoc)
                txn.update(postRef(communityId, postId), "upvoteCount", FieldValue.increment(-1))
                false
            } else {
                txn.set(voteDoc, hashMapOf("value" to 1, "createdAt" to FieldValue.serverTimestamp()))
                txn.update(postRef(communityId, postId), "upvoteCount", FieldValue.increment(1))
                true
            }
        }.addOnSuccessListener { nowUpvoted -> callback.onResult(true, nowUpvoted) }
            .addOnFailureListener { callback.onResult(false, false) }
    }

    fun hasUpvoted(communityId: String, postId: String, callback: BoolCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false)
        postRef(communityId, postId).collection("votes").document(uid).get()
            .addOnSuccessListener { callback.onResult(it.exists()) }
            .addOnFailureListener { callback.onResult(false) }
    }

    fun joinCommunity(communityId: String, callback: BoolCallback) {
        val user = auth.currentUser ?: return callback.onResult(false)
        val uid = user.uid
        val avatar = user.photoUrl?.toString() ?: ""
        val memberDoc = communityRef(communityId).collection("members").document(uid)
        db.runBatch { batch ->
            batch.set(memberDoc, hashMapOf(
                "joinedAt" to FieldValue.serverTimestamp(),
                "photoUrl" to avatar
            ))
            batch.update(communityRef(communityId), "memberCount", FieldValue.increment(1))
        }.addOnSuccessListener {
            // Keep a short list of the most recent joiners' avatars for the overlap view.
            communityRef(communityId).get().addOnSuccessListener { snap ->
                @Suppress("UNCHECKED_CAST")
                val current = (snap.get("recentMemberAvatars") as? List<String>) ?: emptyList()
                val updated = (listOf(avatar) + current.filter { it != avatar }).take(3)
                communityRef(communityId).update("recentMemberAvatars", updated)
            }
            callback.onResult(true)
        }.addOnFailureListener { callback.onResult(false) }
    }

    fun leaveCommunity(communityId: String, callback: BoolCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false)
        val memberDoc = communityRef(communityId).collection("members").document(uid)
        db.runBatch { batch ->
            batch.delete(memberDoc)
            batch.update(communityRef(communityId), "memberCount", FieldValue.increment(-1))
        }.addOnSuccessListener { callback.onResult(true) }
            .addOnFailureListener { callback.onResult(false) }
    }

    fun isMember(communityId: String, callback: BoolCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false)
        communityRef(communityId).collection("members").document(uid).get()
            .addOnSuccessListener { callback.onResult(it.exists()) }
            .addOnFailureListener { callback.onResult(false) }
    }
}
