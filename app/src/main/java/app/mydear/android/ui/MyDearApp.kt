package app.mydear.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.automirrored.outlined.StopScreenShare
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.mydear.android.ui.theme.Coral
import app.mydear.android.ui.theme.WarmIvory
import app.mydear.android.ui.theme.WarmInk
import app.mydear.android.R
import app.mydear.android.domain.Role
import app.mydear.android.domain.Provenance
import app.mydear.android.models.GemmaTier
import app.mydear.android.voice.VoiceReducer
import app.mydear.android.voice.VoiceState
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mydear.android.tools.AndroidToolExecutor
import app.mydear.android.tools.ToolName
import app.mydear.android.runtime.screen.ScreenShareService
import app.mydear.android.runtime.screen.ScreenShareSession
import app.mydear.android.runtime.screen.ScreenShareState

private enum class MainTab(val label: String, val icon: ImageVector) {
    Chat("채팅", Icons.Default.ChatBubble),
    History("채팅 목록", Icons.Default.History),
    Settings("설정", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MyDearApp(
    chatViewModel: VoiceChatViewModel = viewModel(),
    isPictureInPicture: Boolean = false,
    onEnterPictureInPicture: () -> Unit = {},
) {
    val context = LocalContext.current
    val onboardingStore = remember(context) { OnboardingStore(context) }
    val uiPreferences = remember(context) { SeniorUiPreferenceStore(context) }
    var tabName by rememberSaveable { mutableStateOf(MainTab.Chat.name) }
    var showTutorial by remember { mutableStateOf(!onboardingStore.isCompleted()) }
    var showSearchDisclosure by rememberSaveable { mutableStateOf(chatViewModel.needsInternetConsent && onboardingStore.isCompleted()) }
    var showScreenShareDisclosure by rememberSaveable { mutableStateOf(false) }
    var largeText by rememberSaveable { mutableStateOf(uiPreferences.largeText()) }
    var enterPipAfterMicrophonePermission by remember { mutableStateOf(false) }
    val tab = MainTab.valueOf(tabName)
    val chatState by chatViewModel.state.collectAsState()
    val screenShareState by ScreenShareSession.state.collectAsState()
    LaunchedEffect(chatState.pendingTool) {
        when (chatState.pendingTool?.name) {
            ToolName.OpenSettings -> {
                tabName = MainTab.Settings.name
                chatViewModel.consumeTool()
            }
            ToolName.OpenChat -> {
                tabName = MainTab.Chat.name
                chatViewModel.consumeTool()
            }
            else -> Unit
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && enterPipAfterMicrophonePermission) {
            enterPipAfterMicrophonePermission = false
            onEnterPictureInPicture()
        } else if (granted) {
            chatViewModel.beginVoiceCapture()
        } else {
            enterPipAfterMicrophonePermission = false
            chatViewModel.microphonePermissionDenied(
                permanently = !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    context as android.app.Activity,
                    Manifest.permission.RECORD_AUDIO,
                ),
            )
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        chatViewModel.installSelectedModel()
    }
    val ttsNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        chatViewModel.installTtsModel()
    }
    val screenCapturePermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenShareService.start(context, result.resultCode, data)
        } else {
            ScreenShareSession.dismissFailure()
        }
    }
    val onInstallModel = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            chatViewModel.installSelectedModel()
        }
    }
    val onInstallTts = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ttsNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            chatViewModel.installTtsModel()
        }
    }
    val onVoiceClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            chatViewModel.beginVoiceCapture()
        } else {
            enterPipAfterMicrophonePermission = false
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val onUseOverAnotherApp = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onEnterPictureInPicture()
        } else {
            enterPipAfterMicrophonePermission = true
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (isPictureInPicture) {
        PictureInPictureAssistant(
            voiceState = chatState.voiceState,
            onVoiceClick = onVoiceClick,
        )
        return
    }

    val systemDensity = LocalDensity.current
    val appFontMultiplier = if (largeText) 1.15f else 1.0f
    val navigationHeight = when {
        systemDensity.fontScale >= 1.8f -> 112.dp
        systemDensity.fontScale >= 1.3f -> 96.dp
        else -> 80.dp
    }
    CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, systemDensity.fontScale * appFontMultiplier)) {
    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.my_dear_mascot), contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text("내새끼", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } },
                actions = { TextButton(onClick = {
                    largeText = !largeText
                    uiPreferences.setLargeText(largeText)
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (largeText) "기본 글자" else "큰 글자", maxLines = 1, fontSize = 14.sp)
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmIvory),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(navigationHeight)
                    .testTag("primary-navigation"),
            ) {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tabName = item.name },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.Chat -> ChatScreen(
                padding = padding,
                state = chatState,
                onDraftChange = chatViewModel::updateDraft,
                onSend = chatViewModel::sendDraft,
                onQuickPrompt = chatViewModel::sendQuickPrompt,
                onVoiceClick = onVoiceClick,
                screenShareState = screenShareState,
                onScreenShareClick = {
                    when (screenShareState) {
                        ScreenShareState.Active, ScreenShareState.Starting -> {
                            ScreenShareService.stop(context)
                        }
                        else -> showScreenShareDisclosure = true
                    }
                },
                onUseOverAnotherApp = onUseOverAnotherApp,
                onOpenSpeechSettings = {
                    val opened = runCatching {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    }.isSuccess
                    if (!opened) context.startActivity(Intent(Settings.ACTION_SETTINGS))
                },
            )
            MainTab.History -> HistoryScreen(padding, chatState, onOpenChat = { tabName = MainTab.Chat.name })
            MainTab.Settings -> SettingsScreen(
                padding = padding,
                state = chatState,
                onSelectModelTier = chatViewModel::selectModelTier,
                onInstallModel = onInstallModel,
                onInstallTts = onInstallTts,
                largeText = largeText,
                onLargeTextChange = {
                    largeText = it
                    uiPreferences.setLargeText(it)
                },
                onShowTutorial = { showTutorial = true },
                onForgetAllMemories = chatViewModel::forgetAllMemories,
                onWebSearchChanged = { enabled ->
                    if (enabled && chatViewModel.needsInternetConsent) showSearchDisclosure = true
                    else chatViewModel.setWebSearch(enabled)
                },
            )
        }
    }
    }

    if (showTutorial) TutorialDialog(onClose = {
        onboardingStore.markCompleted()
        showTutorial = false
        if (chatViewModel.needsInternetConsent) showSearchDisclosure = true
    })
    if (showSearchDisclosure) AlertDialog(
        onDismissRequest = { showSearchDisclosure = false },
        title = { Text("인터넷 도움 켜기") },
        text = { Text("날씨·최신 정보나 공개된 인물·단체 정보가 필요한 질문만 인터넷으로 확인해요. 현재 질문만 보내고, 음성 녹음과 이전 대화는 보내지 않아요. 이름, 주소, 전화번호는 질문에 쓰지 마세요.") },
        dismissButton = { OutlinedButton(onClick = { showSearchDisclosure = false }) { Text("취소") } },
        confirmButton = { Button(onClick = { showSearchDisclosure = false; chatViewModel.setWebSearch(true) }) { Text("동의하고 켜기") } },
    )
    if (showScreenShareDisclosure) AlertDialog(
        onDismissRequest = { showScreenShareDisclosure = false },
        title = { Text("화면을 함께 볼까요?") },
        text = {
            Text(
                "다른 앱에서 잘 모르겠는 화면을 보여주면, 질문할 때 화면의 버튼·아이콘·글자·사진을 함께 살펴 설명해요. 화면은 저장하거나 인터넷으로 보내지 않아요. 언제든 ‘멈추기’를 누를 수 있어요.",
            )
        },
        dismissButton = { OutlinedButton(onClick = { showScreenShareDisclosure = false }) { Text("취소") } },
        confirmButton = {
            Button(onClick = {
                showScreenShareDisclosure = false
                val manager = context.getSystemService(MediaProjectionManager::class.java)
                screenCapturePermission.launch(manager.createScreenCaptureIntent())
            }) { Text("화면 선택하기") }
        },
    )
    val pendingTool = chatState.pendingTool
    if (pendingTool != null && pendingTool.name !in setOf(ToolName.OpenSettings, ToolName.OpenChat)) {
        val summary = when (pendingTool.name) {
            ToolName.PrepareCall -> "${pendingTool.arguments["phone"]} 번호로 전화 앱을 열까요?"
            ToolName.PrepareAlarm -> "${pendingTool.arguments["hour"]}시 ${pendingTool.arguments["minute"]}분 알람 화면을 열까요?"
            ToolName.OpenMap -> "‘${pendingTool.arguments["query"]}’ 장소를 지도에서 찾을까요?"
            ToolName.PrepareMessage -> "문자 앱을 열까요?"
            else -> "외부 앱을 열까요?"
        }
        AlertDialog(
            onDismissRequest = { chatViewModel.consumeTool("동작을 취소했어요") },
            title = { Text("실행 전 확인") },
            text = { Text(summary, style = MaterialTheme.typography.bodyLarge) },
            dismissButton = { OutlinedButton(onClick = { chatViewModel.consumeTool("동작을 취소했어요") }) { Text("취소") } },
            confirmButton = {
                Button(onClick = {
                    val approved = chatViewModel.takePendingToolForExecution(pendingTool.nonce)
                    val result = if (approved == null) Result.failure(IllegalStateException("승인이 만료되었거나 이미 사용됐어요"))
                    else AndroidToolExecutor.executeExternal(context, approved)
                    chatViewModel.consumeTool(if (result.isSuccess) "외부 앱을 열었어요" else result.exceptionOrNull()?.message)
                }) { Text("확인하고 열기") }
            },
        )
    }
}

@Composable private fun ChatScreen(
    padding: PaddingValues,
    state: ChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onVoiceClick: () -> Unit,
    screenShareState: ScreenShareState,
    onScreenShareClick: () -> Unit,
    onUseOverAnotherApp: () -> Unit,
    onOpenSpeechSettings: () -> Unit,
) {
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitMessage = {
        keyboardController?.hide()
        onSend()
    }
    val latestMessageLength = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, latestMessageLength) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Column(Modifier.padding(top = 12.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("무엇을 도와드릴까요?", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                        Text("글로 묻거나 마이크를 눌러 말해보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (state.messages.isEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { QuickAction(Icons.Outlined.WbSunny, "오늘 날씨") { onQuickPrompt("오늘 날씨 알려줘") } }
                        item { QuickAction(Icons.Outlined.Phone, "전화 걸기") { onQuickPrompt("가족에게 전화하기") } }
                        item { QuickAction(Icons.Outlined.Alarm, "알람 맞추기") { onQuickPrompt("약 먹을 시간 기억해줘") } }
                    }
                }
            }
            items(state.messages, key = { it.id }) { message ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = if (message.role == Role.User) Color(0xFFFFEDE6) else Color.White,
                        shape = RoundedCornerShape(18.dp),
                        modifier = if (message.role == Role.User) Modifier.align(Alignment.End) else Modifier.align(Alignment.Start),
                    ) { Text(message.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge) }
                    val web = message.provenance as? Provenance.Web
                    if (web != null) {
                        Text("출처", color = Coral, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        web.sources.take(1).forEach { source ->
                            TextButton(
                                onClick = {
                                    if (source.url.startsWith("https://")) {
                                        runCatching { uriHandler.openUri(source.url) }
                                    }
                                },
                                enabled = source.url.startsWith("https://"),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "출처 링크: ${source.title}" },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        source.title,
                                        modifier = Modifier.weight(1f),
                                        color = Coral,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        tint = Coral,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            state.notice?.let { notice ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(notice, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        if (state.speechSettingsRequired) {
                            OutlinedButton(onClick = onOpenSpeechSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("한국어 음성 설정 열기")
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp)) {
            when (screenShareState) {
                ScreenShareState.Active -> Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag("screen-share-active"),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ScreenShare, contentDescription = null, tint = Coral, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "화면 공유 중 · 질문할 때 화면을 함께 봐요",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = onUseOverAnotherApp, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("작은 창")
                        }
                        TextButton(onClick = onScreenShareClick, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.StopScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("멈추기")
                        }
                    }
                }
                ScreenShareState.Starting -> Text(
                    "화면을 준비하고 있어요",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = Coral,
                    fontSize = 13.sp,
                )
                is ScreenShareState.Failed -> Text(
                    screenShareState.message,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
                ScreenShareState.Inactive -> Unit
            }
            if (state.voiceState !is VoiceState.Idle) {
                Text(
                    VoiceReducer.accessibleLabel(state.voiceState),
                    color = Coral,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (screenShareState !is ScreenShareState.Active && screenShareState !is ScreenShareState.Starting) {
                AssistChip(
                    onClick = onScreenShareClick,
                    label = { Text("화면 같이 보기", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ScreenShare, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("screen-share-control"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(50.dp).background(if (state.voiceState is VoiceState.Idle) Color(0xFFF4EFEC) else MaterialTheme.colorScheme.primaryContainer, CircleShape).testTag("voice-control"),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = VoiceReducer.accessibleLabel(state.voiceState), tint = if (state.voiceState is VoiceState.Idle) WarmInk else Coral)
                }
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("메시지를 입력하세요") },
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitMessage() }),
                )
                IconButton(
                    onClick = submitMessage,
                    modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "메시지 보내기", tint = Coral) }
            }
        }
    }
}

@Composable private fun PictureInPictureAssistant(
    voiceState: VoiceState,
    onVoiceClick: () -> Unit,
) {
    Surface(color = WarmIvory, modifier = Modifier.fillMaxSize().testTag("picture-in-picture-assistant")) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("내새끼", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = VoiceReducer.accessibleLabel(voiceState),
                    tint = Coral,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (voiceState is VoiceState.Idle) "눌러서 질문" else VoiceReducer.accessibleLabel(voiceState),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable private fun HistoryScreen(padding: PaddingValues, state: ChatUiState, onOpenChat: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("history-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("채팅 목록", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text("지난 대화를 다시 볼 수 있어요.", style = MaterialTheme.typography.bodyLarge) }
        if (state.messages.isEmpty()) {
            item { Text("아직 저장된 대화가 없어요.", style = MaterialTheme.typography.bodyLarge) }
        } else {
            item {
                val firstQuestion = state.messages.firstOrNull { it.role == Role.User }?.text ?: "새 대화"
                val latest = state.messages.last().text
                HistoryCard(firstQuestion.take(40), "이 휴대폰에 암호화하여 저장됨", latest.take(90), onOpenChat)
            }
        }
    }
}

@Composable private fun HistoryCard(title: String, date: String, preview: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(date, color = Coral, fontSize = 16.sp); Spacer(Modifier.height(7.dp)); Text(preview, style = MaterialTheme.typography.bodyMedium)
    } }
}

@Composable private fun SettingsScreen(
    padding: PaddingValues,
    state: ChatUiState,
    onSelectModelTier: (GemmaTier) -> Unit,
    onInstallModel: () -> Unit,
    onInstallTts: () -> Unit,
    largeText: Boolean,
    onLargeTextChange: (Boolean) -> Unit,
    onShowTutorial: () -> Unit,
    onForgetAllMemories: () -> Unit,
    onWebSearchChanged: (Boolean) -> Unit,
) {
    var expandedPlans by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showMemories by rememberSaveable { mutableStateOf(false) }
    var confirmForgetAll by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("settings-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("설정", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text("나에게 편한 모습과 목소리로 바꿔보세요.", style = MaterialTheme.typography.bodyLarge) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0E9)), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("무료 사용 중", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    Text("휴대폰 안에서 기본 대화는 계속 무료예요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("더 많은 검색과 고급 AI는 플러스에서 이용할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { expandedPlans = !expandedPlans }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (expandedPlans) "요금제 닫기" else "요금제 보기") }
                }
            }
        }
        if (expandedPlans) item { PlanComparison() }
        item {
            SettingToggleRow(
                "인터넷 도움",
                "날씨·최신 정보와 공개된 정보를 자동으로 확인",
                state.useWebSearch,
                onWebSearchChanged,
                testTag = "internet-toggle",
                enabled = state.searchConfigured,
            )
        }
        item { SettingToggleRow("큰 글자", "화면의 글자를 조금 더 크게 표시", largeText, onLargeTextChange, testTag = "large-text-toggle") }
        item { SettingRow("목소리 속도", "곧 사용할 수 있어요", enabled = false) }
        item {
            SettingRow(
                "내 정보 기억",
                if (state.savedMemories.isEmpty()) "기억해둔 내용 없음" else "${state.savedMemories.size}개 · 휴대폰 안에만 저장",
            ) { showMemories = true }
        }
        item { SettingRow("사용법 다시 보기", "5단계 안내", onClick = onShowTutorial) }
        item { SettingRow("개인정보와 인터넷 도움", "어떤 정보가 전송되는지 확인") { showPrivacy = true } }
        item {
            Column(
                Modifier.fillMaxWidth().border(1.dp, Color(0xFFE3D6CF), RoundedCornerShape(20.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("오프라인 AI 준비", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("처음 한 번 와이파이로 내려받으면 인터넷 없이 대화할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                GemmaTier.entries.forEach { tier ->
                    val selected = state.selectedModelTier == tier
                    OutlinedButton(
                        onClick = { onSelectModelTier(tier) },
                        enabled = !state.isDownloadingModel,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(tier.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(tier.shortDescription, fontSize = 14.sp)
                        }
                        Text(if (selected) "선택됨" else "선택", color = if (selected) Coral else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(
                    if (state.modelInstalled) "오프라인 대화 준비 완료" else "사용할 AI를 내려받아 주세요",
                    color = if (state.modelInstalled) Color(0xFF237A3B) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                if (state.isDownloadingModel) {
                    Text(
                        if (state.modelDownloadProgress == 0) "다운로드를 준비하고 있어요…" else "내려받는 중 ${state.modelDownloadProgress}%",
                        fontWeight = FontWeight.SemiBold,
                    )
                    LinearProgressIndicator(
                        progress = { state.modelDownloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("화면을 나가도 다운로드는 계속돼요. 연결이 끊기면 받은 부분부터 다시 이어져요.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Button(
                        onClick = onInstallModel,
                        enabled = !state.modelInstalled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(if (state.modelInstalled) "준비 완료" else "${state.selectedModelTier.label} 다운로드") }
                }
                Text(
                    if (state.ttsInstalled) "한국어 목소리 준비 완료" else "답변을 읽어줄 한국어 목소리도 받을 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onInstallTts,
                    enabled = !state.ttsInstalled && !state.isDownloadingTts,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) {
                    Text(
                        when {
                            state.ttsInstalled -> "목소리 준비 완료"
                            state.isDownloadingTts && state.ttsDownloadProgress == 0 -> "목소리 다운로드 준비 중…"
                            state.isDownloadingTts -> "목소리 받는 중 ${state.ttsDownloadProgress}%"
                            else -> "한국어 목소리 다운로드"
                        },
                    )
                }
                Text("두 AI 모두 이 휴대폰에만 저장돼요. 기본 AI를 먼저 사용해보고 답변이 더 자세해야 할 때 고급 AI로 바꿔보세요.", fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
    }
    if (showPrivacy) AlertDialog(
        onDismissRequest = { showPrivacy = false },
        title = { Text("개인정보와 검색") },
        text = { Text("일반 대화와 음성은 휴대폰 안에서 처리해요. 인터넷 도움을 켜면 날씨·최신 정보나 공개된 인물·단체 정보가 필요한 현재 질문만 인터넷으로 전송해요. 음성 녹음과 이전 대화는 보내지 않아요.") },
        confirmButton = { Button(onClick = { showPrivacy = false }) { Text("확인") } },
    )
    if (showMemories) AlertDialog(
        onDismissRequest = { showMemories = false },
        title = { Text("내 정보 기억") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (state.savedMemories.isEmpty()) {
                        "‘내 이름은 ○○야, 기억해줘’처럼 말하면 필요한 정보만 휴대폰 안에 기억해요. 인터넷 검색 내용은 자동으로 기억하지 않아요."
                    } else {
                        state.savedMemories.takeLast(8).joinToString(separator = "\n") { "• $it" }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        dismissButton = {
            if (state.savedMemories.isNotEmpty()) {
                TextButton(onClick = { showMemories = false; confirmForgetAll = true }) { Text("모두 잊기") }
            }
        },
        confirmButton = { Button(onClick = { showMemories = false }) { Text("닫기") } },
    )
    if (confirmForgetAll) AlertDialog(
        onDismissRequest = { confirmForgetAll = false },
        title = { Text("저장한 정보를 모두 잊을까요?") },
        text = { Text("휴대폰에 따로 기억해둔 내 정보만 지워요. 채팅 내용은 그대로 남아 있어요.") },
        dismissButton = { OutlinedButton(onClick = { confirmForgetAll = false }) { Text("취소") } },
        confirmButton = {
            Button(onClick = { confirmForgetAll = false; onForgetAllMemories() }) { Text("모두 잊기") }
        },
    )
}

@Composable private fun PlanComparison() {
    Column(Modifier.fillMaxWidth().border(2.dp, Coral, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("무료 · 0원", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("오프라인 채팅과 음성 · 기본 인터넷 도움", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("내새끼 플러스 · 월 3,900원 예정", color = Coral, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("더 많은 웹 검색 · 온라인 고급 AI 답변 · 가족 기능은 추후 제공", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("출시 알림 · 준비 중") }
        Text("실제 결제는 아직 연결되지 않았어요. 온라인 기능을 쓸 때만 필요한 질문이 서버로 전송돼요.", fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable private fun SettingRow(title: String, value: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(value, fontSize = 15.sp) }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@Composable private fun SettingToggleRow(
    title: String,
    value: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3D6CF)),
        color = Color.Transparent,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = title }
                .toggleable(value = checked, enabled = enabled, role = SemanticsRole.Switch, onValueChange = onCheckedChange)
                .testTag(testTag)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(value, fontSize = 15.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

@Composable private fun TutorialDialog(onClose: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    val titles = listOf("글이나 말로 물어보세요", "답변 중에도 다시 말할 수 있어요", "최신 정보는 인터넷으로 확인해요", "모르는 화면을 함께 보세요", "나에게 편하게 맞춰보세요")
    val bodies = listOf(
        "아래 입력창에 메시지를 쓰거나 왼쪽 마이크 버튼을 눌러 말해보세요.",
        "내새끼가 읽는 중에도 마이크를 누르면 답변을 멈추고 새 질문을 들을게요.",
        "인터넷 도움은 처음부터 켜져 있어요. 날씨·최신 정보나 공개된 인물·단체 정보가 필요한 질문만 인터넷으로 확인하고, 현재 질문만 보내요. 설정에서 언제든 끌 수 있어요.",
        "‘화면 같이 보기’를 누르고 보여줄 화면을 고르세요. 질문할 때 버튼, 아이콘, 글자와 사진을 함께 살펴보고, 화면은 저장하지 않아요.",
        "설정에서 글자 크기를 언제든 바꿀 수 있어요.",
    )
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${step}단계", modifier = Modifier.background(Coral, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("$step / 5", fontSize = 17.sp) }
                Icon(
                    when (step) {
                        3 -> Icons.Outlined.Lock
                        4 -> Icons.AutoMirrored.Outlined.ScreenShare
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = Coral,
                    modifier = Modifier.size(52.dp),
                )
                Text(titles[step - 1], style = MaterialTheme.typography.titleLarge)
                Text(bodies[step - 1], style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 52.dp)) { Text("건너뛰기") }
                    if (step > 1) OutlinedButton(onClick = { step-- }, modifier = Modifier.heightIn(min = 52.dp)) { Text("이전") }
                    Button(onClick = { if (step == 5) onClose() else step++ }, modifier = Modifier.heightIn(min = 52.dp)) { Text(if (step == 5) "시작하기" else "다음") }
                }
            }
        }
    }
}
