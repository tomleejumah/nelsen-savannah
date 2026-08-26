package com.app.nisisiafrica.data.remote

import com.app.nisisiafrica.data.Model.Event
import com.app.nisisiafrica.data.Model.LmsModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.runBlocking

object LmsEventsDataSource {

    @JvmStatic
    fun createHubEventBlocking(bearer: String, payload: LmsModels.CreateHubEventBody): Boolean =
        runBlocking { createHubEvent(bearer, payload) }

    @JvmStatic
    fun reserveSeatBlocking(
        bearer: String,
        eventId: String,
        body: LmsModels.ReserveEventBody,
    ): Pair<Boolean, String?> = runBlocking { reserveSeat(bearer, eventId, body) }

    @JvmStatic
    fun deleteHubEventBlocking(bearer: String, eventId: String): Pair<Boolean, String?> =
        runBlocking { deleteHubEvent(bearer, eventId) }

    @JvmStatic
    fun fetchPublicEventsBlocking(): List<Event> = runBlocking { fetchPublicEvents() }

    private fun HubEventDto.toEvent() = Event(
        eventId = eventId.orEmpty(),
        title = title.orEmpty(),
        date = date,
        startTime = startTime.orEmpty(),
        endTime = endTime.orEmpty(),
        eventType = eventType.orEmpty().ifBlank { "event" },
        mentorId = mentorId.orEmpty(),
        menteeId = menteeId.orEmpty(),
        mentorName = mentorName.orEmpty(),
        menteeName = menteeName.orEmpty(),
        status = status,
        description = description,
        mode = mode.orEmpty().ifBlank { "physical" },
        location = location.orEmpty(),
        meetingLink = meetingLink.orEmpty(),
        participants = null,
        program = program.orEmpty(),
        seats = seats,
        seatsTaken = seatsTaken,
        price = price.orEmpty(),
    )

    suspend fun fetchPublicEvents(): List<Event> = withContext(Dispatchers.IO) {
        try {
            val res = ApiClient.getLmsService().publicHubEvents().execute()
            if (!res.isSuccessful) return@withContext emptyList()
            val body = res.body() ?: return@withContext emptyList()
            if (!body.ok || body.data?.events == null) return@withContext emptyList()
            body.data.events.map { it.toEvent() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun createHubEvent(bearer: String, payload: LmsModels.CreateHubEventBody): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val res = ApiClient.getLmsService().createHubEvent(bearer, payload).execute()
                res.isSuccessful && res.body()?.ok == true
            } catch (_: Exception) {
                false
            }
        }

    suspend fun reserveSeat(
        bearer: String,
        eventId: String,
        body: LmsModels.ReserveEventBody,
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val res = ApiClient.getLmsService().reserveEvent(bearer, eventId, body).execute()
            val envelope = res.body()
            when {
                res.isSuccessful && envelope?.ok == true -> true to null
                envelope?.error != null -> false to envelope.error
                else -> false to "Could not reserve seat"
            }
        } catch (e: Exception) {
            false to (e.message ?: "Network error")
        }
    }

    suspend fun deleteHubEvent(
        bearer: String,
        eventId: String,
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val res = ApiClient.getLmsService().deleteHubEvent(bearer, eventId).execute()
            val envelope = res.body()
            when {
                res.isSuccessful && envelope?.ok == true -> true to null
                envelope?.error != null -> false to envelope.error
                else -> false to "Could not delete event"
            }
        } catch (e: Exception) {
            false to (e.message ?: "Network error")
        }
    }
}

private typealias HubEventDto = LmsModels.HubEventDto
