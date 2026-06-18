package com.shinochanwww.touch

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import com.shinochanwww.touch.ui.theme.TouchTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TouchTheme {
                TouchApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        setNfcReaderMode(false, {}, {})
    }

    fun hasNfc(): Boolean = nfcAdapter != null

    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true

    fun setNfcReaderMode(
        enabled: Boolean,
        onPayload: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val adapter = nfcAdapter ?: return
        if (!enabled) {
            adapter.disableReaderMode(this)
            return
        }
        adapter.enableReaderMode(
            this,
            { tag: Tag ->
                try {
                    val isoDep = IsoDep.get(tag)
                    if (isoDep == null) {
                        runOnUiThread { onError("\u672a\u8bfb\u5230 Touch \u78b0\u4e00\u78b0\u6570\u636e") }
                        return@enableReaderMode
                    }
                    isoDep.use { tech ->
                        tech.connect()
                        tech.timeout = 4_000
                        val response = tech.transceive(TouchNfcProtocol.selectAidCommand)
                        val payload = TouchNfcProtocol.stripSuccessStatus(response)
                        runOnUiThread {
                            if (payload == null) {
                                onError("\u5bf9\u65b9\u5c1a\u672a\u51c6\u5907\u597d\u78b0\u4e00\u78b0")
                            } else {
                                onPayload(payload)
                            }
                        }
                    }
                } catch (error: IOException) {
                    runOnUiThread { onError("\u8bfb\u53d6 NFC \u5931\u8d25\uff0c\u8bf7\u91cd\u65b0\u9760\u8fd1\u624b\u673a") }
                } catch (error: Exception) {
                    runOnUiThread { onError(error.message ?: "\u8bfb\u53d6 NFC \u5931\u8d25") }
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }
}

private enum class AuthMode {
    Login,
    Register
}

private enum class MainTab {
    Home,
    Friends,
    Mine
}

private const val SWITCH_ANIMATION_MS = 170
private const val INLINE_ENTER_MS = 120
private const val INLINE_EXIT_MS = 80
private const val PROFILE_EDITOR_EXIT_MS = 260
private const val PROFILE_SAVE_DISMISS_DELAY_MS = 680L
private const val PRESS_ANIMATION_MS = 85
private const val DRAG_FEEDBACK_MS = 90
private const val REBOUND_ANIMATION_MS = 430
private const val CALENDAR_SIZE_ANIMATION_MS = 240
private const val CALENDAR_CONTENT_ANIMATION_MS = 150

private data class UserSession(
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

private object SessionStore {
    private const val PREFS_NAME = "touch_session"
    private const val KEY_SESSION_JSON = "session_json"

    fun load(context: Context): UserSession? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SESSION_JSON, null)
            ?: return null
        return runCatching {
            val json = JSONObject(raw)
            UserSession(
                userId = json.getString("userId"),
                displayName = json.getString("displayName"),
                email = json.getString("email"),
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                avatarUrl = json.optString("avatarUrl").takeIf { it.isNotBlank() && it != "null" },
                bio = json.optString("bio").takeIf { it.isNotBlank() && it != "null" },
                birthday = json.optString("birthday").takeIf { it.isNotBlank() && it != "null" },
                gender = json.optString("gender").takeIf { it.isNotBlank() && it != "null" },
                cardBackgroundUrl = json.optString("cardBackgroundUrl").takeIf { it.isNotBlank() && it != "null" },
                cardBackgroundKey = json.optString("cardBackgroundKey").takeIf { it.isNotBlank() && it != "null" },
                lastSeenAt = json.optLong("lastSeenAt", 0L).takeIf { it > 0L }
            )
        }.getOrNull()
    }

    fun save(context: Context, session: UserSession) {
        val json = JSONObject()
            .put("userId", session.userId)
            .put("displayName", session.displayName)
            .put("email", session.email)
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("avatarUrl", session.avatarUrl)
            .put("bio", session.bio)
            .put("birthday", session.birthday)
            .put("gender", session.gender)
            .put("cardBackgroundUrl", session.cardBackgroundUrl)
            .put("cardBackgroundKey", session.cardBackgroundKey)
            .put("lastSeenAt", session.lastSeenAt)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION_JSON, json.toString())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION_JSON)
            .apply()
    }
}

private enum class MeetingStatus {
    Confirmed,
    Pending
}

private data class MeetingPreview(
    val name: String,
    val time: String,
    val status: MeetingStatus
)

private data class MeetingRecord(
    val id: String,
    val personUserId: String?,
    val personName: String,
    val metDate: String,
    val metTime: String,
    val status: MeetingStatus
)

private data class FriendUser(
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

private data class Friendship(
    val id: String,
    val status: String,
    val direction: String,
    val friend: FriendUser?,
    val remark: String?,
    val blockedByMe: Boolean,
    val blockedMe: Boolean
)

private data class DayEvent(
    val id: String,
    val eventDate: String,
    val title: String,
    val note: String,
    val imageBase64List: List<String>,
    val participants: List<FriendUser>
)

private data class PendingFriendPrompt(
    val user: FriendUser,
    val friendship: Friendship?
)

private data class FloatingRealtimeNotice(
    val id: Long,
    val title: String,
    val message: String,
    val kind: RealtimeNoticeKind
)

private enum class RealtimeNoticeKind {
    Meeting,
    Friend,
    Neutral
}

private data class CalendarMeeting(
    val day: Int,
    val date: String,
    val weekday: String,
    val records: List<MeetingRecord>,
    val isToday: Boolean = false
)

private enum class CalendarViewMode {
    Week,
    Month,
    Year
}

private data class CalendarRenderKey(
    val mode: CalendarViewMode,
    val year: Int,
    val month: Int,
    val weekStartDate: String?
)

private data class MonthDayMeeting(
    val day: Int?,
    val date: String? = null,
    val records: List<MeetingRecord> = emptyList(),
    val isToday: Boolean = false
)

private data class YearMonthMeeting(
    val month: Int,
    val records: List<MeetingRecord>
)

private data class CalendarFocus(
    val year: Int,
    val month: Int,
    val startDay: Int
)

private enum class CalendarDetailMode {
    Day,
    Week
}

private enum class TapDialogMode {
    ShowMine,
    ScanOther
}

private enum class ThemeStyle(val storageKey: String) {
    Muelsyse("muelsyse"),
    Shu("shu"),
    Mizuki("mizuki")
}

private object ThemeStyleStore {
    private const val PREFS_NAME = "touch_theme"
    private const val KEY_STYLE = "style"

    fun load(context: Context): ThemeStyle {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, ThemeStyle.Mizuki.storageKey)
        return ThemeStyle.entries.firstOrNull { it.storageKey == raw } ?: ThemeStyle.Mizuki
    }

    fun save(context: Context, style: ThemeStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.storageKey)
            .apply()
    }
}

private data class CalendarDetail(
    val mode: CalendarDetailMode,
    val date: String?,
    val title: String,
    val records: List<MeetingRecord>
)

@Composable
private fun TouchApp() {
    val context = LocalContext.current
    var session by remember { mutableStateOf(SessionStore.load(context)) }
    var themeStyle by remember { mutableStateOf(ThemeStyleStore.load(context)) }
    val authApiClient = remember { AuthApiClient() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    TouchColors.style = themeStyle
    val onThemeStyleChanged: (ThemeStyle) -> Unit = { nextStyle ->
        themeStyle = nextStyle
        ThemeStyleStore.save(context, nextStyle)
    }
    val persistSession: (UserSession) -> Unit = { nextSession ->
        session = nextSession
        SessionStore.save(context, nextSession)
    }

    LaunchedEffect(Unit) {
        val savedSession = session
        if (savedSession != null) {
            Thread {
                runCatching { authApiClient.refresh(savedSession.refreshToken) }
                    .onSuccess { refreshed ->
                        mainHandler.post {
                            persistSession(refreshed.toUserSession())
                        }
                    }
                    .onFailure {
                        // Keep the saved session so the user does not get logged out just because the network is unavailable.
                    }
            }.start()
        }
    }

    if (session == null) {
        AuthScreen(
            onLogin = { email, password, onResult ->
                Thread {
                    try {
                        val authSession = authApiClient.login(email, password)
                        mainHandler.post {
                            persistSession(authSession.toUserSession())
                            onResult(null)
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            onResult(error.message ?: "\u767b\u5f55\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u672c\u673a\u540e\u7aef")
                        }
                    }
                }.start()
            },
            onRegister = { displayName, email, password, onResult ->
                Thread {
                    try {
                        val authSession = authApiClient.register(displayName, email, password)
                        mainHandler.post {
                            persistSession(authSession.toUserSession())
                            onResult(null)
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            onResult(error.message ?: "\u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u672c\u673a\u540e\u7aef")
                        }
                    }
                }.start()
            }
        )
    } else {
        TouchHomeScreen(
            session = session!!,
            authApiClient = authApiClient,
            mainHandler = mainHandler,
            themeStyle = themeStyle,
            onThemeStyleChanged = onThemeStyleChanged,
            onSessionChanged = persistSession,
            onLogout = {
                val refreshToken = session?.refreshToken
                NfcTapService.clearPayload()
                session = null
                SessionStore.clear(context)
                if (refreshToken != null) {
                    Thread {
                        runCatching { authApiClient.logout(refreshToken) }
                    }.start()
                }
            }
        )
    }
}

@Composable
private fun AuthScreen(
    onLogin: (String, String, (String?) -> Unit) -> Unit,
    onRegister: (String, String, String, (String?) -> Unit) -> Unit
) {
    var mode by remember { mutableStateOf(AuthMode.Login) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val isLogin = mode == AuthMode.Login

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clearFocusOnBackgroundTap(),
        containerColor = TouchColors.Background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Touch",
                style = MaterialTheme.typography.headlineLarge,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (isLogin) "\u767b\u5f55\u8d26\u6237" else "\u6ce8\u518c\u8d26\u6237",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TouchColors.TextStrong,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\u8d26\u6237\u9a8c\u8bc1\u901a\u8fc7\u540e\u624d\u80fd\u521b\u5efa\u8bbe\u5907\u5bc6\u94a5\u548c\u78b0\u4e00\u78b0\u8bb0\u5f55",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TouchColors.TextMuted
                    )

                    if (!isLogin) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                errorText = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("\u6635\u79f0") },
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorText = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("\u90ae\u7bb1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorText = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("\u5bc6\u7801") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    errorText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = TouchColors.Error,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            val normalizedEmail = email.trim()
                            when {
                                isSubmitting -> Unit
                                !isLogin && displayName.trim().isBlank() -> {
                                    errorText = "\u8bf7\u8f93\u5165\u6635\u79f0"
                                }
                                normalizedEmail.isBlank() -> {
                                    errorText = "\u8bf7\u8f93\u5165\u90ae\u7bb1"
                                }
                                password.length < 8 -> {
                                    errorText = "\u5bc6\u7801\u81f3\u5c11\u9700\u8981 8 \u4f4d"
                                }
                                isLogin -> {
                                    isSubmitting = true
                                    errorText = null
                                    onLogin(normalizedEmail, password) { error ->
                                        isSubmitting = false
                                        errorText = error
                                    }
                                }
                                else -> {
                                    isSubmitting = true
                                    errorText = null
                                    onRegister(displayName.trim(), normalizedEmail, password) { error ->
                                        isSubmitting = false
                                        errorText = error
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchPrimaryButtonColors()
                    ) {
                        Text(
                            text = when {
                                isSubmitting && isLogin -> "\u767b\u5f55\u4e2d..."
                                isSubmitting -> "\u6ce8\u518c\u4e2d..."
                                isLogin -> "\u767b\u5f55"
                                else -> "\u6ce8\u518c\u5e76\u767b\u5f55"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLogin) "\u8fd8\u6ca1\u6709\u8d26\u6237\uff1f" else "\u5df2\u6709\u8d26\u6237\uff1f",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TouchColors.TextMuted
                        )
                        TextButton(
                            onClick = {
                                mode = if (isLogin) AuthMode.Register else AuthMode.Login
                                errorText = null
                                isSubmitting = false
                            },
                            colors = touchTextButtonColors()
                        ) {
                            Text(
                                text = if (isLogin) "\u53bb\u6ce8\u518c" else "\u53bb\u767b\u5f55",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun AuthenticatedSession.toUserSession(): UserSession {
    return UserSession(
        userId = userId,
        displayName = displayName,
        email = email,
        accessToken = accessToken,
        refreshToken = refreshToken,
        avatarUrl = avatarUrl,
        bio = bio,
        birthday = birthday,
        gender = gender,
        cardBackgroundUrl = cardBackgroundUrl,
        cardBackgroundKey = cardBackgroundKey,
        lastSeenAt = lastSeenAt
    )
}

@Composable
private fun TouchHomeScreen(
    session: UserSession,
    authApiClient: AuthApiClient,
    mainHandler: Handler,
    themeStyle: ThemeStyle,
    onThemeStyleChanged: (ThemeStyle) -> Unit,
    onSessionChanged: (UserSession) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val hasNfc = activity?.hasNfc() == true
    val isNfcEnabled = activity?.isNfcEnabled() == true
    val isReady = hasNfc && isNfcEnabled
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAccountEditing by remember { mutableStateOf(false) }
    var calendarViewMode by remember { mutableStateOf(CalendarViewMode.Month) }
    var focusedYear by remember { mutableIntStateOf(2026) }
    var focusedMonth by remember { mutableIntStateOf(6) }
    var focusedWeekStartDay by remember { mutableIntStateOf(15) }
    var detail by remember { mutableStateOf<CalendarDetail?>(null) }
    var selectedDetailDate by remember { mutableStateOf<String?>(null) }
    var meetingRecords by remember { mutableStateOf<List<MeetingRecord>>(emptyList()) }
    var meetingLoadError by remember { mutableStateOf<String?>(null) }
    var friendships by remember { mutableStateOf<List<Friendship>>(emptyList()) }
    var friendLoadError by remember { mutableStateOf<String?>(null) }
    var selectedFriendFilter by remember { mutableStateOf<FriendUser?>(null) }
    var selectedMainTab by remember { mutableStateOf(MainTab.Home) }
    var pendingFriendPrompt by remember { mutableStateOf<PendingFriendPrompt?>(null) }
    var friendCardTarget by remember { mutableStateOf<Friendship?>(null) }
    var dayDetailTarget by remember { mutableStateOf<CalendarDetail?>(null) }
    var tapStatusText by remember {
        mutableStateOf(
            when {
                !hasNfc -> "\u8fd9\u53f0\u8bbe\u5907\u4e0d\u652f\u6301 NFC"
                !isNfcEnabled -> "\u8bf7\u5148\u5728\u7cfb\u7edf\u8bbe\u7f6e\u4e2d\u6253\u5f00 NFC"
                else -> "\u5df2\u5c31\u7eea\uff0c\u53ef\u4ee5\u751f\u6210\u78b0\u4e00\u78b0\u51ed\u8bc1"
            }
        )
    }
    var tapStatusIsError by remember { mutableStateOf(!isReady) }
    var isPreparingTap by remember { mutableStateOf(false) }
    var isScanningTap by remember { mutableStateOf(false) }
    var tapDialogMode by remember { mutableStateOf<TapDialogMode?>(null) }
    var realtimeNotice by remember { mutableStateOf<FloatingRealtimeNotice?>(null) }
    var lastRealtimeEventId by remember(session.userId) { mutableStateOf(0L) }
    var realtimeReady by remember(session.userId) { mutableStateOf(false) }
    var realtimeBaselineReady by remember(session.userId) { mutableStateOf(false) }
    var realtimeStartedAt by remember(session.userId) { mutableStateOf(0L) }
    val acceptedFriends by remember(friendships) {
        derivedStateOf {
            friendships
                .filter { it.status == "accepted" && it.friend != null }
                .mapNotNull { it.friend }
        }
    }
    val visibleMeetingRecords by remember(meetingRecords, selectedFriendFilter) {
        derivedStateOf {
            val friendId = selectedFriendFilter?.userId
            if (friendId == null) meetingRecords else meetingRecords.filter { it.personUserId == friendId }
        }
    }
    val calendarMeetings by remember(visibleMeetingRecords, focusedYear, focusedMonth, focusedWeekStartDay) {
        derivedStateOf {
            buildWeekMeetings(
                records = visibleMeetingRecords,
                year = focusedYear,
                month = focusedMonth,
                startDay = focusedWeekStartDay
            )
        }
    }
    val refreshMeetings = {
        Thread {
            try {
                val loaded = authApiClient.getMeetings(
                    accessToken = session.accessToken,
                    friendUserId = selectedFriendFilter?.userId
                ).map { it.toMeetingRecord() }
                mainHandler.post {
                    meetingRecords = loaded
                    meetingLoadError = null
                }
            } catch (error: Exception) {
                mainHandler.post {
                    meetingLoadError = error.message ?: "\u89c1\u9762\u8bb0\u5f55\u52a0\u8f7d\u5931\u8d25"
                }
            }
        }.start()
    }
    val refreshFriends = {
        Thread {
            try {
                val loaded = authApiClient.getFriends(session.accessToken).map { it.toFriendship() }
                mainHandler.post {
                    friendships = loaded
                    friendLoadError = null
                    val selectedId = selectedFriendFilter?.userId
                    if (selectedId != null && loaded.none { it.status == "accepted" && it.friend?.userId == selectedId }) {
                        selectedFriendFilter = null
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    friendLoadError = error.message ?: "\u597d\u53cb\u5217\u8868\u52a0\u8f7d\u5931\u8d25"
                }
            }
        }.start()
    }
    val handleRealtimeEvent: (RealtimeEventDto) -> Unit = { event ->
        val shouldNotify = realtimeReady &&
            realtimeStartedAt > 0L &&
            event.createdAt >= realtimeStartedAt
        if (!realtimeReady) {
            if (event.id > lastRealtimeEventId) {
                lastRealtimeEventId = event.id
            }
        } else if (event.id > lastRealtimeEventId) {
            lastRealtimeEventId = event.id
            when (event.type) {
                "meeting_confirmed" -> {
                    val peerName = event.payload.optJSONObject("peer")
                        ?.optString("displayName")
                        ?.takeIf { it.isNotBlank() }
                        ?: "\u597d\u53cb"
                    if (shouldNotify) {
                        realtimeNotice = FloatingRealtimeNotice(
                            id = event.id,
                            title = "\u78b0\u4e00\u78b0\u6210\u529f",
                            message = "\u548c $peerName \u5df2\u786e\u8ba4\u89c1\u9762",
                            kind = RealtimeNoticeKind.Meeting
                        )
                    }
                    refreshMeetings()
                }
                "friend_request_received" -> {
                    val fromName = event.payload.optJSONObject("fromUser")
                        ?.optString("displayName")
                        ?.takeIf { it.isNotBlank() }
                        ?: "\u65b0\u670b\u53cb"
                    if (shouldNotify) {
                        realtimeNotice = FloatingRealtimeNotice(
                            id = event.id,
                            title = "\u65b0\u7684\u597d\u53cb\u7533\u8bf7",
                            message = "$fromName \u60f3\u6dfb\u52a0\u4f60\u4e3a\u597d\u53cb",
                            kind = RealtimeNoticeKind.Friend
                        )
                    }
                    refreshFriends()
                }
                "friend_request_accepted" -> {
                    val fromName = event.payload.optJSONObject("fromUser")
                        ?.optString("displayName")
                        ?.takeIf { it.isNotBlank() }
                        ?: "\u5bf9\u65b9"
                    if (shouldNotify) {
                        realtimeNotice = FloatingRealtimeNotice(
                            id = event.id,
                            title = "\u597d\u53cb\u7533\u8bf7\u5df2\u901a\u8fc7",
                            message = "$fromName \u5df2\u6210\u4e3a\u4f60\u7684\u597d\u53cb",
                            kind = RealtimeNoticeKind.Friend
                        )
                    }
                    refreshFriends()
                }
                "friend_request_rejected" -> {
                    val fromName = event.payload.optJSONObject("fromUser")
                        ?.optString("displayName")
                        ?.takeIf { it.isNotBlank() }
                        ?: "\u5bf9\u65b9"
                    if (shouldNotify) {
                        realtimeNotice = FloatingRealtimeNotice(
                            id = event.id,
                            title = "\u597d\u53cb\u7533\u8bf7\u88ab\u62d2\u7edd",
                            message = "$fromName \u6ca1\u6709\u901a\u8fc7\u4f60\u7684\u597d\u53cb\u7533\u8bf7",
                            kind = RealtimeNoticeKind.Friend
                        )
                    }
                    refreshFriends()
                }
                "friend_removed" -> {
                    if (shouldNotify) {
                        realtimeNotice = FloatingRealtimeNotice(
                            id = event.id,
                            title = "\u597d\u53cb\u72b6\u6001\u5df2\u66f4\u65b0",
                            message = "\u597d\u53cb\u5217\u8868\u5df2\u540c\u6b65",
                            kind = RealtimeNoticeKind.Neutral
                        )
                    }
                    refreshFriends()
                    refreshMeetings()
                }
            }
        }
    }

    LaunchedEffect(session.accessToken, selectedFriendFilter?.userId) {
        refreshMeetings()
    }
    LaunchedEffect(session.accessToken) {
        refreshFriends()
    }
    LaunchedEffect(session.accessToken) {
        realtimeReady = false
        realtimeBaselineReady = false
        realtimeStartedAt = System.currentTimeMillis() / 1000
        try {
            val existingEvents = withContext(Dispatchers.IO) {
                authApiClient.getRealtimeEvents(session.accessToken, 0L)
            }
            lastRealtimeEventId = existingEvents.maxOfOrNull { it.id } ?: 0L
        } catch (_: Exception) {
            lastRealtimeEventId = 0L
        }
        realtimeBaselineReady = true
        realtimeReady = true
        while (true) {
            delay(2_000)
            try {
                val events = withContext(Dispatchers.IO) {
                    authApiClient.getRealtimeEvents(session.accessToken, lastRealtimeEventId)
                }
                events.forEach { event ->
                    handleRealtimeEvent(event)
                }
            } catch (_: Exception) {
                // SSE may still be active. Keep polling quietly while the app is foreground.
            }
        }
    }
    DisposableEffect(session.accessToken, realtimeBaselineReady) {
        if (!realtimeBaselineReady) {
            onDispose {}
        }
        val connection = if (realtimeBaselineReady) {
            authApiClient.openRealtimeEvents(
                accessToken = session.accessToken,
                afterId = lastRealtimeEventId,
                onEvent = { event ->
                    mainHandler.post {
                        handleRealtimeEvent(event)
                    }
                },
                onError = {
                    // The connection retries in the background. Keep UI quiet unless a real event arrives.
                }
            )
        } else {
            null
        }
        onDispose {
            connection?.close()
        }
    }
    LaunchedEffect(realtimeNotice?.id) {
        if (realtimeNotice != null) {
            val durationMs = if (realtimeNotice?.kind == RealtimeNoticeKind.Friend) 8_000L else 4_200L
            delay(durationMs)
            realtimeNotice = null
        }
    }
    LaunchedEffect(isScanningTap, session.accessToken) {
        if (activity == null) {
            return@LaunchedEffect
        }
        if (!isScanningTap) {
            activity.setNfcReaderMode(false, {}, {})
            return@LaunchedEffect
        }
        activity.setNfcReaderMode(
            enabled = true,
            onPayload = { payload ->
                isScanningTap = false
                tapStatusIsError = false
                tapStatusText = "\u5df2\u8bfb\u53d6\uff0c\u6b63\u5728\u7531\u670d\u52a1\u5668\u786e\u8ba4..."
                Thread {
                    try {
                        val result = authApiClient.submitMeetingProof(
                            accessToken = session.accessToken,
                            clientSubmissionId = "android-${UUID.randomUUID()}",
                            nfcPayloadJson = payload
                        )
                        mainHandler.post {
                            val resultUser = result.user
                            val resultMeeting = result.meeting
                            if (result.status == "friend_required" && resultUser != null) {
                                val target = resultUser.toFriendUser()
                                val friendship = result.friendship?.toFriendship()
                                tapStatusIsError = false
                                if (friendship?.status == "accepted" && (friendship.blockedByMe || friendship.blockedMe)) {
                                    pendingFriendPrompt = null
                                    tapStatusText = if (friendship.blockedByMe) {
                                        "\u4f60\u5df2\u5c4f\u853d ${target.displayName} \u7684\u78b0\u4e00\u78b0\uff0c\u8bf7\u5148\u5728\u597d\u53cb\u7ba1\u7406\u4e2d\u53d6\u6d88\u5c4f\u853d"
                                    } else {
                                        "${target.displayName} \u5df2\u5c4f\u853d\u4e0e\u4f60\u7684\u78b0\u4e00\u78b0"
                                    }
                                } else {
                                    tapStatusText = "\u4f60\u4eec\u8fd8\u4e0d\u662f\u597d\u53cb\uff0c\u5148\u53d1\u9001\u597d\u53cb\u7533\u8bf7\u540e\u518d\u78b0\u4e00\u78b0"
                                    pendingFriendPrompt = PendingFriendPrompt(
                                        user = target,
                                        friendship = friendship
                                    )
                                }
                                refreshFriends()
                            } else if (result.status == "confirmed" && resultMeeting != null) {
                                val confirmed = resultMeeting.toMeetingRecord()
                                pendingFriendPrompt = null
                                tapStatusIsError = false
                                tapStatusText = "\u5df2\u786e\u8ba4\uff1a\u548c ${confirmed.personName} \u89c1\u9762"
                                meetingRecords = listOf(confirmed) + meetingRecords.filter { it.id != confirmed.id }
                                refreshMeetings()
                            } else {
                                tapStatusIsError = true
                                tapStatusText = "\u670d\u52a1\u5668\u8fd4\u56de\u4e86\u672a\u77e5\u7684\u78b0\u4e00\u78b0\u72b6\u6001"
                            }
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            tapStatusIsError = true
                            tapStatusText = error.message ?: "\u670d\u52a1\u5668\u786e\u8ba4\u5931\u8d25"
                        }
                    }
                }.start()
            },
            onError = { message ->
                tapStatusIsError = true
                tapStatusText = message
            }
        )
    }
    LaunchedEffect(session.avatarUrl) {
        val avatarUrl = session.avatarUrl
        if (avatarUrl == null) {
            avatarBitmap = null
        } else {
            Thread {
                val loaded = runCatching { authApiClient.loadAvatarBitmap(avatarUrl) }.getOrNull()
                mainHandler.post {
                    avatarBitmap = loaded
                }
            }.start()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clearFocusOnBackgroundTap(),
        containerColor = TouchColors.Background,
        bottomBar = {
            BottomTabBar(
                selectedTab = selectedMainTab,
                onTabSelected = {
                    selectedMainTab = it
                    isAccountEditing = false
                    detail = null
                    selectedDetailDate = null
                }
            )
        }
    ) { innerPadding ->
        Crossfade(
            targetState = selectedMainTab,
            animationSpec = tween(SWITCH_ANIMATION_MS, easing = FastOutSlowInEasing),
            label = "main-tab-switch"
        ) { tab ->
            when (tab) {
                MainTab.Home -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 18.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item(key = "header") {
                        Header(isReady = isReady)
                    }
                    item(key = "calendar") {
                        MeetingCalendar(
                            calendarMeetings = calendarMeetings,
                            allRecords = visibleMeetingRecords,
                            focusedYear = focusedYear,
                            focusedMonth = focusedMonth,
                            viewMode = calendarViewMode,
                            detail = detail,
                            friends = acceptedFriends,
                            authApiClient = authApiClient,
                            selectedFriend = selectedFriendFilter,
                            isFriendFiltered = selectedFriendFilter != null,
                            onFriendFilterChanged = {
                                selectedFriendFilter = it
                                detail = null
                                selectedDetailDate = null
                            },
                            onRangeSwipe = { direction ->
                                when (calendarViewMode) {
                                    CalendarViewMode.Week -> {
                                        val nextFocus = shiftWeekFocus(
                                            year = focusedYear,
                                            month = focusedMonth,
                                            startDay = focusedWeekStartDay,
                                            deltaWeeks = direction
                                        )
                                        focusedYear = nextFocus.year
                                        focusedMonth = nextFocus.month
                                        focusedWeekStartDay = nextFocus.startDay
                                    }
                                    CalendarViewMode.Month -> {
                                        val nextMonth = focusedMonth + direction
                                        when {
                                            nextMonth < 1 -> {
                                                focusedYear -= 1
                                                focusedMonth = 12
                                            }
                                            nextMonth > 12 -> {
                                                focusedYear += 1
                                                focusedMonth = 1
                                            }
                                            else -> focusedMonth = nextMonth
                                        }
                                        focusedWeekStartDay = 1
                                    }
                                    CalendarViewMode.Year -> {
                                        focusedYear += direction
                                        focusedMonth = 1
                                        focusedWeekStartDay = 1
                                    }
                                }
                                detail = null
                                selectedDetailDate = null
                            },
                            onViewModeChange = {
                                calendarViewMode = it
                                detail = null
                                selectedDetailDate = null
                            },
                            onClearDetail = {
                                detail = null
                                selectedDetailDate = null
                            },
                            onDaySelected = { date, records ->
                                if (calendarViewMode == CalendarViewMode.Week &&
                                    selectedDetailDate == date &&
                                    detail?.mode == CalendarDetailMode.Day
                                ) {
                                    detail = null
                                    selectedDetailDate = null
                                } else {
                                    selectedDetailDate = date
                                    detail = CalendarDetail(
                                        mode = CalendarDetailMode.Day,
                                        date = date,
                                        title = "${formatDateLabel(date)} \u89c1\u9762\u8be6\u60c5",
                                        records = records
                                    )
                                }
                            },
                            onDayLongPressed = { date, records ->
                                dayDetailTarget = CalendarDetail(
                                    mode = CalendarDetailMode.Day,
                                    date = date,
                                    title = "${formatDateLabel(date)} \u89c1\u9762\u8be6\u60c5",
                                    records = records
                                )
                            },
                            onMonthDaySelected = { day ->
                                if (day.date != null) {
                                    val nextFocus = weekStartForDate(focusedYear, focusedMonth, day.day ?: 1)
                                    focusedYear = nextFocus.year
                                    focusedMonth = nextFocus.month
                                    focusedWeekStartDay = nextFocus.startDay
                                    calendarViewMode = CalendarViewMode.Week
                                    selectedDetailDate = null
                                    val weekMeetings = buildWeekMeetings(
                                        records = visibleMeetingRecords,
                                        year = nextFocus.year,
                                        month = nextFocus.month,
                                        startDay = nextFocus.startDay
                                    )
                                    detail = CalendarDetail(
                                        mode = CalendarDetailMode.Week,
                                        date = weekMeetings.firstOrNull()?.date,
                                        title = weekRangeLabel(weekMeetings),
                                        records = weekMeetings.flatMap { it.records }
                                    )
                                }
                            },
                            onMonthDayLongPressed = { day ->
                                val date = day.date
                                if (date != null) {
                                    dayDetailTarget = CalendarDetail(
                                        mode = CalendarDetailMode.Day,
                                        date = date,
                                        title = "${formatDateLabel(date)} \u89c1\u9762\u8be6\u60c5",
                                        records = day.records
                                    )
                                }
                            },
                            onYearMonthSelected = { month ->
                                focusedMonth = month.month
                                focusedWeekStartDay = 1
                                calendarViewMode = CalendarViewMode.Month
                                detail = null
                                selectedDetailDate = null
                            }
                        )
                    }
                    item(key = "actions") {
                        PrimaryActions(
                            statusText = tapStatusText,
                            statusIsError = tapStatusIsError,
                            isReady = isReady,
                            isPreparing = isPreparingTap,
                            isScanning = isScanningTap,
                            onShowMine = {
                                isAccountEditing = false
                                detail = null
                                selectedDetailDate = null
                                pendingFriendPrompt = null
                                tapDialogMode = TapDialogMode.ShowMine
                                if (!isReady) {
                                    tapStatusIsError = true
                                    tapStatusText = if (!hasNfc) {
                                        "\u8fd9\u53f0\u8bbe\u5907\u4e0d\u652f\u6301 NFC"
                                    } else {
                                        "\u8bf7\u5148\u5728\u7cfb\u7edf\u8bbe\u7f6e\u4e2d\u6253\u5f00 NFC"
                                    }
                                } else {
                                    isPreparingTap = true
                                    tapStatusIsError = false
                                    tapStatusText = "\u6b63\u5728\u7533\u8bf7\u4e00\u6b21\u6027\u78b0\u4e00\u78b0\u51ed\u8bc1..."
                                    Thread {
                                        try {
                                            val token = authApiClient.createMeetToken(session.accessToken)
                                            NfcTapService.setPayload(token.nfcPayloadJson)
                                            mainHandler.post {
                                                isPreparingTap = false
                                                tapStatusIsError = false
                                                tapStatusText = "\u5df2\u51c6\u5907\u597d\uff0c\u8bf7\u8ba9\u5bf9\u65b9\u70b9\u51fb\u201c\u78b0\u4e00\u78b0\u522b\u4eba\u201d\u540e\u9760\u8fd1\u672c\u673a"
                                            }
                                        } catch (error: Exception) {
                                            mainHandler.post {
                                                isPreparingTap = false
                                                tapStatusIsError = true
                                                tapStatusText = error.message ?: "\u751f\u6210\u78b0\u4e00\u78b0\u51ed\u8bc1\u5931\u8d25"
                                            }
                                        }
                                    }.start()
                                }
                            },
                            onScanOther = {
                                isAccountEditing = false
                                detail = null
                                selectedDetailDate = null
                                pendingFriendPrompt = null
                                tapDialogMode = TapDialogMode.ScanOther
                                if (!isReady) {
                                    tapStatusIsError = true
                                    tapStatusText = if (!hasNfc) {
                                        "\u8fd9\u53f0\u8bbe\u5907\u4e0d\u652f\u6301 NFC"
                                    } else {
                                        "\u8bf7\u5148\u5728\u7cfb\u7edf\u8bbe\u7f6e\u4e2d\u6253\u5f00 NFC"
                                    }
                                } else {
                                    val nextScanning = !isScanningTap
                                    isScanningTap = nextScanning
                                    tapStatusIsError = false
                                    tapStatusText = if (nextScanning) {
                                        "\u6b63\u5728\u626b\u63cf\uff0c\u8bf7\u9760\u8fd1\u5df2\u663e\u793a\u78b0\u4e00\u78b0\u7684\u624b\u673a"
                                    } else {
                                        "\u5df2\u505c\u6b62\u626b\u63cf"
                                    }
                                }
                            }
                        )
                    }
                }

                MainTab.Friends -> FriendManagementScreen(
                    modifier = Modifier.padding(innerPadding),
                    session = session,
                    authApiClient = authApiClient,
                    mainHandler = mainHandler,
                    friendships = friendships,
                    meetingRecords = meetingRecords,
                    errorText = friendLoadError,
                    showBackButton = false,
                    onFriendCardOpen = { friendCardTarget = it },
                    onBack = { selectedMainTab = MainTab.Home },
                    onFriendshipsChanged = {
                        friendships = it
                        refreshFriends()
                        refreshMeetings()
                    }
                )

                MainTab.Mine -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 18.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item(key = "mine-header") {
                        Text(
                            text = "\u6211\u7684",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TouchColors.TextStrong,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    item(key = "mine-theme") {
                        ThemeStylePanel(
                            selectedStyle = themeStyle,
                            onStyleSelected = onThemeStyleChanged
                        )
                    }
                    item(key = "mine-account") {
                        AccountPanel(
                            session = session,
                            avatarBitmap = avatarBitmap,
                            onAvatarBitmapChanged = { avatarBitmap = it },
                            authApiClient = authApiClient,
                            mainHandler = mainHandler,
                            isEditing = isAccountEditing,
                            onEditingChange = { isAccountEditing = it },
                            onSessionChanged = onSessionChanged,
                            onLogout = onLogout
                        )
                    }
                }
            }
        }
        tapDialogMode?.let { mode ->
            TapProgressDialog(
                mode = mode,
                statusText = tapStatusText,
                statusIsError = tapStatusIsError,
                isPreparing = isPreparingTap,
                isScanning = isScanningTap,
                pendingFriendPrompt = pendingFriendPrompt,
                onSendFriendRequest = { user ->
                    Thread {
                        try {
                            authApiClient.sendFriendRequest(session.accessToken, user.userId)
                            mainHandler.post {
                                pendingFriendPrompt = null
                                tapStatusIsError = false
                                tapStatusText = "\u5df2\u5411 ${user.displayName} \u53d1\u9001\u597d\u53cb\u7533\u8bf7"
                                refreshFriends()
                            }
                        } catch (error: Exception) {
                            mainHandler.post {
                                tapStatusIsError = true
                                tapStatusText = error.message ?: "\u597d\u53cb\u7533\u8bf7\u53d1\u9001\u5931\u8d25"
                            }
                        }
                    }.start()
                },
                onDismiss = {
                    if (mode == TapDialogMode.ShowMine) {
                        NfcTapService.clearPayload()
                    }
                    if (mode == TapDialogMode.ScanOther) {
                        isScanningTap = false
                        activity?.setNfcReaderMode(false, {}, {})
                    }
                    pendingFriendPrompt = null
                    tapDialogMode = null
                }
            )
        }
        friendCardTarget?.let { friendship ->
            FriendCardDialog(
                friendship = friendship,
                meetingRecords = meetingRecords,
                authApiClient = authApiClient,
                accessToken = session.accessToken,
                mainHandler = mainHandler,
                onDismiss = { friendCardTarget = null },
                onFriendshipChanged = { updated ->
                    friendships = friendships.map { if (it.id == updated.id) updated else it }
                    friendCardTarget = updated
                }
            )
        }
        dayDetailTarget?.let { target ->
            DayDetailDialog(
                detail = target,
                friends = acceptedFriends,
                authApiClient = authApiClient,
                accessToken = session.accessToken,
                mainHandler = mainHandler,
                onDismiss = { dayDetailTarget = null }
            )
        }
        FloatingRealtimeNoticeOverlay(
            notice = realtimeNotice,
            onDismiss = { realtimeNotice = null }
        )
    }
}

@Composable
private fun ThemeStylePanel(
    selectedStyle: ThemeStyle,
    onStyleSelected: (ThemeStyle) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "\u6574\u4f53\u8272\u5f69",
                style = MaterialTheme.typography.titleMedium,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )
            listOf(ThemeStyle.Muelsyse, ThemeStyle.Shu, ThemeStyle.Mizuki).forEach { style ->
                ThemeStyleRow(
                    style = style,
                    selected = selectedStyle == style,
                    onClick = { onStyleSelected(style) }
                )
            }
        }
    }
}

@Composable
private fun ThemeStyleRow(
    style: ThemeStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    val title = when (style) {
        ThemeStyle.Muelsyse -> "\u7f2a\u5c14\u8d5b\u601d"
        ThemeStyle.Shu -> "\u9ecd"
        ThemeStyle.Mizuki -> "\u6c34\u6708"
    }
    val description = when (style) {
        ThemeStyle.Muelsyse -> "\u6e05\u900f\u8584\u8377\u3001\u6c34\u8272\u3001\u6df1\u9752\u7070"
        ThemeStyle.Shu -> "\u7a3b\u7a57\u91d1\u3001\u6a44\u6984\u9752\u3001\u8c61\u7259\u767d"
        ThemeStyle.Mizuki -> "\u6df1\u6d77\u84dd\u3001\u84dd\u7d2b\u3001\u8367\u5149\u6c34\u9752"
    }
    val swatches = when (style) {
        ThemeStyle.Muelsyse -> listOf(Color(0xFF43BBA8), Color(0xFF4DA9C7), Color(0xFFF2FAF8), Color(0xFFB89245))
        ThemeStyle.Shu -> listOf(Color(0xFF7F9F56), Color(0xFFD1A84E), Color(0xFFFBF8EA), Color(0xFF536C37))
        ThemeStyle.Mizuki -> listOf(Color(0xFF5E87D6), Color(0xFF7B68B8), Color(0xFF68D6D0), Color(0xFFF3F7FF))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .cuteClickable(onClick = onClick),
        color = if (selected) TouchColors.SuccessSoft else TouchColors.DetailSurface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = if (selected) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                swatches.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TouchColors.TextMuted
                )
            }
            StatusPill(
                text = if (selected) "\u5df2\u9009" else "\u5207\u6362",
                background = if (selected) TouchColors.CalendarToday else TouchColors.CalendarTile,
                foreground = if (selected) TouchColors.PrimaryDark else TouchColors.TextMuted
            )
        }
    }
}

@Composable
private fun AccountPanel(
    session: UserSession,
    avatarBitmap: Bitmap?,
    onAvatarBitmapChanged: (Bitmap?) -> Unit,
    authApiClient: AuthApiClient,
    mainHandler: Handler,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSessionChanged: (UserSession) -> Unit,
    onLogout: () -> Unit
) {
    var previewBackgroundBitmap by remember(session.userId, session.cardBackgroundUrl, session.cardBackgroundKey) {
        mutableStateOf<Bitmap?>(null)
    }
    var previewBackgroundUri by remember(session.userId, session.cardBackgroundUrl, session.cardBackgroundKey) {
        mutableStateOf<Uri?>(null)
    }
    var previewBackgroundKey by remember(session.userId, session.cardBackgroundUrl, session.cardBackgroundKey) {
        mutableStateOf(session.cardBackgroundKey ?: "mizuki")
    }
    var previewUsesDefaultBackground by remember(session.userId, session.cardBackgroundUrl, session.cardBackgroundKey) {
        mutableStateOf(session.cardBackgroundUrl == null)
    }
    LaunchedEffect(isEditing, session.cardBackgroundUrl, session.cardBackgroundKey) {
        if (!isEditing) {
            delay(PROFILE_EDITOR_EXIT_MS.toLong() + 80L)
            previewBackgroundBitmap = null
            previewBackgroundUri = null
            previewBackgroundKey = session.cardBackgroundKey ?: "mizuki"
            previewUsesDefaultBackground = session.cardBackgroundUrl == null
        }
    }
    val displayBackgroundBitmap = if (isEditing) previewBackgroundBitmap else null
    val displayBackgroundUrl = when {
        !isEditing -> session.cardBackgroundUrl
        previewBackgroundBitmap != null -> null
        previewUsesDefaultBackground -> null
        else -> session.cardBackgroundUrl
    }
    val displayBackgroundKey = if (isEditing) previewBackgroundKey else session.cardBackgroundKey

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .animateContentSize(
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .cuteClickable { onEditingChange(!isEditing) }
            ) {
                ProfileCardBackground(
                    imageUrl = displayBackgroundUrl,
                    backgroundKey = displayBackgroundKey,
                    previewBitmap = displayBackgroundBitmap,
                    authApiClient = authApiClient,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.14f))
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccountAvatar(name = session.displayName, bitmap = avatarBitmap)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = session.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = session.bio ?: "\u70b9\u51fb\u7f16\u8f91\u4e2a\u4eba\u540d\u7247",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.86f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text("\u9000\u51fa", fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileInfoPill("\u751f\u65e5", session.birthday ?: "\u672a\u8bbe\u7f6e", Modifier.weight(1f))
                ProfileInfoPill("\u6027\u522b", session.gender ?: "\u672a\u8bbe\u7f6e", Modifier.weight(1f))
                ProfileInfoPill("\u6700\u540e\u4e0a\u7ebf", formatLastSeen(session.lastSeenAt), Modifier.weight(1f))
            }
            AnimatedVisibility(
                visible = isEditing,
                enter = fadeIn(tween(INLINE_ENTER_MS, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(INLINE_ENTER_MS, easing = FastOutSlowInEasing)) { -it / 18 },
                exit = fadeOut(tween(PROFILE_EDITOR_EXIT_MS, easing = FastOutSlowInEasing)) +
                    slideOutVertically(tween(PROFILE_EDITOR_EXIT_MS, easing = FastOutSlowInEasing)) { -it / 24 }
            ) {
                AccountEditInline(
                    session = session,
                    onAvatarBitmapChanged = onAvatarBitmapChanged,
                    authApiClient = authApiClient,
                    mainHandler = mainHandler,
                    selectedBackgroundKey = previewBackgroundKey,
                    pendingBackgroundUri = previewBackgroundUri,
                    onBackgroundPreviewChanged = { key, uri, bitmap, usesDefault ->
                        previewBackgroundKey = key
                        previewBackgroundUri = uri
                        previewBackgroundBitmap = bitmap
                        previewUsesDefaultBackground = usesDefault
                    },
                    onSessionChanged = onSessionChanged,
                    onDismiss = { onEditingChange(false) }
                )
            }
        }
    }
}

@Composable
private fun AccountEditInline(
    session: UserSession,
    onAvatarBitmapChanged: (Bitmap?) -> Unit,
    authApiClient: AuthApiClient,
    mainHandler: Handler,
    selectedBackgroundKey: String,
    pendingBackgroundUri: Uri?,
    onBackgroundPreviewChanged: (String, Uri?, Bitmap?, Boolean) -> Unit,
    onSessionChanged: (UserSession) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var draftName by remember(session.displayName) { mutableStateOf(session.displayName) }
    var draftBio by remember(session.bio) { mutableStateOf(session.bio.orEmpty()) }
    var draftBirthday by remember(session.birthday) { mutableStateOf(session.birthday.orEmpty()) }
    var draftGender by remember(session.gender) { mutableStateOf(session.gender ?: "\u4fdd\u5bc6") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val avatarButtonInteraction = remember { MutableInteractionSource() }
    val saveButtonInteraction = remember { MutableInteractionSource() }
    val avatarButtonScale = rememberCutePressScale(avatarButtonInteraction)
    val saveButtonScale = rememberCutePressScale(saveButtonInteraction)
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val decoded = decodeBitmapFromUri(context, uri)
            onAvatarBitmapChanged(decoded)
            isSaving = true
            statusText = "\u6b63\u5728\u4e0a\u4f20\u5934\u50cf..."
            Thread {
                try {
                    val updated = authApiClient.uploadAvatar(context, session.accessToken, uri)
                    mainHandler.post {
                        onSessionChanged(session.withAccountUser(updated))
                        isSaving = false
                        statusText = "\u5934\u50cf\u5df2\u66f4\u65b0"
                        mainHandler.postDelayed({ onDismiss() }, PROFILE_SAVE_DISMISS_DELAY_MS)
                    }
                } catch (error: Exception) {
                    mainHandler.post {
                        isSaving = false
                        statusText = error.message ?: "\u5934\u50cf\u4e0a\u4f20\u5931\u8d25"
                    }
                }
            }.start()
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val decoded = decodeBitmapFromUri(context, uri)
            onBackgroundPreviewChanged(selectedBackgroundKey, uri, decoded, false)
            statusText = "\u80cc\u666f\u5df2\u9884\u89c8\uff0c\u70b9\u51fb\u4fdd\u5b58\u540e\u4e0a\u4f20"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = draftName,
            onValueChange = {
                draftName = it
                statusText = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("\u6635\u79f0") },
            singleLine = true
        )
        OutlinedTextField(
            value = draftBio,
            onValueChange = {
                draftBio = it.take(160)
                statusText = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("\u4e2a\u4eba\u7b80\u4ecb") },
            minLines = 2,
            maxLines = 3
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = draftBirthday,
                onValueChange = {
                    draftBirthday = it.take(10)
                    statusText = null
                },
                modifier = Modifier.weight(1f),
                label = { Text("\u751f\u65e5") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true
            )
            OutlinedTextField(
                value = draftGender,
                onValueChange = {
                    draftGender = it.take(8)
                    statusText = null
                },
                modifier = Modifier.weight(1f),
                label = { Text("\u6027\u522b") },
                placeholder = { Text("\u7537/\u5973/\u5176\u4ed6/\u4fdd\u5bc6") },
                singleLine = true
            )
        }
        Text(
            text = "\u540d\u7247\u80cc\u666f",
            style = MaterialTheme.typography.labelLarge,
            color = TouchColors.TextStrong,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultCardBackgroundChip("\u6c34\u6708", "mizuki", selectedBackgroundKey, Modifier.weight(1f)) {
                onBackgroundPreviewChanged("mizuki", null, null, true)
                statusText = null
            }
            DefaultCardBackgroundChip("\u7f2a\u5c14\u585e\u65af", "muelsyse", selectedBackgroundKey, Modifier.weight(1f)) {
                onBackgroundPreviewChanged("muelsyse", null, null, true)
                statusText = null
            }
            DefaultCardBackgroundChip("\u9ecd", "shu", selectedBackgroundKey, Modifier.weight(1f)) {
                onBackgroundPreviewChanged("shu", null, null, true)
                statusText = null
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                enabled = !isSaving,
                interactionSource = avatarButtonInteraction,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = avatarButtonScale
                        scaleY = avatarButtonScale
                    },
                shape = RoundedCornerShape(8.dp),
                colors = touchOutlinedButtonColors()
            ) {
                Text("\u66f4\u6362\u5934\u50cf")
            }
            OutlinedButton(
                onClick = { backgroundPicker.launch("image/*") },
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = touchOutlinedButtonColors()
            ) {
                Text("\u4e0a\u4f20\u80cc\u666f")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val nextName = draftName.trim()
                    if (nextName.isBlank()) {
                        statusText = "\u6635\u79f0\u4e0d\u80fd\u4e3a\u7a7a"
                    } else {
                        isSaving = true
                        statusText = "\u6b63\u5728\u4fdd\u5b58..."
                        Thread {
                            try {
                                val backgroundUpdated = if (pendingBackgroundUri != null) {
                                    authApiClient.uploadCardBackground(context, session.accessToken, pendingBackgroundUri)
                                } else {
                                    null
                                }
                                val updated = authApiClient.updateProfile(
                                    accessToken = session.accessToken,
                                    displayName = nextName,
                                    bio = draftBio.trim(),
                                    birthday = draftBirthday.trim(),
                                    gender = draftGender.trim(),
                                    cardBackgroundKey = if (pendingBackgroundUri == null) selectedBackgroundKey else null
                                )
                                mainHandler.post {
                                    onSessionChanged(session.withAccountUser(backgroundUpdated ?: updated).withAccountUser(updated))
                                    isSaving = false
                                    statusText = "\u8d44\u6599\u5df2\u4fdd\u5b58"
                                    mainHandler.postDelayed({ onDismiss() }, PROFILE_SAVE_DISMISS_DELAY_MS)
                                }
                            } catch (error: Exception) {
                                mainHandler.post {
                                    isSaving = false
                                    statusText = error.message ?: "\u4fdd\u5b58\u5931\u8d25"
                                }
                            }
                        }.start()
                    }
                },
                enabled = !isSaving,
                interactionSource = saveButtonInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = saveButtonScale
                        scaleY = saveButtonScale
                    },
                shape = RoundedCornerShape(8.dp),
                colors = touchPrimaryButtonColors()
            ) {
                Text(if (isSaving) "\u5904\u7406\u4e2d..." else "\u4fdd\u5b58")
            }
        }
        statusText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("\u5931\u8d25") || it.contains("\u4e0d\u80fd") || it.contains("\u5360\u7528")) {
                    TouchColors.Error
                } else {
                    TouchColors.TextMuted
                }
            )
        }
    }
}

private fun UserSession.withAccountUser(user: AccountUser): UserSession {
    return copy(
        userId = user.userId,
        displayName = user.displayName,
        email = user.email,
        avatarUrl = user.avatarUrl,
        bio = user.bio,
        birthday = user.birthday,
        gender = user.gender,
        cardBackgroundUrl = user.cardBackgroundUrl,
        cardBackgroundKey = user.cardBackgroundKey,
        lastSeenAt = user.lastSeenAt
    )
}

@Composable
private fun ProfileInfoPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = TouchColors.DetailSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TouchColors.TextMuted,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DefaultCardBackgroundChip(
    label: String,
    key: String,
    selectedKey: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selected = key == selectedKey
    Surface(
        modifier = modifier
            .height(38.dp)
            .cuteClickable(onClick = onClick),
        color = if (selected) TouchColors.SuccessSoft else TouchColors.CalendarTile,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) TouchColors.PrimaryDark else TouchColors.TextMuted,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfileCardBackground(
    imageUrl: String?,
    backgroundKey: String?,
    previewBitmap: Bitmap? = null,
    authApiClient: AuthApiClient,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) {
            bitmap = null
        } else {
            Thread {
                val loaded = runCatching { authApiClient.loadAvatarBitmap(imageUrl) }.getOrNull()
                Handler(Looper.getMainLooper()).post {
                    bitmap = loaded
                }
            }.start()
        }
    }
    Box(modifier = modifier.background(defaultCardBackgroundColor(backgroundKey))) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            DefaultCardBackgroundCanvas(backgroundKey = backgroundKey, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DefaultCardBackgroundCanvas(backgroundKey: String?, modifier: Modifier = Modifier) {
    val base = defaultCardBackgroundColor(backgroundKey)
    val accent = when (backgroundKey) {
        "muelsyse" -> Color(0xFF9DE5D5)
        "shu" -> Color(0xFFE5C15A)
        else -> Color(0xFF8FC7FF)
    }
    val deep = when (backgroundKey) {
        "muelsyse" -> Color(0xFF267B72)
        "shu" -> Color(0xFF7F9F56)
        else -> Color(0xFF5E87D6)
    }
    Canvas(modifier = modifier.background(base)) {
        drawCircle(
            color = accent.copy(alpha = 0.48f),
            radius = size.maxDimension * 0.42f,
            center = Offset(size.width * 0.18f, size.height * 0.1f)
        )
        drawCircle(
            color = deep.copy(alpha = 0.28f),
            radius = size.maxDimension * 0.36f,
            center = Offset(size.width * 0.92f, size.height * 0.88f)
        )
        repeat(6) { index ->
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = size.minDimension * (0.06f + index * 0.012f),
                center = Offset(size.width * (0.18f + index * 0.13f), size.height * (0.68f - index * 0.07f))
            )
        }
    }
}

private fun defaultCardBackgroundColor(backgroundKey: String?): Color {
    return when (backgroundKey) {
        "muelsyse" -> Color(0xFFBFEFE5)
        "shu" -> Color(0xFFF3E5A7)
        else -> Color(0xFFC9DFFF)
    }
}

private fun formatLastSeen(lastSeenAt: Long?): String {
    if (lastSeenAt == null || lastSeenAt <= 0L) return "\u672a\u77e5"
    val now = System.currentTimeMillis() / 1000
    val delta = (now - lastSeenAt).coerceAtLeast(0)
    return when {
        delta < 90 -> "\u521a\u521a"
        delta < 3600 -> "${delta / 60}\u5206\u949f\u524d"
        delta < 86_400 -> "${delta / 3600}\u5c0f\u65f6\u524d"
        else -> "${delta / 86_400}\u5929\u524d"
    }
}

private fun decodeBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    }
}

private fun encodeImageBase64(context: Context, uri: Uri): String? {
    val original = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: return null
    val maxSide = 1280f
    val scale = minOf(1f, maxSide / maxOf(original.width, original.height).toFloat())
    val scaled = if (scale < 1f) {
        Bitmap.createBitmap(
            original,
            0,
            0,
            original.width,
            original.height,
            Matrix().apply { postScale(scale, scale) },
            true
        )
    } else {
        original
    }
    return ByteArrayOutputStream().use { output ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, output)
        if (scaled !== original) {
            scaled.recycle()
        }
        original.recycle()
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}

private fun decodeBase64Bitmap(raw: String): Bitmap? {
    return runCatching {
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

@Composable
private fun rememberCutePressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(PRESS_ANIMATION_MS, easing = FastOutSlowInEasing),
        label = "cute-press-scale"
    ).value
}

@Composable
private fun Modifier.cuteClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberCutePressScale(interactionSource)
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
private fun Modifier.clearFocusOnBackgroundTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            focusManager.clearFocus(force = true)
            waitForUpOrCancellation(pass = PointerEventPass.Final)
        }
    }
}

@Composable
private fun touchPrimaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = TouchColors.Primary,
    contentColor = Color.White,
    disabledContainerColor = TouchColors.CalendarTile,
    disabledContentColor = TouchColors.TextMuted
)

@Composable
private fun touchSecondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = TouchColors.Secondary,
    contentColor = Color.White,
    disabledContainerColor = TouchColors.CalendarTile,
    disabledContentColor = TouchColors.TextMuted
)

@Composable
private fun touchDangerButtonColors() = ButtonDefaults.buttonColors(
    containerColor = TouchColors.Error,
    contentColor = Color.White,
    disabledContainerColor = TouchColors.ErrorSoft,
    disabledContentColor = TouchColors.TextMuted
)

@Composable
private fun touchOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = TouchColors.PrimaryDark,
    disabledContentColor = TouchColors.TextMuted
)

@Composable
private fun touchDangerTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = TouchColors.Error,
    disabledContentColor = TouchColors.TextMuted
)

@Composable
private fun touchTextButtonColors() = ButtonDefaults.textButtonColors(
    contentColor = TouchColors.PrimaryDark,
    disabledContentColor = TouchColors.TextMuted
)

@Composable
private fun Header(isReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Touch",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TouchColors.Primary,
                    fontWeight = FontWeight.Bold
                )
                ReadinessPill(isReady = isReady)
            }
        }
    }
}

@Composable
private fun ReadinessPill(isReady: Boolean) {
    val background = if (isReady) TouchColors.SuccessSoft else TouchColors.ErrorSoft
    val foreground = if (isReady) TouchColors.Success else TouchColors.Error
    val label = if (isReady) "\u5df2\u5c31\u7eea" else "\u672a\u5c31\u7eea"

    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(foreground)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BottomTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TouchColors.Background.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TouchColors.Surface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                BottomTabItem(
                    tab = MainTab.Home,
                    label = "\u4e3b\u754c\u9762",
                    selected = selectedTab == MainTab.Home,
                    onClick = { onTabSelected(MainTab.Home) },
                    modifier = Modifier.weight(1f)
                )
                BottomTabItem(
                    tab = MainTab.Friends,
                    label = "\u597d\u53cb",
                    selected = selectedTab == MainTab.Friends,
                    onClick = { onTabSelected(MainTab.Friends) },
                    modifier = Modifier.weight(1f)
                )
                BottomTabItem(
                    tab = MainTab.Mine,
                    label = "\u6211\u7684",
                    selected = selectedTab == MainTab.Mine,
                    onClick = { onTabSelected(MainTab.Mine) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: MainTab,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "bottom-tab-selected"
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) -2f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "bottom-tab-lift"
    )
    val backgroundColor = if (selected) {
        TouchColors.Primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }
    val iconColor = if (selected) TouchColors.PrimaryDark else TouchColors.TextMuted
    val textColor = if (selected) TouchColors.PrimaryDark else TouchColors.TextMuted

    Box(
        modifier = modifier
            .height(54.dp)
            .graphicsLayer { translationY = lift }
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .cuteClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 7.dp, bottom = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BottomTabGlyph(
                tab = tab,
                selected = selected,
                tint = iconColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width((12 + 18 * selectedProgress).dp)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(TouchColors.Accent.copy(alpha = 0.28f + 0.62f * selectedProgress))
        )
    }
}

@Composable
private fun BottomTabGlyph(
    tab: MainTab,
    selected: Boolean,
    tint: Color
) {
    val pulse by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "bottom-tab-glyph"
    )
    Canvas(modifier = Modifier.size(21.dp)) {
        val stroke = Stroke(width = 2.1.dp.toPx())
        val softTint = tint.copy(alpha = 0.32f + 0.28f * pulse)
        when (tab) {
            MainTab.Home -> {
                drawRoundRect(
                    color = softTint,
                    topLeft = Offset(size.width * 0.2f, size.height * 0.3f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.22f, size.height * 0.42f),
                    end = Offset(size.width * 0.5f, size.height * 0.17f),
                    strokeWidth = 2.1.dp.toPx()
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.5f, size.height * 0.17f),
                    end = Offset(size.width * 0.78f, size.height * 0.42f),
                    strokeWidth = 2.1.dp.toPx()
                )
            }
            MainTab.Friends -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.16f,
                    center = Offset(size.width * 0.38f, size.height * 0.36f)
                )
                drawCircle(
                    color = softTint,
                    radius = size.minDimension * 0.13f,
                    center = Offset(size.width * 0.65f, size.height * 0.42f)
                )
                drawRoundRect(
                    color = tint.copy(alpha = 0.82f),
                    topLeft = Offset(size.width * 0.18f, size.height * 0.62f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.58f, size.height * 0.16f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx())
                )
            }
            MainTab.Mine -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.16f,
                    center = Offset(size.width * 0.5f, size.height * 0.34f)
                )
                drawRoundRect(
                    color = softTint,
                    topLeft = Offset(size.width * 0.24f, size.height * 0.6f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.18f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun FriendEntryPanel(
    friendships: List<Friendship>,
    errorText: String?,
    onOpen: () -> Unit
) {
    val acceptedCount = friendships.count { it.status == "accepted" }
    val incomingCount = friendships.count { it.status == "pending" && it.direction == "incoming" }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .cuteClickable(onClick = onOpen),
        color = TouchColors.Surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "\u597d\u53cb\u7ba1\u7406",
                    style = MaterialTheme.typography.titleMedium,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = errorText ?: "\u5df2\u6709 ${acceptedCount} \u4f4d\u597d\u53cb\uff0c${incomingCount} \u4e2a\u5f85\u5904\u7406\u7533\u8bf7",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorText == null) TouchColors.TextMuted else TouchColors.Error
                )
            }
            StatusPill(
                text = "\u6253\u5f00",
                background = TouchColors.CalendarTile,
                foreground = TouchColors.Primary
            )
        }
    }
}

@Composable
private fun FriendManagementScreen(
    modifier: Modifier = Modifier,
    session: UserSession,
    authApiClient: AuthApiClient,
    mainHandler: Handler,
    friendships: List<Friendship>,
    meetingRecords: List<MeetingRecord>,
    errorText: String?,
    showBackButton: Boolean = true,
    onFriendCardOpen: (Friendship) -> Unit = {},
    onBack: () -> Unit,
    onFriendshipsChanged: (List<Friendship>) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FriendUser>>(emptyList()) }
    var statusText by remember { mutableStateOf<String?>(errorText) }
    var isBusy by remember { mutableStateOf(false) }
    var friendPendingRemove by remember { mutableStateOf<Friendship?>(null) }
    val primaryButtonColors = touchPrimaryButtonColors()
    val outlineButtonColors = touchOutlinedButtonColors()
    val dangerTextButtonColors = touchDangerTextButtonColors()
    val accepted = friendships
        .filter { it.status == "accepted" && it.friend != null }
        .sortedWith(
            compareByDescending<Friendship> {
                friendMeetRatioScore(meetingRecords, it.friend?.userId.orEmpty())
            }.thenBy { it.friend?.displayName.orEmpty() }
        )
    val incoming = friendships.filter { it.status == "pending" && it.direction == "incoming" && it.friend != null }
    val outgoing = friendships.filter { it.status == "pending" && it.direction == "outgoing" && it.friend != null }

    fun runFriendAction(action: () -> Unit) {
        isBusy = true
        Thread {
            try {
                action()
                mainHandler.post {
                    isBusy = false
                    statusText = null
                    onFriendshipsChanged(friendships)
                }
            } catch (error: Exception) {
                mainHandler.post {
                    isBusy = false
                    statusText = error.message ?: "\u64cd\u4f5c\u5931\u8d25"
                }
            }
        }.start()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 18.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "friend-header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "\u597d\u53cb\u7ba1\u7406",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TouchColors.TextStrong,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\u53ea\u6709\u597d\u53cb\u4e4b\u95f4\u7684\u78b0\u4e00\u78b0\u4f1a\u88ab\u8bb0\u5f55",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TouchColors.TextMuted
                    )
                }
                if (showBackButton) {
                    TextButton(onClick = onBack, colors = touchTextButtonColors()) {
                        Text("\u8fd4\u56de", color = TouchColors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item(key = "friend-search") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "\u641c\u7d22\u65b0\u597d\u53cb",
                        style = MaterialTheme.typography.titleMedium,
                        color = TouchColors.TextStrong,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = {
                                searchText = it
                                statusText = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            label = { Text("\u7528\u6237\u540d") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (searchText.trim().isBlank()) {
                                    statusText = "\u8bf7\u8f93\u5165\u8981\u641c\u7d22\u7684\u7528\u6237\u540d"
                                } else {
                                    isBusy = true
                                    Thread {
                                        try {
                                            val results = authApiClient.searchUsers(session.accessToken, searchText.trim())
                                                .map { it.toFriendUser() }
                                            mainHandler.post {
                                                searchResults = results
                                                isBusy = false
                                                statusText = if (results.isEmpty()) "\u6ca1\u6709\u627e\u5230\u5339\u914d\u7528\u6237" else null
                                            }
                                        } catch (error: Exception) {
                                            mainHandler.post {
                                                isBusy = false
                                                statusText = error.message ?: "\u641c\u7d22\u5931\u8d25"
                                            }
                                        }
                                    }.start()
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = primaryButtonColors
                        ) {
                            Text("\u641c\u7d22")
                        }
                    }
                    searchResults.forEach { user ->
                        FriendUserRow(
                            user = user,
                            authApiClient = authApiClient,
                            trailing = {
                                OutlinedButton(
                                    onClick = {
                                        isBusy = true
                                        Thread {
                                            try {
                                                authApiClient.sendFriendRequest(session.accessToken, user.userId)
                                                mainHandler.post {
                                                    isBusy = false
                                                    searchResults = emptyList()
                                                    searchText = ""
                                                    statusText = "\u5df2\u53d1\u9001\u597d\u53cb\u7533\u8bf7"
                                                    onFriendshipsChanged(friendships)
                                                }
                                            } catch (error: Exception) {
                                                mainHandler.post {
                                                    isBusy = false
                                                    statusText = error.message ?: "\u597d\u53cb\u7533\u8bf7\u53d1\u9001\u5931\u8d25"
                                                }
                                            }
                                        }.start()
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = outlineButtonColors
                                ) {
                                    Text("\u7533\u8bf7")
                                }
                            }
                        )
                    }
                }
            }
        }
        item(key = "friend-status") {
            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.contains("\u5931\u8d25")) TouchColors.Error else TouchColors.TextMuted
                )
            }
        }
        item(key = "incoming") {
            FriendSection(
                title = "\u6536\u5230\u7684\u7533\u8bf7",
                emptyText = "\u6682\u65e0\u5f85\u5904\u7406\u7533\u8bf7",
                isEmpty = incoming.isEmpty()
            ) {
                incoming.forEach { friendship ->
                    friendship.friend?.let { user ->
                        FriendUserRow(
                            user = user,
                            authApiClient = authApiClient,
                            subtitle = "\u60f3\u6dfb\u52a0\u4f60\u4e3a\u597d\u53cb",
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            runFriendAction {
                                                authApiClient.respondFriendRequest(session.accessToken, friendship.id, "reject")
                                            }
                                        },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = outlineButtonColors
                                    ) {
                                        Text("\u62d2\u7edd")
                                    }
                                    Button(
                                        onClick = {
                                            runFriendAction {
                                                authApiClient.respondFriendRequest(session.accessToken, friendship.id, "accept")
                                            }
                                        },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = primaryButtonColors
                                    ) {
                                        Text("\u63a5\u53d7")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        item(key = "friends") {
            FriendSection(
                title = "\u6211\u7684\u597d\u53cb",
                emptyText = "\u8fd8\u6ca1\u6709\u597d\u53cb",
                isEmpty = accepted.isEmpty()
            ) {
                accepted.forEach { friendship ->
                    friendship.friend?.let { user ->
                        FriendUserRow(
                            user = user,
                            authApiClient = authApiClient,
                            onAvatarClick = { onFriendCardOpen(friendship) },
                            subtitle = friendMeetRatioLabel(meetingRecords, user.userId),
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            runFriendAction {
                                                authApiClient.blockFriend(
                                                    session.accessToken,
                                                    user.userId,
                                                    !friendship.blockedByMe
                                                )
                                            }
                                        },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = outlineButtonColors
                                    ) {
                                        Text(if (friendship.blockedByMe) "\u53d6\u6d88\u5c4f\u853d" else "\u5c4f\u853d")
                                    }
                                    TextButton(
                                        onClick = { friendPendingRemove = friendship },
                                        enabled = !isBusy,
                                        colors = dangerTextButtonColors
                                    ) {
                                        Text("\u5220\u9664")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        item(key = "outgoing") {
            FriendSection(
                title = "\u5df2\u53d1\u9001\u7684\u7533\u8bf7",
                emptyText = "\u6682\u65e0\u53d1\u51fa\u7684\u7533\u8bf7",
                isEmpty = outgoing.isEmpty()
            ) {
                outgoing.forEach { friendship ->
                    friendship.friend?.let { user ->
                        FriendUserRow(
                            user = user,
                            authApiClient = authApiClient,
                            subtitle = "\u7b49\u5f85\u5bf9\u65b9\u5904\u7406"
                        )
                    }
                }
            }
        }
    }
    friendPendingRemove?.let { friendship ->
        val user = friendship.friend
        if (user != null) {
            ConfirmRemoveFriendDialog(
                friendName = user.displayName,
                isBusy = isBusy,
                onCancel = { if (!isBusy) friendPendingRemove = null },
                onConfirm = {
                    friendPendingRemove = null
                    runFriendAction {
                        authApiClient.removeFriend(session.accessToken, user.userId)
                    }
                }
            )
        }
    }
}

@Composable
private fun FriendSection(
    title: String,
    emptyText: String,
    isEmpty: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )
            if (isEmpty) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TouchColors.TextMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ConfirmRemoveFriendDialog(
    friendName: String,
    isBusy: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "\u5220\u9664\u597d\u53cb\uff1f",
                    style = MaterialTheme.typography.titleLarge,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\u5220\u9664 $friendName \u540e\uff0c\u4f60\u5c06\u4e0d\u518d\u770b\u5230\u4e0e\u8be5\u597d\u53cb\u76f8\u5173\u7684\u89c1\u9762\u8bb0\u5f55\uff1b\u53ea\u6709\u8be5\u597d\u53cb\u53c2\u4e0e\u7684\u4e8b\u4ef6\u4e5f\u4f1a\u4ece\u4f60\u7684\u5217\u8868\u4e2d\u9690\u85cf\u3002",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TouchColors.TextMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchOutlinedButtonColors()
                    ) {
                        Text("\u53d6\u6d88")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchDangerButtonColors()
                    ) {
                        Text(if (isBusy) "\u5220\u9664\u4e2d..." else "\u786e\u8ba4\u5220\u9664")
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendUserRow(
    user: FriendUser,
    authApiClient: AuthApiClient,
    subtitle: String = user.email,
    onAvatarClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        color = TouchColors.DetailSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FriendAvatar(
                user = user,
                authApiClient = authApiClient,
                modifier = if (onAvatarClick != null) {
                    Modifier.cuteClickable(onClick = onAvatarClick)
                } else {
                    Modifier
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TouchColors.TextMuted
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun FriendCardDialog(
    friendship: Friendship,
    meetingRecords: List<MeetingRecord>,
    authApiClient: AuthApiClient,
    accessToken: String,
    mainHandler: Handler,
    onDismiss: () -> Unit,
    onFriendshipChanged: (Friendship) -> Unit
) {
    val friend = friendship.friend ?: return
    var remark by remember(friendship.id, friendship.remark) { mutableStateOf(friendship.remark ?: "") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val weekCount = countMeetingsWithFriendWithin(meetingRecords, friend.userId, 7)
    val monthCount = countMeetingsWithFriendWithin(meetingRecords, friend.userId, 30)
    val yearCount = countMeetingsWithFriendWithin(meetingRecords, friend.userId, 365)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    ProfileCardBackground(
                        imageUrl = friend.cardBackgroundUrl,
                        backgroundKey = friend.cardBackgroundKey,
                        authApiClient = authApiClient,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.16f))
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FriendAvatar(user = friend, authApiClient = authApiClient)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = remark.ifBlank { friend.displayName },
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = friend.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (!friend.bio.isNullOrBlank()) {
                    Text(
                        text = friend.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TouchColors.TextStrong
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileInfoPill("\u751f\u65e5", friend.birthday ?: "\u672a\u8bbe\u7f6e", Modifier.weight(1f))
                    ProfileInfoPill("\u6027\u522b", friend.gender ?: "\u672a\u8bbe\u7f6e", Modifier.weight(1f))
                    ProfileInfoPill("\u4e0a\u7ebf", formatLastSeen(friend.lastSeenAt), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FriendStatPill("\u8fd1\u4e00\u5468", weekCount, Modifier.weight(1f))
                    FriendStatPill("\u8fd1\u4e00\u6708", monthCount, Modifier.weight(1f))
                    FriendStatPill("\u8fd1\u4e00\u5e74", yearCount, Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = remark,
                    onValueChange = {
                        remark = it
                        statusText = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u597d\u53cb\u5907\u6ce8") },
                    singleLine = true
                )
                statusText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("\u5931\u8d25")) TouchColors.Error else TouchColors.TextMuted
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchOutlinedButtonColors()
                    ) {
                        Text("\u5173\u95ed")
                    }
                    Button(
                        onClick = {
                            isSaving = true
                            Thread {
                                try {
                                    val updated = authApiClient.updateFriendRemark(accessToken, friend.userId, remark.trim())
                                    mainHandler.post {
                                        isSaving = false
                                        statusText = "\u5907\u6ce8\u5df2\u4fdd\u5b58"
                                        onFriendshipChanged(updated.toFriendship())
                                    }
                                } catch (error: Exception) {
                                    mainHandler.post {
                                        isSaving = false
                                        statusText = error.message ?: "\u4fdd\u5b58\u5931\u8d25"
                                    }
                                }
                            }.start()
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchPrimaryButtonColors()
                    ) {
                        Text(if (isSaving) "\u4fdd\u5b58\u4e2d..." else "\u4fdd\u5b58")
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendStatPill(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = TouchColors.DetailSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TouchColors.TextMuted
            )
        }
    }
}

@Composable
private fun FriendFilterRow(
    friends: List<FriendUser>,
    selectedFriend: FriendUser?,
    onFriendFilterChanged: (FriendUser?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = selectedFriend?.displayName ?: "\u5168\u90e8",
            modifier = Modifier.cuteClickable { expanded = !expanded },
            style = MaterialTheme.typography.labelMedium,
            color = TouchColors.TextMuted,
            fontWeight = FontWeight.SemiBold
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(INLINE_ENTER_MS, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(INLINE_ENTER_MS, easing = FastOutSlowInEasing)) { -it / 18 },
            exit = fadeOut(tween(INLINE_EXIT_MS, easing = FastOutSlowInEasing))
        ) {
            Surface(
                color = TouchColors.DetailSurface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FriendFilterChip(
                        label = "\u5168\u90e8",
                        selected = selectedFriend == null,
                        onClick = {
                            onFriendFilterChanged(null)
                            expanded = false
                        }
                    )
                    friends.forEach { friend ->
                        FriendFilterChip(
                            label = friend.displayName,
                            selected = selectedFriend?.userId == friend.userId,
                            onClick = {
                                onFriendFilterChanged(friend)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) TouchColors.Primary else TouchColors.CalendarTile,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.cuteClickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else TouchColors.TextStrong,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MeetingCalendar(
    calendarMeetings: List<CalendarMeeting>,
    allRecords: List<MeetingRecord>,
    focusedYear: Int,
    focusedMonth: Int,
    viewMode: CalendarViewMode,
    detail: CalendarDetail?,
    friends: List<FriendUser>,
    authApiClient: AuthApiClient,
    selectedFriend: FriendUser?,
    isFriendFiltered: Boolean,
    onFriendFilterChanged: (FriendUser?) -> Unit,
    onRangeSwipe: (Int) -> Unit,
    onViewModeChange: (CalendarViewMode) -> Unit,
    onClearDetail: () -> Unit,
    onDaySelected: (String, List<MeetingRecord>) -> Unit,
    onDayLongPressed: (String, List<MeetingRecord>) -> Unit,
    onMonthDaySelected: (MonthDayMeeting) -> Unit,
    onMonthDayLongPressed: (MonthDayMeeting) -> Unit,
    onYearMonthSelected: (YearMonthMeeting) -> Unit
) {
    val monthMeetings by remember(allRecords, focusedYear, focusedMonth) {
        derivedStateOf { buildMonthMeetings(allRecords, focusedYear, focusedMonth) }
    }
    val yearMeetings by remember(allRecords, focusedYear) {
        derivedStateOf { buildYearMeetings(allRecords, focusedYear) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(CALENDAR_SIZE_ANIMATION_MS, easing = FastOutSlowInEasing)
            ),
        colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(detail, viewMode) {
                        detectTapGestures {
                            if (viewMode == CalendarViewMode.Week && detail != null) {
                                onClearDetail()
                            }
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when (viewMode) {
                            CalendarViewMode.Week -> "\u672c\u5468\u78b0\u4e00\u78b0"
                            CalendarViewMode.Month -> "\u672c\u6708\u78b0\u4e00\u78b0"
                            CalendarViewMode.Year -> "\u672c\u5e74\u78b0\u4e00\u78b0"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TouchColors.TextStrong,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (viewMode) {
                            CalendarViewMode.Week -> "\u6309\u5468\u4e00\u5230\u5468\u65e5\u67e5\u770b\u89c1\u9762\u8bb0\u5f55"
                            CalendarViewMode.Month -> "\u6309\u6708\u5386\u67e5\u770b\u89c1\u9762\u8bb0\u5f55"
                            CalendarViewMode.Year -> "\u6309\u6708\u4efd\u67e5\u770b\u89c1\u9762\u8bb0\u5f55"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TouchColors.TextMuted
                    )
                }
                CalendarModeSwitch(
                    viewMode = viewMode,
                    onViewModeChange = onViewModeChange
                )
            }

            Text(
                text = when (viewMode) {
                    CalendarViewMode.Week -> weekRangeLabel(calendarMeetings)
                    CalendarViewMode.Month -> "${focusedYear}.${focusedMonth.toString().padStart(2, '0')}"
                    CalendarViewMode.Year -> focusedYear.toString()
                },
                modifier = Modifier.pointerInput(detail, viewMode) {
                    detectTapGestures {
                        if (viewMode == CalendarViewMode.Week && detail != null) {
                            onClearDetail()
                        }
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = TouchColors.TextMuted,
                fontWeight = FontWeight.Medium
            )

            FriendFilterRow(
                friends = friends,
                selectedFriend = selectedFriend,
                onFriendFilterChanged = onFriendFilterChanged
            )

            var dragOffset by remember(viewMode, focusedYear, focusedMonth, calendarMeetings.firstOrNull()?.date) {
                mutableStateOf(0f)
            }
            var isDraggingCalendar by remember(viewMode, focusedYear, focusedMonth, calendarMeetings.firstOrNull()?.date) {
                mutableStateOf(false)
            }
            var rangeSwitchDirection by remember { mutableIntStateOf(0) }
            val rangeSwitchOffset = remember { Animatable(0f) }
            val modeTransitionAlpha = remember { Animatable(1f) }
            val modeTransitionScale = remember { Animatable(1f) }
            val modeTransitionOffset = remember { Animatable(0f) }
            val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
            val rangeSnapOffset = with(LocalDensity.current) { 20.dp.toPx() }
            val modeSnapOffset = with(LocalDensity.current) { 10.dp.toPx() }
            LaunchedEffect(viewMode) {
                modeTransitionAlpha.snapTo(0.86f)
                modeTransitionScale.snapTo(0.994f)
                modeTransitionOffset.snapTo(modeSnapOffset * 0.55f)
                coroutineScope {
                    launch {
                        modeTransitionAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(CALENDAR_CONTENT_ANIMATION_MS, easing = FastOutSlowInEasing)
                        )
                    }
                    launch {
                        modeTransitionScale.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(CALENDAR_CONTENT_ANIMATION_MS, easing = FastOutSlowInEasing)
                        )
                    }
                    launch {
                        modeTransitionOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(CALENDAR_CONTENT_ANIMATION_MS, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }
            LaunchedEffect(viewMode, focusedYear, focusedMonth, calendarMeetings.firstOrNull()?.date) {
                if (rangeSwitchDirection != 0) {
                    rangeSwitchOffset.snapTo(-rangeSwitchDirection * rangeSnapOffset)
                    rangeSwitchOffset.animateTo(
                        targetValue = rangeSwitchDirection * 3.5f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    )
                    rangeSwitchOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                    )
                    rangeSwitchDirection = 0
                }
            }
            val visualDragOffset by animateFloatAsState(
                targetValue = if (isDraggingCalendar) {
                    dragOffset.coerceIn(-swipeThreshold, swipeThreshold) * 0.32f
                } else {
                    0f
                },
                animationSpec = tween(DRAG_FEEDBACK_MS, easing = FastOutSlowInEasing),
                label = "calendar-drag-offset"
            )
            Box(
                modifier = if (viewMode == CalendarViewMode.Week) {
                    Modifier
                } else {
                    Modifier.pointerInput(viewMode, focusedYear, focusedMonth, calendarMeetings.firstOrNull()?.date) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragOffset = 0f
                                isDraggingCalendar = true
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffset += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    dragOffset <= -swipeThreshold -> {
                                        rangeSwitchDirection = 1
                                        onRangeSwipe(1)
                                    }
                                    dragOffset >= swipeThreshold -> {
                                        rangeSwitchDirection = -1
                                        onRangeSwipe(-1)
                                    }
                                }
                                dragOffset = 0f
                                isDraggingCalendar = false
                            },
                            onDragCancel = {
                                dragOffset = 0f
                                isDraggingCalendar = false
                            }
                        )
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .animateContentSize(
                            animationSpec = tween(CALENDAR_SIZE_ANIMATION_MS, easing = FastOutSlowInEasing)
                        )
                        .graphicsLayer {
                            translationX = if (isDraggingCalendar) visualDragOffset else rangeSwitchOffset.value
                            translationY = modeTransitionOffset.value
                            alpha = modeTransitionAlpha.value
                            scaleX = modeTransitionScale.value
                            scaleY = modeTransitionScale.value
                        }
                ) {
                    when (viewMode) {
                        CalendarViewMode.Week -> WeekCalendarView(
                            calendarMeetings = calendarMeetings,
                            friends = friends,
                            authApiClient = authApiClient,
                            onDaySelected = onDaySelected,
                            onDayLongPressed = onDayLongPressed,
                            onRangeSwipe = onRangeSwipe
                        )
                        CalendarViewMode.Month -> MonthCalendarView(
                            monthMeetings = monthMeetings,
                            isFriendFiltered = isFriendFiltered,
                            onMonthDaySelected = onMonthDaySelected,
                            onMonthDayLongPressed = onMonthDayLongPressed
                        )
                        CalendarViewMode.Year -> YearCalendarView(yearMeetings, isFriendFiltered, onYearMonthSelected)
                    }
                }
            }

            AnimatedVisibility(
                visible = detail != null,
                enter = fadeIn(tween(INLINE_ENTER_MS, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(INLINE_ENTER_MS, easing = FastOutSlowInEasing)) { -it / 20 },
                exit = fadeOut(tween(INLINE_EXIT_MS, easing = FastOutSlowInEasing))
            ) {
                detail?.let {
                    CalendarDetailPanel(
                        detail = it,
                        friends = friends,
                        authApiClient = authApiClient,
                        modifier = Modifier.pointerInput(it) {
                            detectTapGestures { onClearDetail() }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarModeSwitch(
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalendarModeButton("\u5468", CalendarViewMode.Week, viewMode, onViewModeChange)
        CalendarModeButton("\u6708", CalendarViewMode.Month, viewMode, onViewModeChange)
        CalendarModeButton("\u5e74", CalendarViewMode.Year, viewMode, onViewModeChange)
    }
}

@Composable
private fun CalendarModeButton(
    label: String,
    mode: CalendarViewMode,
    currentMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit
) {
    val selected = mode == currentMode
    Surface(
        color = if (selected) TouchColors.Primary else TouchColors.CalendarTile,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.cuteClickable { onViewModeChange(mode) }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else TouchColors.TextStrong,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WeekCalendarView(
    calendarMeetings: List<CalendarMeeting>,
    friends: List<FriendUser>,
    authApiClient: AuthApiClient,
    onDaySelected: (String, List<MeetingRecord>) -> Unit,
    onDayLongPressed: (String, List<MeetingRecord>) -> Unit,
    onRangeSwipe: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    var edgeDragOffset by remember(calendarMeetings.firstOrNull()?.date) { mutableStateOf(0f) }
    var isDraggingEdge by remember(calendarMeetings.firstOrNull()?.date) { mutableStateOf(false) }
    var edgeSwitchLocked by remember { mutableStateOf(false) }
    var reboundDirection by remember { mutableIntStateOf(0) }
    var reboundToken by remember { mutableIntStateOf(0) }
    val swipeThreshold = with(LocalDensity.current) { 118.dp.toPx() }
    val reboundVisualOffset = remember { Animatable(0f) }
    val reboundStartOffset = with(LocalDensity.current) { 34.dp.toPx() }
    suspend fun reboundToWeekCenter() {
        if (scrollState.maxValue > 0) {
            val center = scrollState.maxValue / 2
            scrollState.scrollTo(center)
        }
        reboundVisualOffset.snapTo(-reboundDirection * reboundStartOffset)
        reboundVisualOffset.animateTo(
            targetValue = reboundDirection * 7f,
            animationSpec = tween(REBOUND_ANIMATION_MS, easing = FastOutSlowInEasing)
        )
        reboundVisualOffset.animateTo(
            targetValue = 0f,
            animationSpec = tween(170, easing = FastOutSlowInEasing)
        )
    }
    LaunchedEffect(calendarMeetings.firstOrNull()?.date, scrollState.maxValue, reboundToken) {
        if (reboundToken > 0) {
            delay(80)
            reboundToWeekCenter()
            delay(120)
            edgeDragOffset = 0f
            isDraggingEdge = false
            edgeSwitchLocked = false
            reboundDirection = 0
        } else if (scrollState.maxValue > 0 && scrollState.value == 0) {
            scrollState.scrollTo(scrollState.maxValue / 2)
        }
    }
    val edgeNestedScrollConnection = remember(
        swipeThreshold,
        scrollState,
        edgeSwitchLocked,
        edgeDragOffset,
        reboundToken
    ) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || edgeSwitchLocked) {
                    return Offset.Zero
                }
                val atStart = scrollState.value <= 0
                val atEnd = scrollState.value >= scrollState.maxValue
                val edgeDelta = when {
                    atStart && available.x > 0f -> available.x
                    atEnd && available.x < 0f -> available.x
                    else -> 0f
                }
                if (edgeDelta == 0f) {
                    if (edgeDragOffset != 0f) {
                        edgeDragOffset = 0f
                        isDraggingEdge = false
                    }
                    return Offset.Zero
                }
                edgeDragOffset += edgeDelta
                isDraggingEdge = true
                when {
                    edgeDragOffset >= swipeThreshold -> {
                        edgeDragOffset = 0f
                        isDraggingEdge = false
                        edgeSwitchLocked = true
                        reboundDirection = -1
                        reboundToken += 1
                        onRangeSwipe(-1)
                    }
                    edgeDragOffset <= -swipeThreshold -> {
                        edgeDragOffset = 0f
                        isDraggingEdge = false
                        edgeSwitchLocked = true
                        reboundDirection = 1
                        reboundToken += 1
                        onRangeSwipe(1)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                edgeDragOffset = 0f
                isDraggingEdge = false
                if (!edgeSwitchLocked) {
                    edgeSwitchLocked = false
                }
                return Velocity.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                edgeDragOffset = 0f
                isDraggingEdge = false
                if (!edgeSwitchLocked) {
                    edgeSwitchLocked = false
                }
                return Velocity.Zero
            }
        }
    }
    val visualEdgeOffset by animateFloatAsState(
        targetValue = if (isDraggingEdge) {
            edgeDragOffset.coerceIn(-swipeThreshold, swipeThreshold) * 0.14f
        } else {
            0f
        },
        animationSpec = tween(DRAG_FEEDBACK_MS, easing = FastOutSlowInEasing),
        label = "week-edge-drag"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(edgeNestedScrollConnection)
            .horizontalScroll(scrollState)
            .padding(bottom = 5.dp)
            .graphicsLayer {
                translationX = visualEdgeOffset + reboundVisualOffset.value
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        calendarMeetings.forEach { day ->
            CalendarDayCard(
                meeting = day,
                friends = friends,
                authApiClient = authApiClient,
                onClick = { onDaySelected(day.date, day.records) },
                onLongClick = { onDayLongPressed(day.date, day.records) },
                modifier = Modifier.width(76.dp)
            )
        }
    }
}

@Composable
private fun MonthCalendarView(
    monthMeetings: List<MonthDayMeeting>,
    isFriendFiltered: Boolean,
    onMonthDaySelected: (MonthDayMeeting) -> Unit,
    onMonthDayLongPressed: (MonthDayMeeting) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("\u4e00", "\u4e8c", "\u4e09", "\u56db", "\u4e94", "\u516d", "\u65e5").forEach { weekday ->
                Text(
                    text = weekday,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = TouchColors.TextMuted,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        monthMeetings.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { day ->
                    MonthDayCell(
                        day = day,
                        isFriendFiltered = isFriendFiltered,
                        onClick = { onMonthDaySelected(day) },
                        onLongClick = { onMonthDayLongPressed(day) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    day: MonthDayMeeting,
    isFriendFiltered: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val count = day.records.size
    Surface(
        modifier = modifier
            .height(44.dp)
            .pointerInput(day.date, day.day) {
                detectTapGestures(
                    onTap = {
                        if (day.day != null) {
                            onClick()
                        }
                    },
                    onLongPress = {
                        if (day.day != null) {
                            onLongClick()
                        }
                    }
                )
            },
        color = when {
            day.day == null -> Color.Transparent
            count > 0 -> meetingHeatColor(if (isFriendFiltered) 1 else count)
            day.isToday -> TouchColors.CalendarToday
            else -> TouchColors.CalendarTile
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (day.day != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = day.day.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = TouchColors.TextStrong,
                        fontWeight = FontWeight.Bold
                    )
                    if (count > 0 && !isFriendFiltered) {
                        Text(
                            text = "${count}\u4eba",
                            style = MaterialTheme.typography.labelSmall,
                            color = TouchColors.HeatText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearCalendarView(
    yearMeetings: List<YearMonthMeeting>,
    isFriendFiltered: Boolean,
    onYearMonthSelected: (YearMonthMeeting) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        yearMeetings.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { month ->
                    YearMonthCell(
                        month = month,
                        isFriendFiltered = isFriendFiltered,
                        onClick = { onYearMonthSelected(month) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun YearMonthCell(
    month: YearMonthMeeting,
    isFriendFiltered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peopleCount = month.records.size
    Surface(
        modifier = modifier
            .height(58.dp)
            .cuteClickable(onClick = onClick),
        color = if (peopleCount > 0) meetingHeatColor(if (isFriendFiltered) 1 else peopleCount) else TouchColors.CalendarTile,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${month.month}\u6708",
                style = MaterialTheme.typography.labelLarge,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    peopleCount <= 0 -> "\u65e0"
                    isFriendFiltered -> "${peopleCount}\u6b21"
                    else -> "${peopleCount}\u4eba"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (peopleCount > 0) TouchColors.HeatText else TouchColors.TextMuted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CalendarDetailPanel(
    detail: CalendarDetail,
    friends: List<FriendUser>,
    authApiClient: AuthApiClient,
    modifier: Modifier = Modifier
) {
    val friendsById = remember(friends) { friends.associateBy { it.userId } }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TouchColors.DetailSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleSmall,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )
            if (detail.records.isEmpty()) {
                Text(
                    text = if (detail.mode == CalendarDetailMode.Week) {
                        "\u8fd9\u4e00\u5468\u6ca1\u6709\u89c1\u9762\u8bb0\u5f55"
                    } else {
                        "\u8fd9\u4e00\u5929\u6ca1\u6709\u89c1\u9762\u8bb0\u5f55"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TouchColors.TextMuted
                )
            } else {
                detail.records
                    .sortedWith(compareBy<MeetingRecord> { it.metDate }.thenBy { it.metTime })
                    .forEach { record ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = record.personName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TouchColors.TextStrong,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                MeetingFriendAvatar(
                                    record = record,
                                    friend = record.personUserId?.let { friendsById[it] },
                                    authApiClient = authApiClient,
                                    size = 24
                                )
                            }
                            Text(
                                text = "${formatDateLabel(record.metDate)} ${record.metTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TouchColors.TextMuted
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun DayDetailDialog(
    detail: CalendarDetail,
    friends: List<FriendUser>,
    authApiClient: AuthApiClient,
    accessToken: String,
    mainHandler: Handler,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val eventDate = detail.date ?: detail.records.firstOrNull()?.metDate ?: "2026-06-17"
    var events by remember(eventDate) { mutableStateOf<List<DayEvent>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var imageBase64List by remember { mutableStateOf<List<String>>(emptyList()) }
    var imageDeleteMode by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isEventsLoading by remember { mutableStateOf(true) }
    var canHideEventsLoading by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<DayEvent?>(null) }
    var eventActionTarget by remember { mutableStateOf<DayEvent?>(null) }
    var eventEditing by remember { mutableStateOf<DayEvent?>(null) }
    var eventPendingDelete by remember { mutableStateOf<DayEvent?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val metFriendIds = remember(detail.records) {
        detail.records.mapNotNull { it.personUserId }.toSet()
    }
    val metFriends = remember(friends, metFriendIds) {
        friends.filter { it.userId in metFriendIds }
    }
    val defaultParticipantIds = remember(metFriends) {
        metFriends.map { it.userId }.toSet()
    }
    var participantIds by remember(metFriends) { mutableStateOf(defaultParticipantIds) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val encoded = uris.take(6).mapNotNull { uri -> encodeImageBase64(context, uri) }
            val nextImages = (imageBase64List + encoded).take(6)
            imageBase64List = nextImages
            statusText = if (encoded.isEmpty()) {
                "\u56fe\u7247\u8bfb\u53d6\u5931\u8d25"
            } else {
                "\u5df2\u6dfb\u52a0 ${nextImages.size} \u5f20\u56fe\u7247"
            }
        }
    }

    LaunchedEffect(eventDate) {
        isEventsLoading = true
        canHideEventsLoading = false
        launch {
            delay(650)
            canHideEventsLoading = true
        }
        Thread {
            val loaded = runCatching { authApiClient.getDayEvents(accessToken, eventDate).map { it.toDayEvent() } }
            mainHandler.post {
                events = loaded.getOrDefault(emptyList())
                isEventsLoading = false
            }
        }.start()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .clearFocusOnBackgroundTap()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (detail.records.isEmpty()) {
                        FriendBubble("\u65e0\u89c1\u9762\u8bb0\u5f55", selected = false, onClick = {})
                    } else {
                        detail.records.forEach { record ->
                            FriendBubble("${record.personName} ${record.metTime}", selected = true, onClick = {})
                        }
                    }
                }
                AnimatedVisibility(
                    visible = isEventsLoading || !canHideEventsLoading,
                    enter = fadeIn(tween(120, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(120, easing = FastOutSlowInEasing)) { -it / 18 },
                    exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it / 12 }
                ) {
                    LoadingWaveHint(text = "\u6b63\u5728\u52a0\u8f7d\u5df2\u8bb0\u5f55\u7684\u4e8b...")
                }
                if (!isEventsLoading && canHideEventsLoading && events.isNotEmpty()) {
                    Text(
                        text = "\u5df2\u8bb0\u5f55\u7684\u4e8b",
                        style = MaterialTheme.typography.titleSmall,
                        color = TouchColors.TextStrong,
                        fontWeight = FontWeight.Bold
                    )
                    events.forEach { event ->
                        EventCard(
                            event = event,
                            onOpen = { selectedEvent = event },
                            onDeleteRequest = { eventActionTarget = event }
                        )
                    }
                }
                Text(
                    text = "\u65b0\u5efa\u4eca\u5929\u53d1\u751f\u7684\u4e8b",
                    style = MaterialTheme.typography.titleSmall,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u6807\u9898") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("\u6587\u5b57\u8bb0\u5f55") },
                    minLines = 3
                )
                if (metFriends.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        metFriends.forEach { friend ->
                            val selected = friend.userId in participantIds
                            FriendBubble(
                                label = friend.displayName,
                                selected = selected,
                                onClick = {
                                    participantIds = if (selected) {
                                        participantIds - friend.userId
                                    } else {
                                        participantIds + friend.userId
                                    }
                                }
                            )
                        }
                    }
                }
                if (imageBase64List.isNotEmpty()) {
                    EditableImageStrip(
                        images = imageBase64List,
                        deleteMode = imageDeleteMode,
                        onEnterDeleteMode = {
                            imageDeleteMode = true
                            statusText = "\u5411\u4e0a\u6ed1\u52a8\u56fe\u7247\u53ef\u5220\u9664"
                        },
                        onRemove = { index ->
                            imageBase64List = imageBase64List.filterIndexed { candidateIndex, _ -> candidateIndex != index }
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchOutlinedButtonColors()
                    ) {
                        Text(if (imageBase64List.isEmpty()) "\u4e0a\u4f20\u56fe\u7247" else "\u7ee7\u7eed\u6dfb\u52a0")
                    }
                    Button(
                        onClick = {
                            isSaving = true
                            Thread {
                                try {
                                    val created = authApiClient.createDayEvent(
                                        accessToken = accessToken,
                                        eventDate = eventDate,
                                        title = title.trim().ifBlank { "\u4eca\u5929\u53d1\u751f\u7684\u4e8b" },
                                        note = note.trim(),
                                        participantUserIds = participantIds.toList(),
                                        imageBase64List = imageBase64List
                                    ).toDayEvent()
                                    mainHandler.post {
                                        events = listOf(created) + events
                                        isSaving = false
                                        statusText = "\u4e8b\u4ef6\u5df2\u4fdd\u5b58"
                                        note = ""
                                        imageBase64List = emptyList()
                                        imageDeleteMode = false
                                    }
                                } catch (error: Exception) {
                                    mainHandler.post {
                                        isSaving = false
                                        statusText = error.message ?: "\u4fdd\u5b58\u5931\u8d25"
                                    }
                                }
                            }.start()
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchPrimaryButtonColors()
                    ) {
                        Text(if (isSaving) "\u4fdd\u5b58\u4e2d..." else "\u4fdd\u5b58")
                    }
                }
                statusText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("\u5931\u8d25")) TouchColors.Error else TouchColors.TextMuted
                    )
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = touchOutlinedButtonColors()
                ) {
                    Text("\u5173\u95ed")
                }
            }
        }
    }

    selectedEvent?.let { event ->
        EventDetailDialog(
            event = event,
            onDismiss = { selectedEvent = null }
        )
    }
    eventActionTarget?.let { event ->
        EventActionDialog(
            event = event,
            onDismiss = { eventActionTarget = null },
            onEdit = {
                eventActionTarget = null
                eventEditing = event
            },
            onDelete = {
                eventActionTarget = null
                eventPendingDelete = event
            }
        )
    }
    eventEditing?.let { event ->
        EventEditDialog(
            event = event,
            metFriends = metFriends,
            authApiClient = authApiClient,
            accessToken = accessToken,
            mainHandler = mainHandler,
            onDismiss = { eventEditing = null },
            onUpdated = { updated ->
                events = events.map { if (it.id == updated.id) updated else it }
                eventEditing = null
                statusText = "\u4e8b\u4ef6\u5df2\u66f4\u65b0"
            }
        )
    }
    eventPendingDelete?.let { event ->
        ConfirmDeleteEventDialog(
            event = event,
            isDeleting = isDeleting,
            onCancel = {
                if (!isDeleting) {
                    eventPendingDelete = null
                }
            },
            onConfirm = {
                isDeleting = true
                Thread {
                    try {
                        authApiClient.deleteDayEvent(accessToken, event.id)
                        mainHandler.post {
                            events = events.filterNot { it.id == event.id }
                            eventPendingDelete = null
                            selectedEvent = null
                            isDeleting = false
                            statusText = "\u8bb0\u5f55\u5df2\u5220\u9664"
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            isDeleting = false
                            statusText = error.message ?: "\u5220\u9664\u5931\u8d25"
                        }
                    }
                }.start()
            }
        )
    }
}

@Composable
private fun FriendBubble(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) TouchColors.SuccessSoft else TouchColors.CalendarTile,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.cuteClickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TouchColors.Success else TouchColors.TextMuted,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LoadingWaveHint(text: String) {
    val transition = rememberInfiniteTransition(label = "event-loading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "event-loading-phase"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TouchColors.DetailSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Canvas(modifier = Modifier.size(34.dp)) {
                repeat(3) { index ->
                    val local = (phase + index * 0.18f) % 1f
                    drawCircle(
                        color = TouchColors.Primary.copy(alpha = 0.25f + 0.55f * (1f - local)),
                        radius = size.minDimension * (0.10f + 0.14f * local),
                        center = center.copy(x = center.x + (index - 1) * size.minDimension * 0.22f)
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = TouchColors.TextMuted,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EventActionDialog(
    event: DayEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\u8981\u7f16\u8f91\u8fd9\u4ef6\u4e8b\uff0c\u8fd8\u662f\u5220\u9664\uff1f",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TouchColors.TextMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchDangerTextButtonColors()
                    ) {
                        Text("\u5220\u9664")
                    }
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchPrimaryButtonColors()
                    ) {
                        Text("\u7f16\u8f91")
                    }
                }
            }
        }
    }
}

@Composable
private fun EventEditDialog(
    event: DayEvent,
    metFriends: List<FriendUser>,
    authApiClient: AuthApiClient,
    accessToken: String,
    mainHandler: Handler,
    onDismiss: () -> Unit,
    onUpdated: (DayEvent) -> Unit
) {
    val context = LocalContext.current
    var title by remember(event.id) { mutableStateOf(event.title) }
    var note by remember(event.id) { mutableStateOf(event.note) }
    var images by remember(event.id) { mutableStateOf(event.imageBase64List) }
    var imageDeleteMode by remember { mutableStateOf(false) }
    var participantIds by remember(event.id) { mutableStateOf(event.participants.map { it.userId }.toSet()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val encoded = uris.take(6).mapNotNull { uri -> encodeImageBase64(context, uri) }
            images = (images + encoded).take(6)
            statusText = "\u5df2\u9009\u62e9 ${images.size} \u5f20\u56fe\u7247"
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .clearFocusOnBackgroundTap()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("\u7f16\u8f91\u8bb0\u5f55", style = MaterialTheme.typography.titleLarge, color = TouchColors.TextStrong, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("\u6807\u9898") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("\u6587\u5b57\u8bb0\u5f55") }, minLines = 3)
                if (metFriends.isNotEmpty()) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        metFriends.forEach { friend ->
                            val selected = friend.userId in participantIds
                            FriendBubble(friend.displayName, selected) {
                                participantIds = if (selected) participantIds - friend.userId else participantIds + friend.userId
                            }
                        }
                    }
                }
                EditableImageStrip(
                    images = images,
                    deleteMode = imageDeleteMode,
                    onEnterDeleteMode = {
                        imageDeleteMode = true
                        statusText = "\u5411\u4e0a\u6ed1\u52a8\u56fe\u7247\u53ef\u5220\u9664"
                    },
                    onRemove = { index ->
                        images = images.filterIndexed { candidateIndex, _ -> candidateIndex != index }
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchOutlinedButtonColors()
                    ) {
                        Text("\u6dfb\u52a0\u56fe\u7247")
                    }
                    Button(
                        onClick = {
                            isSaving = true
                            Thread {
                                try {
                                    val updated = authApiClient.updateDayEvent(
                                        accessToken = accessToken,
                                        eventId = event.id,
                                        title = title.trim().ifBlank { "\u4eca\u5929\u53d1\u751f\u7684\u4e8b" },
                                        note = note.trim(),
                                        participantUserIds = participantIds.toList(),
                                        imageBase64List = images
                                    ).toDayEvent()
                                    mainHandler.post {
                                        isSaving = false
                                        statusText = "\u4e8b\u4ef6\u5df2\u66f4\u65b0"
                                        mainHandler.postDelayed({ onUpdated(updated) }, 420)
                                    }
                                } catch (error: Exception) {
                                    mainHandler.post {
                                        isSaving = false
                                        statusText = error.message ?: "\u4fdd\u5b58\u5931\u8d25"
                                    }
                                }
                            }.start()
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchPrimaryButtonColors()
                    ) {
                        Text(if (isSaving) "\u4fdd\u5b58\u4e2d..." else "\u4fdd\u5b58")
                    }
                }
                statusText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.contains("\u5931\u8d25")) TouchColors.Error else TouchColors.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun EditableImageStrip(
    images: List<String>,
    deleteMode: Boolean,
    onEnterDeleteMode: () -> Unit,
    onRemove: (Int) -> Unit
) {
    if (images.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (deleteMode) 10.dp else (-18).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        images.forEachIndexed { index, raw ->
            val bitmap = remember(raw) { decodeBase64Bitmap(raw) }
            bitmap?.let {
                var dragY by remember(raw) { mutableStateOf(0f) }
                Surface(
                    modifier = Modifier
                        .width(78.dp)
                        .height(98.dp)
                        .graphicsLayer {
                            translationY = dragY
                            rotationZ = if (deleteMode) 0f else (index % 3 - 1) * 2.5f
                            shadowElevation = if (deleteMode) 12f else 5f
                            scaleX = if (deleteMode) 1.06f else 1f
                            scaleY = if (deleteMode) 1.06f else 1f
                        }
                        .pointerInput(raw, deleteMode) {
                            detectTapGestures(onLongPress = { onEnterDeleteMode() })
                        }
                        .pointerInput(raw, deleteMode) {
                            if (deleteMode) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount -> dragY += dragAmount },
                                    onDragEnd = {
                                        if (dragY < -42f) onRemove(index)
                                        dragY = 0f
                                    },
                                    onDragCancel = { dragY = 0f }
                                )
                            }
                        },
                    color = if (deleteMode) TouchColors.WarningSoft else Color.White,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = if (deleteMode) 8.dp else 3.dp
                ) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "\u53ef\u7f16\u8f91\u56fe\u7247",
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: DayEvent,
    onOpen: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(event.id) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { onDeleteRequest() }
                )
            },
        color = TouchColors.DetailSurface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )
            if (event.note.isNotBlank()) {
                Text(
                    text = event.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TouchColors.TextMuted
                )
            }
            if (event.participants.isNotEmpty()) {
                Text(
                    text = event.participants.joinToString(" / ") { it.displayName },
                    style = MaterialTheme.typography.labelMedium,
                    color = TouchColors.Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (event.imageBase64List.isNotEmpty()) {
                EventImageStrip(
                    images = event.imageBase64List,
                    onImageClick = { onOpen() },
                    height = 118
                )
            }
        }
    }
}

@Composable
private fun EventDetailDialog(event: DayEvent, onDismiss: () -> Unit) {
    var galleryStartIndex by remember(event.id) { mutableStateOf<Int?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.eventDate.replace("-", "."),
                    style = MaterialTheme.typography.labelMedium,
                    color = TouchColors.TextMuted,
                    fontWeight = FontWeight.Medium
                )
                if (event.participants.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        event.participants.forEach { participant ->
                            FriendBubble(participant.displayName, selected = true, onClick = {})
                        }
                    }
                }
                if (event.note.isNotBlank()) {
                    Surface(
                        color = TouchColors.DetailSurface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = event.note,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TouchColors.TextStrong
                        )
                    }
                }
                if (event.imageBase64List.isNotEmpty()) {
                    EventImageStrip(
                        images = event.imageBase64List,
                        onImageClick = { index -> galleryStartIndex = index },
                        height = 184
                    )
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = touchOutlinedButtonColors()
                ) {
                    Text("\u5173\u95ed")
                }
            }
        }
    }
    galleryStartIndex?.let { startIndex ->
        ImageGalleryDialog(
            images = event.imageBase64List,
            startIndex = startIndex,
            onDismiss = { galleryStartIndex = null }
        )
    }
}

@Composable
private fun EventImageStrip(
    images: List<String>,
    onImageClick: (Int) -> Unit,
    height: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy((-18).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        images.forEachIndexed { index, raw ->
            val bitmap = remember(raw) { decodeBase64Bitmap(raw) }
            bitmap?.let {
                val rotation = when (index % 4) {
                    0 -> -3f
                    1 -> 2f
                    2 -> -1.5f
                    else -> 3f
                }
                Surface(
                    modifier = Modifier
                        .width((height * 0.78f).dp)
                        .height(height.dp)
                        .graphicsLayer {
                            rotationZ = rotation
                            shadowElevation = 7f
                        }
                        .pointerInput(raw) {
                            detectTapGestures(onTap = { onImageClick(index) })
                        },
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 4.dp
                ) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "\u4e8b\u4ef6\u56fe\u7247 ${index + 1}",
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryDialog(
    images: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember(images, startIndex) { mutableIntStateOf(startIndex.coerceIn(images.indices)) }
    var zoom by remember(selectedIndex) { mutableStateOf(1f) }
    var pan by remember(selectedIndex) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        pan = if (zoom > 1f) pan + panChange else Offset.Zero
    }
    val selectedBitmap = remember(images, selectedIndex) {
        images.getOrNull(selectedIndex)?.let { decodeBase64Bitmap(it) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u9644\u4ef6\u7167\u7247 ${selectedIndex + 1}/${images.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TouchColors.TextStrong,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss, colors = touchTextButtonColors()) {
                        Text("\u5173\u95ed")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(390.dp)
                        .background(TouchColors.DetailSurface, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    selectedBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "\u653e\u5927\u56fe\u7247",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = pan.x
                                    translationY = pan.y
                                }
                                .transformable(transformState),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy((-22).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    images.forEachIndexed { index, raw ->
                        val bitmap = remember(raw) { decodeBase64Bitmap(raw) }
                        bitmap?.let {
                            val selected = index == selectedIndex
                            Surface(
                                modifier = Modifier
                                    .width(82.dp)
                                    .height(104.dp)
                                    .graphicsLayer {
                                        rotationZ = (index - selectedIndex).coerceIn(-2, 2) * 2.2f
                                        scaleX = if (selected) 1.05f else 0.92f
                                        scaleY = if (selected) 1.05f else 0.92f
                                        alpha = if (selected) 1f else 0.76f
                                    }
                                    .pointerInput(raw) {
                                        detectTapGestures(onTap = { selectedIndex = index })
                                    },
                                color = if (selected) TouchColors.CalendarToday else Color.White,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = if (selected) 7.dp else 2.dp
                            ) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "\u56fe\u7247\u7f29\u7565\u56fe ${index + 1}",
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteEventDialog(
    event: DayEvent,
    isDeleting: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "\u786e\u8ba4\u5220\u9664\uff1f",
                    style = MaterialTheme.typography.titleLarge,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "\u5220\u9664\u540e\u8fd9\u6761\u201c${event.title}\u201d\u8bb0\u5f55\u5c06\u4ece\u4f60\u7684\u5f53\u5929\u4e8b\u4ef6\u4e2d\u79fb\u9664\u3002",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TouchColors.TextMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isDeleting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchOutlinedButtonColors()
                    ) {
                        Text("\u53d6\u6d88")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !isDeleting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchDangerButtonColors()
                    ) {
                        Text(if (isDeleting) "\u5220\u9664\u4e2d..." else "\u5220\u9664")
                    }
                }
            }
        }
    }
}

private fun buildWeekMeetings(
    records: List<MeetingRecord>,
    year: Int,
    month: Int,
    startDay: Int
): List<CalendarMeeting> {
    val recordsByDate = records.groupBy { it.metDate }
    return (0..6).map { offset ->
        val focus = shiftDateFocus(year, month, startDay, offset)
        val date = dateString(focus.year, focus.month, focus.startDay)
        CalendarMeeting(
            day = focus.startDay,
            date = date,
            weekday = weekdayLabel(focus.year, focus.month, focus.startDay),
            records = recordsByDate[date].orEmpty(),
            isToday = date == "2026-06-17"
        )
    }
}

private fun buildMonthMeetings(
    records: List<MeetingRecord>,
    year: Int,
    month: Int
): List<MonthDayMeeting> {
    val recordsByDate = records.groupBy { it.metDate }
    val leadingEmptyDays = firstWeekdayOffset(year, month)
    val days = List(leadingEmptyDays) { MonthDayMeeting(day = null) } +
        (1..daysInMonth(year, month)).map { day ->
            val date = dateString(year, month, day)
            MonthDayMeeting(
                day = day,
                date = date,
                records = recordsByDate[date].orEmpty(),
                isToday = date == "2026-06-17"
            )
        }
    val trailingEmptyDays = (7 - days.size % 7).takeIf { it < 7 } ?: 0
    return days + List(trailingEmptyDays) { MonthDayMeeting(day = null) }
}

private fun buildYearMeetings(records: List<MeetingRecord>, year: Int): List<YearMonthMeeting> {
    val recordsByMonth = records
        .filter { it.metDate.take(4).toIntOrNull() == year }
        .groupBy { it.metDate.substring(5, 7).toIntOrNull() ?: 0 }
    return (1..12).map { month ->
        YearMonthMeeting(
            month = month,
            records = recordsByMonth[month].orEmpty()
        )
    }
}

private fun recordsForWeek(records: List<MeetingRecord>, year: Int, month: Int, startDay: Int): List<MeetingRecord> {
    val validDates = (0..6)
        .map { offset ->
            val focus = shiftDateFocus(year, month, startDay, offset)
            dateString(focus.year, focus.month, focus.startDay)
        }
        .toSet()
    return records.filter { it.metDate in validDates }
}

private fun weekStartForDate(year: Int, month: Int, day: Int): CalendarFocus {
    val weekdayOffset = (firstWeekdayOffset(year, month) + day - 1) % 7
    return shiftDateFocus(year, month, day, -weekdayOffset)
}

private fun shiftWeekFocus(year: Int, month: Int, startDay: Int, deltaWeeks: Int): CalendarFocus {
    return shiftDateFocus(year, month, startDay, deltaWeeks * 7)
}

private fun shiftDateFocus(year: Int, month: Int, day: Int, deltaDays: Int): CalendarFocus {
    val currentOrdinal = dayOfYear(year, month, day)
    val nextOrdinal = currentOrdinal + deltaDays
    val daysInCurrentYear = daysInYear(year)
    return when {
        nextOrdinal < 1 -> monthDayFromDayOfYear(year - 1, (daysInYear(year - 1) + nextOrdinal).coerceAtLeast(1))
        nextOrdinal > daysInCurrentYear -> monthDayFromDayOfYear(year + 1, nextOrdinal - daysInCurrentYear)
        else -> monthDayFromDayOfYear(year, nextOrdinal)
    }
}

private fun dayOfYear(year: Int, month: Int, day: Int): Int {
    val daysBefore = (1 until month).sumOf { daysInMonth(year, it) }
    return daysBefore + day.coerceIn(1, daysInMonth(year, month))
}

private fun monthDayFromDayOfYear(year: Int, dayOfYear: Int): CalendarFocus {
    var remaining = dayOfYear.coerceIn(1, daysInYear(year))
    for (month in 1..12) {
        val daysInMonth = daysInMonth(year, month)
        if (remaining <= daysInMonth) {
            return CalendarFocus(year = year, month = month, startDay = remaining)
        }
        remaining -= daysInMonth
    }
    return CalendarFocus(year = year, month = 12, startDay = 31)
}

private fun daysInYear(year: Int): Int {
    return if (isLeapYear(year)) 366 else 365
}

private fun daysInMonth(year: Int, month: Int): Int {
    return when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

private fun firstWeekdayOffset(year: Int, month: Int): Int {
    var daysBefore = 0
    if (year >= 2026) {
        for (candidate in 2026 until year) {
            daysBefore += daysInYear(candidate)
        }
    } else {
        for (candidate in year until 2026) {
            daysBefore -= daysInYear(candidate)
        }
    }
    daysBefore += (1 until month).sumOf { daysInMonth(year, it) }
    return Math.floorMod(3 + daysBefore, 7)
}

private fun weekdayLabel(year: Int, month: Int, day: Int): String {
    val offset = (firstWeekdayOffset(year, month) + day - 1) % 7
    return listOf("\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d", "\u5468\u65e5")[offset]
}

private fun dateString(year: Int, month: Int, day: Int): String {
    return "${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun weekRangeLabel(calendarMeetings: List<CalendarMeeting>): String {
    val first = calendarMeetings.firstOrNull() ?: return ""
    val last = calendarMeetings.lastOrNull() ?: return ""
    return "${first.date.substring(5).replace("-", ".")}-${last.date.substring(5).replace("-", ".")}"
}

private fun formatDateLabel(date: String): String {
    return when (date) {
        "2026-06-17" -> "\u4eca\u5929"
        "2026-06-16" -> "\u6628\u5929"
        else -> date.substring(5).replace("-", ".")
    }
}

private fun countMeetingsWithFriendWithin(records: List<MeetingRecord>, friendUserId: String, days: Int): Int {
    val todayOrdinal = absoluteDayOrdinal("2026-06-17") ?: return 0
    return records.count { record ->
        val ordinal = absoluteDayOrdinal(record.metDate)
        record.personUserId == friendUserId &&
            ordinal != null &&
            ordinal in (todayOrdinal - days + 1)..todayOrdinal
    }
}

private fun friendMeetRatioScore(records: List<MeetingRecord>, friendUserId: String): Double {
    val todayOrdinal = absoluteDayOrdinal("2026-06-18") ?: return 0.0
    val recent = records.count { record ->
        val ordinal = absoluteDayOrdinal(record.metDate)
        record.personUserId == friendUserId && ordinal != null && ordinal in (todayOrdinal - 9)..todayOrdinal
    }
    val previous = records.count { record ->
        val ordinal = absoluteDayOrdinal(record.metDate)
        record.personUserId == friendUserId && ordinal != null && ordinal in (todayOrdinal - 19)..(todayOrdinal - 10)
    }
    return if (previous == 0) {
        if (recent == 0) 0.0 else recent.toDouble() + 1000.0
    } else {
        recent.toDouble() / previous.toDouble()
    }
}

private fun friendMeetRatioLabel(records: List<MeetingRecord>, friendUserId: String): String {
    val todayOrdinal = absoluteDayOrdinal("2026-06-18") ?: return "\u8fd110\u5929\u89c1\u9762 0 \u6b21"
    val recent = records.count { record ->
        val ordinal = absoluteDayOrdinal(record.metDate)
        record.personUserId == friendUserId && ordinal != null && ordinal in (todayOrdinal - 9)..todayOrdinal
    }
    return "\u8fd110\u5929\u89c1\u9762 ${recent} \u6b21"
}

private fun absoluteDayOrdinal(date: String): Int? {
    val year = date.take(4).toIntOrNull() ?: return null
    val month = date.substring(5, 7).toIntOrNull() ?: return null
    val day = date.substring(8, 10).toIntOrNull() ?: return null
    var days = 0
    if (year >= 2026) {
        for (candidate in 2026 until year) {
            days += daysInYear(candidate)
        }
    } else {
        for (candidate in year until 2026) {
            days -= daysInYear(candidate)
        }
    }
    return days + dayOfYear(year, month, day)
}

private fun MeetingRecordDto.toMeetingRecord(): MeetingRecord {
    return MeetingRecord(
        id = id,
        personUserId = personUserId,
        personName = personName,
        metDate = metDate,
        metTime = metTime,
        status = if (status == "confirmed") MeetingStatus.Confirmed else MeetingStatus.Pending
    )
}

private fun FriendUserDto.toFriendUser(): FriendUser {
    return FriendUser(
        userId = userId,
        displayName = displayName,
        email = email,
        avatarUrl = avatarUrl,
        bio = bio,
        birthday = birthday,
        gender = gender,
        cardBackgroundUrl = cardBackgroundUrl,
        cardBackgroundKey = cardBackgroundKey,
        lastSeenAt = lastSeenAt
    )
}

private fun FriendshipDto.toFriendship(): Friendship {
    return Friendship(
        id = id,
        status = status,
        direction = direction,
        friend = friend?.toFriendUser(),
        remark = remark,
        blockedByMe = blockedByMe,
        blockedMe = blockedMe
    )
}

private fun DayEventDto.toDayEvent(): DayEvent {
    val images = imageBase64List.ifEmpty { imageBase64?.let { listOf(it) }.orEmpty() }
    return DayEvent(
        id = id,
        eventDate = eventDate,
        title = title,
        note = note,
        imageBase64List = images,
        participants = participants.map { it.toFriendUser() }
    )
}

private fun meetingHeatColor(count: Int): Color {
    return when {
        count <= 1 -> TouchColors.HeatLow
        count == 2 -> TouchColors.HeatMedium
        count == 3 -> TouchColors.HeatHigh
        else -> TouchColors.HeatStrong
    }
}

@Composable
private fun CalendarDayCard(
    meeting: CalendarMeeting,
    friends: List<FriendUser>,
    authApiClient: AuthApiClient,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val friendsById = remember(friends) { friends.associateBy { it.userId } }
    val avatarRecords = remember(meeting.records, friends) {
        meeting.records.distinctBy { it.personUserId ?: it.personName }.take(3)
    }
    Surface(
        modifier = modifier
            .height(122.dp)
            .pointerInput(meeting.date) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        color = when {
            meeting.isToday -> TouchColors.CalendarToday
            meeting.records.isNotEmpty() -> TouchColors.SuccessSoft
            else -> TouchColors.CalendarTile
        },
        shape = RoundedCornerShape(8.dp),
        shadowElevation = if (meeting.records.isNotEmpty() || meeting.isToday) 3.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = meeting.weekday,
                style = MaterialTheme.typography.labelMedium,
                color = if (meeting.isToday) TouchColors.Primary else TouchColors.TextMuted,
                fontWeight = if (meeting.isToday) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Text(
                text = meeting.day.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (meeting.records.isEmpty()) {
                    Text(
                        text = "\u65e0",
                        style = MaterialTheme.typography.labelMedium,
                        color = TouchColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-5).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        avatarRecords.forEach { record ->
                            MeetingFriendAvatar(
                                record = record,
                                friend = record.personUserId?.let { friendsById[it] },
                                authApiClient = authApiClient,
                                size = 24
                            )
                        }
                    }
                    if (meeting.records.isNotEmpty()) {
                        Text(
                            text = "${meeting.records.size}\u6b21",
                            style = MaterialTheme.typography.labelSmall,
                            color = TouchColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
                if (avatarRecords.size < meeting.records.distinctBy { it.personUserId ?: it.personName }.size) {
                    Text(
                        text = "+${meeting.records.distinctBy { it.personUserId ?: it.personName }.size - avatarRecords.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TouchColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingFriendAvatar(
    record: MeetingRecord,
    friend: FriendUser?,
    authApiClient: AuthApiClient,
    size: Int
) {
    val displayName = friend?.displayName ?: record.personName
    var avatarBitmap by remember(friend?.userId, friend?.avatarUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(friend?.userId, friend?.avatarUrl) {
        val avatarUrl = friend?.avatarUrl
        if (avatarUrl == null) {
            avatarBitmap = null
        } else {
            Thread {
                val loaded = runCatching { authApiClient.loadAvatarBitmap(avatarUrl) }.getOrNull()
                Handler(Looper.getMainLooper()).post {
                    avatarBitmap = loaded
                }
            }.start()
        }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(TouchColors.Surface)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(TouchColors.Avatar),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.take(1).ifBlank { "T" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FloatingRealtimeNoticeOverlay(
    notice: FloatingRealtimeNotice?,
    onDismiss: () -> Unit
) {
    var displayedNotice by remember { mutableStateOf<FloatingRealtimeNotice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            displayedNotice = notice
        } else {
            delay(180)
            displayedNotice = null
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 34.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn(tween(130, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(170, easing = FastOutSlowInEasing)) { -it / 2 } +
                scaleIn(tween(170, easing = FastOutSlowInEasing), initialScale = 0.96f),
            exit = fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(140, easing = FastOutSlowInEasing)) { -it / 3 }
        ) {
            val current = displayedNotice ?: return@AnimatedVisibility
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss),
                colors = CardDefaults.cardColors(
                    containerColor = TouchColors.Surface.copy(alpha = 0.96f)
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RealtimeNoticePulse(
                        kind = current.kind,
                        modifier = Modifier.size(48.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TouchColors.TextStrong,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TouchColors.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealtimeNoticePulse(
    kind: RealtimeNoticeKind,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "realtime-notice-pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "realtime-notice-progress"
    )
    val color = when (kind) {
        RealtimeNoticeKind.Meeting -> TouchColors.Primary
        RealtimeNoticeKind.Friend -> TouchColors.Accent
        RealtimeNoticeKind.Neutral -> TouchColors.Secondary
    }
    Canvas(modifier = modifier) {
        repeat(3) { index ->
            val phase = (progress + index * 0.33f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - phase) * 0.18f),
                radius = size.minDimension * (0.2f + phase * 0.34f),
                center = center,
                style = Stroke(width = size.minDimension * 0.035f)
            )
        }
        drawCircle(
            color = color.copy(alpha = 0.16f),
            radius = size.minDimension * 0.32f,
            center = center
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.16f,
            center = center
        )
    }
}

@Composable
private fun TapProgressDialog(
    mode: TapDialogMode,
    statusText: String,
    statusIsError: Boolean,
    isPreparing: Boolean,
    isScanning: Boolean,
    pendingFriendPrompt: PendingFriendPrompt?,
    onSendFriendRequest: (FriendUser) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(INLINE_ENTER_MS, easing = FastOutSlowInEasing)) +
                scaleIn(tween(INLINE_ENTER_MS, easing = FastOutSlowInEasing), initialScale = 0.98f)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = when (mode) {
                            TapDialogMode.ShowMine -> "\u663e\u793a\u6211\u7684\u78b0\u4e00\u78b0"
                            TapDialogMode.ScanOther -> "\u78b0\u4e00\u78b0\u522b\u4eba"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = TouchColors.TextStrong,
                        fontWeight = FontWeight.Bold
                    )
                    TapWaveAnimation(
                        mode = mode,
                        statusIsError = statusIsError,
                        modifier = Modifier
                            .fillMaxWidth(0.74f)
                            .aspectRatio(1f)
                    )
                    Text(
                        text = when {
                            statusIsError -> "\u9700\u8981\u5904\u7406"
                            statusText.startsWith("\u5df2\u786e\u8ba4") -> "\u5df2\u786e\u8ba4"
                            statusText.contains("\u670d\u52a1\u5668\u786e\u8ba4") -> "\u6b63\u5728\u786e\u8ba4"
                            isPreparing -> "\u6b63\u5728\u51c6\u5907"
                            mode == TapDialogMode.ScanOther && isScanning -> "\u6b63\u5728\u626b\u63cf"
                            mode == TapDialogMode.ShowMine -> "\u7b49\u5f85\u5bf9\u65b9\u9760\u8fd1"
                            else -> "\u7b49\u5f85\u8bfb\u53d6"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (statusIsError) TouchColors.Error else TouchColors.Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TouchColors.TextMuted
                    )
                    if (pendingFriendPrompt != null) {
                        Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = TouchColors.WarningSoft,
                        shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "\u662f\u5426\u6dfb\u52a0 ${pendingFriendPrompt.user.displayName} \u4e3a\u597d\u53cb\uff1f",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TouchColors.TextStrong,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Button(
                                    onClick = { onSendFriendRequest(pendingFriendPrompt.user) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = touchPrimaryButtonColors()
                                ) {
                                    Text("\u53d1\u9001\u597d\u53cb\u7533\u8bf7")
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = touchOutlinedButtonColors()
                    ) {
                        Text(
                            text = when (mode) {
                                TapDialogMode.ShowMine -> "\u6536\u8d77\u5e76\u505c\u6b62\u663e\u793a"
                                TapDialogMode.ScanOther -> "\u505c\u6b62\u626b\u63cf"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TapWaveAnimation(
    mode: TapDialogMode,
    statusIsError: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "tap-wave")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Restart
        ),
        label = "tap-wave-progress"
    )
    val waveColor = when {
        statusIsError -> TouchColors.Error
        mode == TapDialogMode.ShowMine -> TouchColors.Primary
        else -> TouchColors.Avatar
    }
    val accentColor = if (mode == TapDialogMode.ShowMine) TouchColors.Avatar else TouchColors.Primary

    Canvas(modifier = modifier) {
        val minRadius = size.minDimension * 0.13f
        val maxRadius = size.minDimension * 0.46f
        drawCircle(
            color = waveColor.copy(alpha = 0.08f),
            radius = maxRadius,
            center = center
        )
        repeat(4) { index ->
            val phase = (progress + index * 0.25f) % 1f
            val radius = if (mode == TapDialogMode.ShowMine) {
                minRadius + (maxRadius - minRadius) * phase
            } else {
                maxRadius - (maxRadius - minRadius) * phase
            }
            val alpha = if (mode == TapDialogMode.ShowMine) {
                0.42f * (1f - phase)
            } else {
                0.12f + 0.30f * phase
            }
            drawCircle(
                color = waveColor.copy(alpha = alpha.coerceIn(0.08f, 0.42f)),
                radius = radius,
                center = center,
                style = Stroke(width = size.minDimension * 0.018f)
            )
        }
        drawCircle(
            color = accentColor.copy(alpha = 0.16f),
            radius = size.minDimension * 0.18f,
            center = center
        )
        drawCircle(
            color = waveColor,
            radius = size.minDimension * 0.075f,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.90f),
            radius = size.minDimension * 0.030f,
            center = center
        )
    }
}

@Composable
private fun PrimaryActions(
    statusText: String,
    statusIsError: Boolean,
    isReady: Boolean,
    isPreparing: Boolean,
    isScanning: Boolean,
    onShowMine: () -> Unit,
    onScanOther: () -> Unit
) {
    val showMineInteraction = remember { MutableInteractionSource() }
    val scanOtherInteraction = remember { MutableInteractionSource() }
    val showMineScale = rememberCutePressScale(showMineInteraction)
    val scanOtherScale = rememberCutePressScale(scanOtherInteraction)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (statusIsError) TouchColors.ErrorSoft else TouchColors.SuccessSoft,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 2.dp
        ) {
            Text(
                text = statusText,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (statusIsError) TouchColors.Error else TouchColors.Success,
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onShowMine,
            enabled = isReady && !isPreparing,
            interactionSource = showMineInteraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .graphicsLayer {
                    scaleX = showMineScale
                    scaleY = showMineScale
            },
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp),
            colors = touchPrimaryButtonColors()
        ) {
            Text(
                text = if (isPreparing) "\u6b63\u5728\u51c6\u5907..." else "\u663e\u793a\u6211\u7684\u78b0\u4e00\u78b0",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onScanOther,
            enabled = isReady && !isPreparing,
            interactionSource = scanOtherInteraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .graphicsLayer {
                    scaleX = scanOtherScale
                    scaleY = scanOtherScale
            },
            shape = RoundedCornerShape(8.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp),
            colors = touchSecondaryButtonColors()
        ) {
            Text(
                text = if (isScanning) "\u505c\u6b62\u626b\u63cf" else "\u78b0\u4e00\u78b0\u522b\u4eba",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SyncSummary(meetings: List<MeetingRecord>) {
    val confirmed = meetings.count { it.status == MeetingStatus.Confirmed }
    val pending = meetings.count { it.status == MeetingStatus.Pending }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryMetric(
            label = "\u5df2\u786e\u8ba4",
            value = confirmed.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryMetric(
            label = "\u5f85\u786e\u8ba4",
            value = pending.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryMetric(
            label = "\u5931\u8d25",
            value = "0",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = TouchColors.Surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TouchColors.TextMuted
            )
        }
    }
}

@Composable
private fun MeetingHistory(meetings: List<MeetingPreview>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "\u6700\u8fd1\u89c1\u9762",
                style = MaterialTheme.typography.titleLarge,
                color = TouchColors.TextStrong,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "\u4ee5\u670d\u52a1\u7aef\u786e\u8ba4\u4e3a\u51c6",
                style = MaterialTheme.typography.labelMedium,
                color = TouchColors.TextMuted
            )
        }
        meetings.forEach { meeting ->
            MeetingRow(meeting = meeting)
        }
    }
}

@Composable
private fun MeetingRow(meeting: MeetingPreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TouchColors.Surface),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(name = meeting.name)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = meeting.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TouchColors.TextStrong,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = meeting.time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TouchColors.TextMuted
                )
            }
            val isConfirmed = meeting.status == MeetingStatus.Confirmed
            StatusPill(
                text = if (isConfirmed) "\u5df2\u786e\u8ba4" else "\u5f85\u786e\u8ba4",
                background = if (isConfirmed) TouchColors.SuccessSoft else TouchColors.WarningSoft,
                foreground = if (isConfirmed) TouchColors.Success else TouchColors.Warning
            )
        }
    }
}

@Composable
private fun InitialsAvatar(name: String) {
    val initials = name
        .take(2)
        .ifBlank { "TC" }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(TouchColors.Avatar),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FriendAvatar(
    user: FriendUser,
    authApiClient: AuthApiClient,
    modifier: Modifier = Modifier
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var avatarBitmap by remember(user.userId, user.avatarUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(user.userId, user.avatarUrl) {
        val avatarUrl = user.avatarUrl
        if (avatarUrl == null) {
            avatarBitmap = null
        } else {
            Thread {
                val loaded = runCatching { authApiClient.loadAvatarBitmap(avatarUrl) }.getOrNull()
                mainHandler.post {
                    avatarBitmap = loaded
                }
            }.start()
        }
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(TouchColors.Avatar),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap!!.asImageBitmap(),
                contentDescription = "\u597d\u53cb\u5934\u50cf",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = user.displayName.take(2).ifBlank { "TC" },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AccountAvatar(
    name: String,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(TouchColors.Avatar),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "\u5934\u50cf",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.take(2).ifBlank { "TC" },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    background: Color,
    foreground: Color
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private data class TouchPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val primaryDark: Color,
    val secondary: Color,
    val accent: Color,
    val avatar: Color,
    val success: Color,
    val successSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val calendarTile: Color,
    val calendarToday: Color,
    val detailSurface: Color,
    val heatLow: Color,
    val heatMedium: Color,
    val heatHigh: Color,
    val heatStrong: Color,
    val heatText: Color,
    val textStrong: Color,
    val textMuted: Color
)

private object TouchColors {
    var style: ThemeStyle by mutableStateOf(ThemeStyle.Mizuki)

    private val muelsyse = TouchPalette(
        background = Color(0xFFF2FAF8),
        surface = Color(0xFFFFFFFF),
        primary = Color(0xFF43BBA8),
        primaryDark = Color(0xFF247C72),
        secondary = Color(0xFF4DA9C7),
        accent = Color(0xFFB89245),
        avatar = Color(0xFF74CFC2),
        success = Color(0xFF2E9D8F),
        successSoft = Color(0xFFDDF7F1),
        error = Color(0xFFB85F5B),
        errorSoft = Color(0xFFF8E8E4),
        warning = Color(0xFFB98A24),
        warningSoft = Color(0xFFFFF3D4),
        calendarTile = Color(0xFFEAF5F3),
        calendarToday = Color(0xFFFFF0BC),
        detailSurface = Color(0xFFF3FBF8),
        heatLow = Color(0xFFF8EED7),
        heatMedium = Color(0xFFEEDDAE),
        heatHigh = Color(0xFFE4C682),
        heatStrong = Color(0xFFD4AA5F),
        heatText = Color(0xFF765D2B),
        textStrong = Color(0xFF172A31),
        textMuted = Color(0xFF6C7E82)
    )

    private val shu = TouchPalette(
        background = Color(0xFFFBF8EA),
        surface = Color(0xFFFFFFFF),
        primary = Color(0xFF7F9F56),
        primaryDark = Color(0xFF536C37),
        secondary = Color(0xFFD1A84E),
        accent = Color(0xFFE7C96B),
        avatar = Color(0xFFAFC57A),
        success = Color(0xFF6F914E),
        successSoft = Color(0xFFF0F5D8),
        error = Color(0xFFAA6A54),
        errorSoft = Color(0xFFF7E9DF),
        warning = Color(0xFFAE7E24),
        warningSoft = Color(0xFFFFF1C7),
        calendarTile = Color(0xFFF5F0D9),
        calendarToday = Color(0xFFFFE9A8),
        detailSurface = Color(0xFFFEFAEC),
        heatLow = Color(0xFFF4EBCB),
        heatMedium = Color(0xFFE9D59A),
        heatHigh = Color(0xFFDAB85E),
        heatStrong = Color(0xFFC69632),
        heatText = Color(0xFF725821),
        textStrong = Color(0xFF2A2A1D),
        textMuted = Color(0xFF77745F)
    )

    private val mizuki = TouchPalette(
        background = Color(0xFFF3F7FF),
        surface = Color(0xFFFFFFFF),
        primary = Color(0xFF5E87D6),
        primaryDark = Color(0xFF385A99),
        secondary = Color(0xFF7B68B8),
        accent = Color(0xFF68D6D0),
        avatar = Color(0xFF86CFE8),
        success = Color(0xFF4BA7A3),
        successSoft = Color(0xFFE2F7F8),
        error = Color(0xFF9C6681),
        errorSoft = Color(0xFFF2E7EF),
        warning = Color(0xFF9F7B35),
        warningSoft = Color(0xFFF5EFD8),
        calendarTile = Color(0xFFEAF0FF),
        calendarToday = Color(0xFFDDF7F5),
        detailSurface = Color(0xFFF4F6FF),
        heatLow = Color(0xFFEAE4F7),
        heatMedium = Color(0xFFD8CBEF),
        heatHigh = Color(0xFFC4AFE4),
        heatStrong = Color(0xFFA889D2),
        heatText = Color(0xFF5B4778),
        textStrong = Color(0xFF17243A),
        textMuted = Color(0xFF67758A)
    )

    private val palette: TouchPalette
        get() = when (style) {
            ThemeStyle.Muelsyse -> muelsyse
            ThemeStyle.Shu -> shu
            ThemeStyle.Mizuki -> mizuki
        }

    val Background get() = palette.background
    val Surface get() = palette.surface
    val Primary get() = palette.primary
    val PrimaryDark get() = palette.primaryDark
    val Secondary get() = palette.secondary
    val Accent get() = palette.accent
    val Avatar get() = palette.avatar
    val Success get() = palette.success
    val SuccessSoft get() = palette.successSoft
    val Error get() = palette.error
    val ErrorSoft get() = palette.errorSoft
    val Warning get() = palette.warning
    val WarningSoft get() = palette.warningSoft
    val CalendarTile get() = palette.calendarTile
    val CalendarToday get() = palette.calendarToday
    val DetailSurface get() = palette.detailSurface
    val HeatLow get() = palette.heatLow
    val HeatMedium get() = palette.heatMedium
    val HeatHigh get() = palette.heatHigh
    val HeatStrong get() = palette.heatStrong
    val HeatText get() = palette.heatText
    val TextStrong get() = palette.textStrong
    val TextMuted get() = palette.textMuted
}

@Preview(showBackground = true)
@Composable
private fun TouchHomeScreenPreview() {
    TouchTheme {
        TouchHomeScreen(
            session = UserSession(
                userId = "user_preview",
                displayName = "\u9648\u6797",
                email = "chenlin@example.com",
                accessToken = "preview_access",
                refreshToken = "preview_refresh",
                avatarUrl = null,
                bio = "喜欢记录偶遇。",
                birthday = "2003-05-30",
                gender = "保密",
                cardBackgroundUrl = null,
                cardBackgroundKey = "mizuki",
                lastSeenAt = null
            ),
            authApiClient = AuthApiClient(),
            mainHandler = Handler(Looper.getMainLooper()),
            themeStyle = ThemeStyle.Muelsyse,
            onThemeStyleChanged = {},
            onSessionChanged = {},
            onLogout = {}
        )
    }
}
