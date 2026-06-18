package com.shinochanwww.touch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import org.json.JSONException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

private const val DEFAULT_BASE_URL = "http://47.100.48.119"

data class AuthenticatedSession(
    val userId: String,
    val displayName: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val avatarUrl: String?,
    val bio: String?,
    val birthday: String?,
    val gender: String?,
    val cardBackgroundUrl: String?,
    val cardBackgroundKey: String?,
    val lastSeenAt: Long?
)

data class AccountUser(
    val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val bio: String?,
    val birthday: String?,
    val gender: String?,
    val cardBackgroundUrl: String?,
    val cardBackgroundKey: String?,
    val lastSeenAt: Long?
)

data class MeetingRecordDto(
    val id: String,
    val personUserId: String?,
    val personName: String,
    val metDate: String,
    val metTime: String,
    val status: String,
    val method: String
)

data class FriendUserDto(
    val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val bio: String?,
    val birthday: String?,
    val gender: String?,
    val cardBackgroundUrl: String?,
    val cardBackgroundKey: String?,
    val lastSeenAt: Long?
)

data class FriendshipDto(
    val id: String,
    val status: String,
    val direction: String,
    val friend: FriendUserDto?,
    val remark: String?,
    val blockedByMe: Boolean,
    val blockedMe: Boolean
)

data class DayEventDto(
    val id: String,
    val eventDate: String,
    val title: String,
    val note: String,
    val imageBase64: String?,
    val imageBase64List: List<String>,
    val participants: List<FriendUserDto>
)

data class MeetTokenDto(
    val nfcPayloadJson: String,
    val expiresAt: Long
)

data class MeetingProofResultDto(
    val status: String,
    val meeting: MeetingRecordDto?,
    val user: FriendUserDto?,
    val friendship: FriendshipDto?
)

data class RealtimeEventDto(
    val id: Long,
    val type: String,
    val payload: JSONObject,
    val createdAt: Long
)

class AuthApiException(message: String) : Exception(message)

class RealtimeEventConnection(
    private val baseUrl: String,
    private val accessToken: String,
    initialLastEventId: Long,
    private val onEvent: (RealtimeEventDto) -> Unit,
    private val onError: (Exception) -> Unit
) {
    @Volatile
    private var closed = false

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    private var lastEventId = initialLastEventId

    private val worker = Thread {
        while (!closed) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$baseUrl/events/stream?afterId=$lastEventId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 0
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                activeConnection = connection
                val status = connection.responseCode
                if (status !in 200..299) {
                    throw AuthApiException("\u5b9e\u65f6\u8fde\u63a5\u6682\u65f6\u4e0d\u53ef\u7528")
                }
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    val dataLines = mutableListOf<String>()
                    while (!closed) {
                        val line = reader.readLine() ?: break
                        when {
                            line.isBlank() -> {
                                if (dataLines.isNotEmpty()) {
                                    val rawData = dataLines.joinToString("\n")
                                    val json = JSONObject(rawData)
                                    val event = RealtimeEventDto(
                                        id = json.optLong("id", lastEventId),
                                        type = json.optString("type"),
                                        payload = json.optJSONObject("payload") ?: JSONObject(),
                                        createdAt = json.optLong("createdAt", 0L)
                                    )
                                    if (event.id > lastEventId) {
                                        lastEventId = event.id
                                    }
                                    onEvent(event)
                                    dataLines.clear()
                                }
                            }
                            line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                        }
                    }
                }
            } catch (error: Exception) {
                if (!closed) {
                    onError(error)
                    try {
                        Thread.sleep(2_000)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            } finally {
                if (activeConnection === connection) {
                    activeConnection = null
                }
                connection?.disconnect()
            }
        }
    }.apply {
        name = "touch-realtime-events"
        isDaemon = true
    }

    fun start() {
        worker.start()
    }

    fun close() {
        closed = true
        activeConnection?.disconnect()
        worker.interrupt()
    }
}

class AuthApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    fun register(displayName: String, email: String, password: String): AuthenticatedSession {
        val body = JSONObject()
            .put("displayName", displayName)
            .put("email", email)
            .put("password", password)
        return parseSession(requestWithJson("POST", "/auth/register", body, null))
    }

    fun login(email: String, password: String): AuthenticatedSession {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
        return parseSession(requestWithJson("POST", "/auth/login", body, null))
    }

    fun logout(refreshToken: String) {
        requestWithJson("POST", "/auth/logout", JSONObject().put("refreshToken", refreshToken), null)
    }

    fun refresh(refreshToken: String): AuthenticatedSession {
        return parseSession(
            requestWithJson(
                "POST",
                "/auth/refresh",
                JSONObject().put("refreshToken", refreshToken),
                null
            )
        )
    }

    fun updateDisplayName(accessToken: String, displayName: String): AccountUser {
        val body = JSONObject().put("displayName", displayName)
        return parseUser(requestWithJson("POST", "/account/me", body, accessToken))
    }

    fun updateProfile(
        accessToken: String,
        displayName: String,
        bio: String,
        birthday: String,
        gender: String,
        cardBackgroundKey: String?
    ): AccountUser {
        val body = JSONObject()
            .put("displayName", displayName)
            .put("bio", bio)
            .put("birthday", birthday)
            .put("gender", gender)
        if (!cardBackgroundKey.isNullOrBlank()) {
            body.put("cardBackgroundKey", cardBackgroundKey)
        }
        return parseUser(requestWithJson("POST", "/account/me", body, accessToken))
    }

    fun uploadAvatar(context: Context, accessToken: String, imageUri: Uri): AccountUser {
        return uploadAccountImage(
            context = context,
            accessToken = accessToken,
            imageUri = imageUri,
            path = "/account/avatar",
            fieldName = "avatar",
            filePrefix = "avatar"
        )
    }

    fun uploadCardBackground(context: Context, accessToken: String, imageUri: Uri): AccountUser {
        return uploadAccountImage(
            context = context,
            accessToken = accessToken,
            imageUri = imageUri,
            path = "/account/card-background",
            fieldName = "background",
            filePrefix = "card-background"
        )
    }

    private fun uploadAccountImage(
        context: Context,
        accessToken: String,
        imageUri: Uri,
        path: String,
        fieldName: String,
        filePrefix: String
    ): AccountUser {
        val boundary = "TouchBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }

        val mimeType = context.contentResolver.getType(imageUri) ?: "application/octet-stream"
        val fileName = "$filePrefix.${mimeType.substringAfterLast("/", "bin")}"
        connection.outputStream.use { output ->
            output.write("--$boundary\r\n".toByteArray())
            output.write(
                "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n"
                    .toByteArray()
            )
            output.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                input.copyTo(output)
            } ?: throw AuthApiException("\u65e0\u6cd5\u8bfb\u53d6\u9009\u62e9\u7684\u56fe\u7247")
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }

        return parseUser(readJsonResponse(connection))
    }

    fun loadAvatarBitmap(avatarUrl: String): Bitmap? {
        val resolvedUrl = if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            avatarUrl
        } else {
            "$baseUrl$avatarUrl"
        }
        val connection = (URL(resolvedUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
        }
        if (connection.responseCode !in 200..299) {
            return null
        }
        return connection.inputStream.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }

    fun getMeetings(accessToken: String, friendUserId: String? = null): List<MeetingRecordDto> {
        val path = if (friendUserId.isNullOrBlank()) {
            "/meetings"
        } else {
            "/meetings?friendUserId=${urlEncode(friendUserId)}"
        }
        val json = requestWithoutBody("GET", path, accessToken)
        val array = json.getJSONArray("meetings")
        return (0 until array.length()).map { index ->
            parseMeeting(json = array.getJSONObject(index))
        }
    }

    fun getFriends(accessToken: String): List<FriendshipDto> {
        val json = requestWithoutBody("GET", "/friends", accessToken)
        val array = json.getJSONArray("friendships")
        return (0 until array.length()).map { index ->
            parseFriendship(array.getJSONObject(index))
        }
    }

    fun searchUsers(accessToken: String, query: String): List<FriendUserDto> {
        val json = requestWithoutBody("GET", "/friends/search?q=${urlEncode(query)}", accessToken)
        val array = json.getJSONArray("users")
        return (0 until array.length()).map { index ->
            parseFriendUser(array.getJSONObject(index))
        }
    }

    fun sendFriendRequest(accessToken: String, targetUserId: String): FriendshipDto {
        val body = JSONObject().put("targetUserId", targetUserId)
        return parseFriendship(
            requestWithJson("POST", "/friends/requests", body, accessToken).getJSONObject("friendship")
        )
    }

    fun respondFriendRequest(accessToken: String, friendshipId: String, action: String): FriendshipDto {
        val body = JSONObject()
            .put("friendshipId", friendshipId)
            .put("action", action)
        return parseFriendship(
            requestWithJson("POST", "/friends/requests/respond", body, accessToken).getJSONObject("friendship")
        )
    }

    fun removeFriend(accessToken: String, friendUserId: String) {
        requestWithJson("POST", "/friends/remove", JSONObject().put("friendUserId", friendUserId), accessToken)
    }

    fun blockFriend(accessToken: String, friendUserId: String, blocked: Boolean): FriendshipDto {
        val body = JSONObject()
            .put("friendUserId", friendUserId)
            .put("blocked", blocked)
        return parseFriendship(requestWithJson("POST", "/friends/block", body, accessToken).getJSONObject("friendship"))
    }

    fun updateFriendRemark(accessToken: String, friendUserId: String, remark: String): FriendshipDto {
        val body = JSONObject()
            .put("friendUserId", friendUserId)
            .put("remark", remark)
        return parseFriendship(requestWithJson("POST", "/friends/remark", body, accessToken).getJSONObject("friendship"))
    }

    fun getDayEvents(accessToken: String, date: String): List<DayEventDto> {
        val json = requestWithoutBody("GET", "/day-events?date=${urlEncode(date)}", accessToken)
        val array = json.getJSONArray("events")
        return (0 until array.length()).map { index ->
            parseDayEvent(array.getJSONObject(index))
        }
    }

    fun createDayEvent(
        accessToken: String,
        eventDate: String,
        title: String,
        note: String,
        participantUserIds: List<String>,
        imageBase64List: List<String>
    ): DayEventDto {
        val participants = org.json.JSONArray()
        participantUserIds.forEach { participants.put(it) }
        val images = org.json.JSONArray()
        imageBase64List.forEach { images.put(it) }
        val body = JSONObject()
            .put("eventDate", eventDate)
            .put("title", title)
            .put("note", note)
            .put("participantUserIds", participants)
            .put("imageBase64List", images)
        return parseDayEvent(requestWithJson("POST", "/day-events", body, accessToken).getJSONObject("event"))
    }

    fun deleteDayEvent(accessToken: String, eventId: String) {
        requestWithoutBody("DELETE", "/day-events/${urlEncode(eventId)}", accessToken)
    }

    fun updateDayEvent(
        accessToken: String,
        eventId: String,
        title: String,
        note: String,
        participantUserIds: List<String>,
        imageBase64List: List<String>
    ): DayEventDto {
        val participants = org.json.JSONArray()
        participantUserIds.forEach { participants.put(it) }
        val images = org.json.JSONArray()
        imageBase64List.forEach { images.put(it) }
        val body = JSONObject()
            .put("title", title)
            .put("note", note)
            .put("participantUserIds", participants)
            .put("imageBase64List", images)
        return parseDayEvent(requestWithJson("POST", "/day-events/${urlEncode(eventId)}", body, accessToken).getJSONObject("event"))
    }

    fun createMeetToken(accessToken: String): MeetTokenDto {
        val json = requestWithJson("POST", "/meet-tokens", JSONObject(), accessToken)
        return MeetTokenDto(
            nfcPayloadJson = json.getJSONObject("nfcPayload").toString(),
            expiresAt = json.getLong("expiresAt")
        )
    }

    fun submitMeetingProof(
        accessToken: String,
        clientSubmissionId: String,
        nfcPayloadJson: String
    ): MeetingProofResultDto {
        val payload = try {
            JSONObject(nfcPayloadJson)
        } catch (error: Exception) {
            throw AuthApiException("\u8bfb\u53d6\u5230\u7684\u78b0\u4e00\u78b0\u6570\u636e\u65e0\u6548")
        }
        val body = JSONObject()
            .put("clientSubmissionId", clientSubmissionId)
            .put("nfcPayload", payload)
        val json = requestWithJson("POST", "/meetings/proofs", body, accessToken)
        return MeetingProofResultDto(
            status = json.getString("status"),
            meeting = json.optJSONObject("meeting")?.let { parseMeeting(it) },
            user = json.optJSONObject("user")?.let { parseFriendUser(it) },
            friendship = json.optJSONObject("friendship")?.let { parseFriendship(it) }
        )
    }

    fun openRealtimeEvents(
        accessToken: String,
        afterId: Long,
        onEvent: (RealtimeEventDto) -> Unit,
        onError: (Exception) -> Unit
    ): RealtimeEventConnection {
        return RealtimeEventConnection(
            baseUrl = baseUrl,
            accessToken = accessToken,
            initialLastEventId = afterId,
            onEvent = onEvent,
            onError = onError
        ).also { it.start() }
    }

    fun getRealtimeEvents(accessToken: String, afterId: Long): List<RealtimeEventDto> {
        val json = requestWithoutBody("GET", "/events?afterId=$afterId", accessToken)
        val array = json.getJSONArray("events")
        return (0 until array.length()).map { index ->
            parseRealtimeEvent(array.getJSONObject(index))
        }
    }

    private fun requestWithoutBody(
        method: String,
        path: String,
        accessToken: String?
    ): JSONObject {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/json")
            if (accessToken != null) {
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
        }
        return readJsonResponse(connection)
    }

    private fun requestWithJson(
        method: String,
        path: String,
        body: JSONObject,
        accessToken: String?
    ): JSONObject {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6_000
            readTimeout = 6_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (accessToken != null) {
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
        }

        return readJsonResponse(connection)
    }

    private fun readJsonResponse(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.use {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
        }.orEmpty()

        val json = try {
            if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } catch (error: JSONException) {
            val message = if (status == 413 || responseText.startsWith("<html", ignoreCase = true)) {
                "\u56fe\u7247\u6216\u8bb0\u5f55\u5185\u5bb9\u8fc7\u5927\uff0c\u8bf7\u51cf\u5c11\u56fe\u7247\u6570\u91cf\u6216\u6362\u7528\u66f4\u5c0f\u7684\u56fe\u7247"
            } else {
                "\u670d\u52a1\u5668\u8fd4\u56de\u4e86\u65e0\u6cd5\u8bc6\u522b\u7684\u54cd\u5e94\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
            }
            throw AuthApiException(message)
        }
        if (status !in 200..299) {
            val message = json.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: "\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
            throw AuthApiException(message)
        }
        return json
    }

    private fun parseSession(json: JSONObject): AuthenticatedSession {
        val user = json.getJSONObject("user")
        return AuthenticatedSession(
            userId = user.getString("id"),
            displayName = user.getString("displayName"),
            email = user.getString("email"),
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            avatarUrl = user.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" },
            bio = user.optString("bio").takeIf { it.isNotBlank() && it != "null" },
            birthday = user.optString("birthday").takeIf { it.isNotBlank() && it != "null" },
            gender = user.optString("gender").takeIf { it.isNotBlank() && it != "null" },
            cardBackgroundUrl = user.optString("cardBackgroundUrl").takeIf { it.isNotBlank() && it != "null" },
            cardBackgroundKey = user.optString("cardBackgroundKey").takeIf { it.isNotBlank() && it != "null" },
            lastSeenAt = user.optLong("lastSeenAt", 0L).takeIf { it > 0L }
        )
    }

    private fun parseUser(json: JSONObject): AccountUser {
        val user = json.getJSONObject("user")
        return AccountUser(
            userId = user.getString("id"),
            displayName = user.getString("displayName"),
            email = user.getString("email"),
            avatarUrl = user.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" },
            bio = user.optString("bio").takeIf { it.isNotBlank() && it != "null" },
            birthday = user.optString("birthday").takeIf { it.isNotBlank() && it != "null" },
            gender = user.optString("gender").takeIf { it.isNotBlank() && it != "null" },
            cardBackgroundUrl = user.optString("cardBackgroundUrl").takeIf { it.isNotBlank() && it != "null" },
            cardBackgroundKey = user.optString("cardBackgroundKey").takeIf { it.isNotBlank() && it != "null" },
            lastSeenAt = user.optLong("lastSeenAt", 0L).takeIf { it > 0L }
        )
    }

    private fun parseMeeting(json: JSONObject): MeetingRecordDto {
        return MeetingRecordDto(
            id = json.getString("id"),
            personUserId = json.optString("personUserId").takeIf { it.isNotBlank() && it != "null" },
            personName = json.getString("personName"),
            metDate = json.getString("metDate"),
            metTime = json.getString("metTime"),
            status = json.getString("status"),
            method = json.getString("method")
        )
    }

    private fun parseFriendUser(json: JSONObject): FriendUserDto {
        return FriendUserDto(
            userId = json.getString("id"),
            displayName = json.getString("displayName"),
            email = json.getString("email"),
            avatarUrl = json.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" },
            bio = json.optString("bio").takeIf { it.isNotBlank() && it != "null" },
            birthday = json.optString("birthday").takeIf { it.isNotBlank() && it != "null" },
            gender = json.optString("gender").takeIf { it.isNotBlank() && it != "null" },
            cardBackgroundUrl = json.optString("cardBackgroundUrl").takeIf { it.isNotBlank() && it != "null" },
            cardBackgroundKey = json.optString("cardBackgroundKey").takeIf { it.isNotBlank() && it != "null" },
            lastSeenAt = json.optLong("lastSeenAt", 0L).takeIf { it > 0L }
        )
    }

    private fun parseFriendship(json: JSONObject): FriendshipDto {
        return FriendshipDto(
            id = json.getString("id"),
            status = json.getString("status"),
            direction = json.getString("direction"),
            friend = json.optJSONObject("friend")?.let { parseFriendUser(it) },
            remark = json.optString("remark").takeIf { it.isNotBlank() && it != "null" },
            blockedByMe = json.optBoolean("blockedByMe", false),
            blockedMe = json.optBoolean("blockedMe", false)
        )
    }

    private fun parseDayEvent(json: JSONObject): DayEventDto {
        val participants = json.getJSONArray("participants")
        return DayEventDto(
            id = json.getString("id"),
            eventDate = json.getString("eventDate"),
            title = json.getString("title"),
            note = json.getString("note"),
            imageBase64 = json.optString("imageBase64").takeIf { it.isNotBlank() && it != "null" },
            imageBase64List = json.optJSONArray("imageBase64List")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() && it != "null" }
                }
            }.orEmpty(),
            participants = (0 until participants.length()).map { index ->
                parseFriendUser(participants.getJSONObject(index))
            }
        )
    }

    private fun parseRealtimeEvent(json: JSONObject): RealtimeEventDto {
        return RealtimeEventDto(
            id = json.getLong("id"),
            type = json.getString("type"),
            payload = json.optJSONObject("payload") ?: JSONObject(),
            createdAt = json.optLong("createdAt", 0L)
        )
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
