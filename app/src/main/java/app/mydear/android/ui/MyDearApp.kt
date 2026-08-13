package app.mydear.android.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.mydear.android.ui.theme.Coral
import app.mydear.android.ui.theme.WarmIvory
import app.mydear.android.R

private enum class MainTab(val label: String, val icon: ImageVector) {
    Chat("채팅", Icons.Default.ChatBubble),
    History("채팅 목록", Icons.Default.History),
    Settings("설정", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MyDearApp() {
    var tabName by rememberSaveable { mutableStateOf(MainTab.Chat.name) }
    var showTutorial by rememberSaveable { mutableStateOf(true) }
    var draft by rememberSaveable { mutableStateOf("") }
    val tab = MainTab.valueOf(tabName)

    Scaffold(
        containerColor = WarmIvory,
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.my_dear_mascot), contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text("내새끼", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp)
                } },
                actions = { OutlinedButton(onClick = { tabName = MainTab.Settings.name }, modifier = Modifier.height(52.dp)) { Text("AA  글자 크게") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmIvory),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, modifier = Modifier.testTag("primary-navigation")) {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tabName = item.name },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            MainTab.Chat -> ChatScreen(padding, draft, onDraftChange = { draft = it })
            MainTab.History -> HistoryScreen(padding)
            MainTab.Settings -> SettingsScreen(padding, onShowTutorial = { showTutorial = true })
        }
    }

    if (showTutorial) TutorialDialog(onClose = { showTutorial = false })
}

@Composable private fun ChatScreen(padding: PaddingValues, draft: String, onDraftChange: (String) -> Unit) {
    val messages = rememberSaveable { mutableStateListOf("오늘 서울 날씨 어때?") }
    var listening by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1EB)), shape = RoundedCornerShape(24.dp)) {
                    Text("오늘은 무엇을\n도와드릴까요?", modifier = Modifier.padding(24.dp).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                }
            }
            item { QuickAction("☀", "오늘 날씨 알려줘") }
            item { QuickAction("☎", "가족에게 전화하기") }
            item { QuickAction("◷", "약 먹을 시간 기억해줘") }
            items(messages) { message ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Color(0xFFFFEDE6), shape = RoundedCornerShape(20.dp), modifier = Modifier.align(Alignment.End)) { Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column {
                            Text("오늘 서울은 구름이 많고 낮 기온은 28℃로 덥겠습니다. 오후에 소나기가 내릴 수 있으니 우산을 챙기세요.", modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyLarge)
                            HorizontalDivider()
                            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Cloud, contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text("웹에서 찾았어요 · 오후 2:10", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                            Text("기상청  ·  2026년 8월 13일 업데이트", modifier = Modifier.padding(18.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { listening = !listening },
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(136.dp).testTag("voice-control"),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(36.dp))
                        Text(if (listening) "듣고 있어요" else "말로 하기", fontWeight = FontWeight.Bold)
                        Text(if (listening) "다 말했어요" else "눌러서 시작", fontSize = 13.sp)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Spacer(Modifier.width(7.dp)); Text("대화는 이 휴대폰에서 처리돼요", fontSize = 16.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지를 입력하세요") },
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (draft.isNotBlank()) { messages += draft.trim(); onDraftChange("") }
                }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { if (draft.isNotBlank()) { messages += draft.trim(); onDraftChange("") } },
                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "메시지 보내기", tint = Coral) }
        }
    }
}

@Composable private fun QuickAction(symbol: String, label: String) {
    Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFFEFD9)), contentAlignment = Alignment.Center) { Text(symbol, fontSize = 23.sp) }
        Spacer(Modifier.width(14.dp)); Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@Composable private fun HistoryScreen(padding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("채팅 목록", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text("지난 대화를 다시 볼 수 있어요.", style = MaterialTheme.typography.bodyLarge) }
        item { HistoryCard("오늘 서울 날씨", "오늘 · 오후 2:10", "오후에 소나기가 내릴 수 있어요…") }
        item { HistoryCard("약 먹을 시간", "어제 · 오전 9:30", "매일 아침 9시에 알려드릴게요.") }
    }
}

@Composable private fun HistoryCard(title: String, date: String, preview: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(date, color = Coral, fontSize = 16.sp); Spacer(Modifier.height(7.dp)); Text(preview, style = MaterialTheme.typography.bodyMedium)
    } }
}

@Composable private fun SettingsScreen(padding: PaddingValues, onShowTutorial: () -> Unit) {
    var expandedPlans by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("설정", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        item { Text("나에게 편한 모습과 목소리로 바꿔보세요.", style = MaterialTheme.typography.bodyLarge) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0E9)), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("무료 사용 중", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    Text("휴대폰 안에서 기본 대화는 계속 무료예요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("더 많은 검색과 고급 AI는 플러스에서 이용할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { expandedPlans = !expandedPlans }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (expandedPlans) "요금제 닫기" else "요금제 보기") }
                }
            }
        }
        if (expandedPlans) item { PlanComparison() }
        item { SettingRow("글자 크기", "크게") }
        item { SettingRow("목소리 속도", "천천히") }
        item { SettingRow("사용법 다시 보기", "4단계 안내", onShowTutorial) }
        item { SettingRow("개인정보와 인터넷 검색", "휴대폰 안에서 우선 처리") }
        item { SettingRow("모델과 저장 공간", "모델을 설치하지 않았어요") }
    }
}

@Composable private fun PlanComparison() {
    Column(Modifier.fillMaxWidth().border(2.dp, Coral, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("무료 · 0원", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("오프라인 채팅과 음성 · 기본 웹 검색", style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        Text("내새끼 플러스 · 월 3,900원 예정", color = Coral, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("더 많은 웹 검색 · 온라인 고급 AI 답변 · 가족 기능은 추후 제공", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("출시 알림 받기") }
        Text("실제 결제는 아직 연결되지 않았어요. 온라인 기능을 쓸 때만 필요한 질문이 서버로 전송돼요.", fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable private fun SettingRow(title: String, value: String, onClick: () -> Unit = {}) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(76.dp), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(value, fontSize = 15.sp) }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@Composable private fun TutorialDialog(onClose: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    val titles = listOf("글이나 말로 물어보세요", "말하는 중에도 다시 시작할 수 있어요", "검색어만 인터넷으로 보내요", "나에게 편하게 맞춰보세요")
    val bodies = listOf(
        "메시지를 쓰거나 큰 ‘말로 하기’ 버튼을 누르면 돼요.",
        "내새끼가 읽는 중에 ‘다시 말하기’를 누르면 바로 멈추고 새로 들을게요.",
        "일반 대화는 휴대폰 안에서 처리하고, 웹 검색을 선택한 경우 검색어만 온라인으로 보내요.",
        "설정에서 글자 크기와 목소리 속도를 언제든 바꿀 수 있어요.",
    )
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${step}단계", modifier = Modifier.background(Coral, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("$step / 4", fontSize = 17.sp) }
                Icon(if (step == 3) Icons.Outlined.Lock else Icons.Default.CheckCircle, contentDescription = null, tint = Coral, modifier = Modifier.size(52.dp))
                Text(titles[step - 1], style = MaterialTheme.typography.titleLarge)
                Text(bodies[step - 1], style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onClose, modifier = Modifier.height(52.dp)) { Text("건너뛰기") }
                    if (step > 1) OutlinedButton(onClick = { step-- }, modifier = Modifier.height(52.dp)) { Text("이전") }
                    Button(onClick = { if (step == 4) onClose() else step++ }, modifier = Modifier.height(52.dp)) { Text(if (step == 4) "시작하기" else "다음") }
                }
            }
        }
    }
}
