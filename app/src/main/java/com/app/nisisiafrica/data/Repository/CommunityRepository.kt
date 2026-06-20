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

    fun createCommunity(name: String, description: String, callback: ResultCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false, "Not signed in")
        val data = hashMapOf(
            "name" to name,
            "description" to description,
            "createdBy" to uid,
            "iconUrl" to "",
            "memberCount" to 0L,
            "postCount" to 0L,
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
        callback: ResultCallback
    ) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false, "Not signed in")
        val postData = hashMapOf(
            "title" to title,
            "body" to body,
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
        callback: ResultCallback
    ) {
        val uid = auth.currentUser?.uid ?: return callback.onResult(false, "Not signed in")
        val commentData = hashMapOf(
            "body" to body,
            "authorId" to uid,
            "authorName" to authorName,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val commentDoc = postRef(communityId, postId).collection("comments").document()
        db.runBatch { batch ->
            batch.set(commentDoc, commentData)
            batch.update(postRef(communityId, postId), "commentCount", FieldValue.increment(1))
        }.addOnSuccessListener { callback.onResult(true, commentDoc.id) }
            .addOnFailureListener { callback.onResult(false, it.message) }
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
        val uid = auth.currentUser?.uid ?: return callback.onResult(false)
        val memberDoc = communityRef(communityId).collection("members").document(uid)
        db.runBatch { batch ->
            batch.set(memberDoc, hashMapOf("joinedAt" to FieldValue.serverTimestamp()))
            batch.update(communityRef(communityId), "memberCount", FieldValue.increment(1))
        }.addOnSuccessListener { callback.onResult(true) }
            .addOnFailureListener { callback.onResult(false) }
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
