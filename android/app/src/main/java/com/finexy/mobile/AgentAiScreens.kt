package com.finexy.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finexy.mobile.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(seconds: Long): String =
    if (seconds <= 0) "未知" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000))

private fun formatCount(value: Long): String = NumberFormat.getIntegerInstance().format(value)

@Composable
private fun AgentAiPageFrame(title: String, subtitle: String, backLabel: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = CanvasBlack) {
        Box(Modifier.safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = backLabel }) {
                    Mark("back", Muted, 16); Spacer(Modifier.width(6.dp)); Text("返回设置")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text(subtitle, color = Muted, fontSize = 13.sp)
                    }
                    action?.invoke()
                }
                content()
            }
        }
    }
}

@Composable
private fun StatusText(message: String?, error: Boolean) {
    message?.let {
        Text(it, color = if (error) MaterialTheme.colorScheme.error else IncomeGreen, fontSize = 13.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun UsageBlock(label: String, item: AiUsageSummary) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text("调用 ${formatCount(item.requests)} 次 · 失败 ${formatCount(item.failedRequests)} 次", fontSize = 13.sp)
        Text("输入 ${formatCount(item.inputTokens)} · 输出 ${formatCount(item.outputTokens)} · 合计 ${formatCount(item.totalTokens)} tokens", color = Muted, fontSize = 12.sp)
        if (item.unmeteredRequests > 0) Text("其中 ${formatCount(item.unmeteredRequests)} 次服务商未返回用量，未计入 tokens", color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun AiUsagePage(store: SecureStore, localMode: Boolean, onBack: () -> Unit) {
    val api = remember(store) { AgentAiApi(FinexyApi(store)) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<AiUsageSnapshot?>(null) }
    var balance by remember { mutableStateOf<AiBalanceSnapshot?>(null) }
    var usageError by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun load() {
        if (localMode || loading) return
        scope.launch {
            loading = true; usageError = null; balanceError = null
            try { usage = api.usage() } catch (error: CancellationException) { throw error } catch (error: Exception) { usageError = "读取用量失败：${error.message ?: "请检查网络后重试"}" }
            try { balance = api.balance() } catch (error: CancellationException) { throw error } catch (error: Exception) { balanceError = "读取余额失败：${error.message ?: "请检查网络后重试"}" }
            loading = false
        }
    }
    LaunchedEffect(localMode) { load() }

    AgentAiPageFrame("AI 用量与余额", "查看你在 Finexy 中的模型调用量和服务余额。", "返回设置", onBack, action = {
        TextButton(onClick = ::load, enabled = !localMode && !loading, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (loading) "读取中…" else "刷新") }
    }) {
        if (localMode) { Text("本机账本没有服务器，请先连接服务器。", color = Muted); return@AgentAiPageFrame }
        StatusText(usageError, true); StatusText(balanceError, true)
        PanelCard {
            Text("服务余额", color = Muted, fontSize = 12.sp)
            val current = balance
            when {
                current == null -> if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("暂未读取", color = Muted)
                current.status == "unsupported" -> Text("当前接口不是 DeepSeek 官方服务，暂不支持查询余额。", color = Muted, fontSize = 13.sp)
                current.status == "not_configured" -> Text("尚未配置模型密钥，请在服务端配置后再查看。", color = Muted, fontSize = 13.sp)
                current.balances.isEmpty() -> Text("暂未获取余额。可刷新重试，或在官方平台查看。", color = Muted, fontSize = 13.sp)
                else -> current.balances.forEach { item ->
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("${item.total} ${item.currency}", style = MaterialTheme.typography.titleLarge)
                        Text("赠送 ${item.granted} · 充值 ${item.toppedUp}", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        PanelCard {
            Text("我的调用用量", color = Muted, fontSize = 12.sp)
            val current = usage
            if (current == null) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("暂未读取", color = Muted) }
            else {
                UsageBlock("今日", current.today); UsageBlock("本月", current.month); UsageBlock("累计", current.all)
                Text("统计时区：${current.timezone.ifBlank { "服务器默认" }} · 更新于 ${formatTime(current.updatedAt)}", color = Muted, fontSize = 11.sp)
            }
        }
        usage?.recent?.takeIf { it.isNotEmpty() }?.let { recent ->
            PanelCard {
                Text("最近调用", color = Muted, fontSize = 12.sp)
                recent.forEach { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("${formatTime(item.time)} · ${item.modelId.ifBlank { item.provider }}", fontSize = 13.sp)
                        Text(
                            (if (item.success) "成功" else "失败") + " · " + (if (item.usageKnown) "${formatCount(item.totalTokens)} tokens" else "用量未返回") + " · ${item.durationMs} ms",
                            color = if (item.success) Muted else MaterialTheme.colorScheme.error, fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgentAccessPage(store: SecureStore, localMode: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val api = remember(store) { AgentAiApi(FinexyApi(store)) }
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf<AgentAccessState?>(null) }
    var tokens by remember { mutableStateOf<List<AgentToken>>(emptyList()) }
    var ledgers by remember { mutableStateOf<List<Pair<Long, String>>>(listOf(0L to "默认个人账本")) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var mcp by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("我的 Codex") }
    var expiry by rememberSaveable { mutableLongStateOf(86_400L) }
    var scopeText by rememberSaveable { mutableStateOf(AgentScopes.READ) }
    val scopes = scopeText.split(',').filter { it.isNotBlank() }.toSet()
    var ledgerId by rememberSaveable { mutableLongStateOf(0L) }
    var password by remember { mutableStateOf("") }
    var generated by remember { mutableStateOf<GeneratedAgentToken?>(null) }
    var revoking by remember { mutableStateOf<AgentToken?>(null) }

    fun report(text: String, isError: Boolean) { message = text; failed = isError }
    fun copy(label: String, value: String) {
        context.getSystemService(Context.CLIPBOARD_SERVICE).let { it as ClipboardManager }.setPrimaryClip(ClipData.newPlainText(label, value))
        report("已复制$label", false)
    }
    fun load() {
        if (localMode || loading) return
        scope.launch {
            loading = true
            try {
                access = api.access(); tokens = api.tokens()
                runCatching { FinexyApi(store).listLedgers() }.getOrNull()?.let { list ->
                    ledgers = listOf(0L to "默认个人账本") + list.map { it.id to it.name }
                }
            } catch (error: CancellationException) { throw error } catch (error: Exception) { report("读取失败：${error.message ?: "请检查网络后重试"}", true) }
            loading = false
        }
    }
    LaunchedEffect(localMode) { load() }
    DisposableEffect(Unit) { onDispose { password = ""; generated = null } }

    val mcpReady = access?.let { it.mcpAvailable && it.mcpEnabled } == true
    AgentAiPageFrame("Agent 接入", "为 Codex 等助手生成专用令牌，并控制它能做什么。", "返回设置", onBack, action = {
        TextButton(onClick = ::load, enabled = !localMode && !loading && !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (loading) "读取中…" else "刷新") }
    }) {
        if (localMode) { Text("本机账本没有服务器，请先连接服务器。", color = Muted); return@AgentAiPageFrame }
        StatusText(message, failed)
        PanelCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("此账号的 MCP 接入")
                    Text(
                        when { access == null -> "正在读取状态…"; access?.mcpAvailable != true -> "管理员未允许 MCP 接入"; else -> "关闭后已有 MCP 令牌暂停访问，API 令牌不受影响" },
                        color = Muted, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = access?.mcpEnabled == true, enabled = access?.mcpAvailable == true && !busy,
                    onCheckedChange = { target -> scope.launch {
                        busy = true
                        try { access = api.setMcpEnabled(target); generated = null; password = ""; report(if (target) "MCP 接入已开启" else "MCP 接入已关闭", false) }
                        catch (error: CancellationException) { throw error } catch (error: Exception) { report("保存失败：${error.message ?: "请稍后重试"}", true) }
                        busy = false
                    } },
                    modifier = Modifier.semantics { contentDescription = "此账号的 MCP 接入" }
                )
            }
        }
        val created = generated
        if (created != null) PanelCard {
            Text("令牌已生成", fontWeight = FontWeight.SemiBold)
            Text("完整令牌只在这里显示一次，离开页面后无法再次查看。", color = Muted, fontSize = 12.sp)
            SelectionContainer { Text(created.token, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (created.endpoint.isNotBlank()) Text("地址：${created.endpoint}", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copy("令牌", created.token) }, modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape) { Text("复制令牌") }
                OutlinedButton(onClick = { generated = null }, modifier = Modifier.heightIn(min = 48.dp), shape = CircleShape) { Text("完成") }
            }
        } else PanelCard {
            Text("生成令牌", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(name, { name = it.take(64) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("令牌名称") }, singleLine = true, enabled = !busy)
            Text("连接方式", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mcp, onClick = { mcp = true }, enabled = mcpReady && !busy, label = { Text("MCP · Codex 等助手") }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = !mcp, onClick = { mcp = false }, enabled = !busy, label = { Text("API") }, modifier = Modifier.heightIn(min = 48.dp))
            }
            Text("有效期", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3_600L to "1 小时", 86_400L to "1 天", 604_800L to "7 天", 2_592_000L to "30 天").forEach { (seconds, label) ->
                    FilterChip(selected = expiry == seconds, onClick = { expiry = seconds }, enabled = !busy, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Text("授权范围", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            AgentScopes.all.forEach { (id, description) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = id in scopes, enabled = id != AgentScopes.READ && !busy, onCheckedChange = { checked -> scopeText = (if (checked) scopes + id else scopes - id).sorted().joinToString(",") })
                    Text(description, fontSize = 13.sp)
                }
            }
            Text("授权账本", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ledgers.forEach { (id, label) -> FilterChip(selected = ledgerId == id, onClick = { ledgerId = id }, enabled = !busy, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
            Text("令牌只能访问所选账本；切换 App 当前账本不会改变已生成令牌的授权。", color = Muted, fontSize = 12.sp)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Finexy 登录密码") }, singleLine = true, enabled = !busy,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Button(
                onClick = { scope.launch {
                    busy = true; message = null
                    try {
                        generated = api.generate(mcp, name, scopes, expiry, ledgerId, password)
                        password = ""; tokens = api.tokens(); report("令牌已生成，请立即保存", false)
                    } catch (error: CancellationException) { throw error } catch (error: Exception) { report("生成失败：${error.message ?: "请核对密码与服务开关后重试"}", true) }
                    busy = false
                } },
                enabled = !busy && name.isNotBlank() && password.length >= 6 && (if (mcp) mcpReady else true),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp), shape = CircleShape
            ) { Text(if (busy) "正在生成…" else "生成令牌") }
        }
        PanelCard {
            Text("已授权的 Agent", fontWeight = FontWeight.SemiBold)
            when {
                loading && tokens.isEmpty() -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                tokens.isEmpty() -> Text("暂无 Agent 令牌。生成后，可随时在这里撤销。", color = Muted, fontSize = 13.sp)
                else -> tokens.forEach { token ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(token.name.ifBlank { "旧版令牌" })
                            Text("${if (token.isMcp) "MCP" else "API"} · 到期 ${formatTime(token.expiresAt)}", color = Muted, fontSize = 12.sp)
                            Text("账本：${ledgers.firstOrNull { it.first == token.ledgerId }?.second ?: "账本 ${token.ledgerId}"} · ${token.scopes.joinToString(" · ") { AgentScopes.label(it) }}", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { revoking = token }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "撤销令牌：${token.name.ifBlank { "旧版令牌" }}" }) {
                            Text("撤销", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    revoking?.let { token ->
        AlertDialog(
            onDismissRequest = { if (!busy) revoking = null }, title = { Text("撤销 Agent 令牌？") },
            text = { Text("撤销「${token.name.ifBlank { "旧版令牌" }}」后，使用它的助手会立即失去访问权限。已有流水保留。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true
                try { api.revoke(token.tokenId); tokens = api.tokens(); generated = null; report("令牌已撤销", false) }
                catch (error: CancellationException) { throw error } catch (error: Exception) { report("撤销失败：${error.message ?: "请稍后重试"}", true) }
                busy = false; revoking = null
            } }) { Text("确认撤销", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { revoking = null }) { Text("取消") } }
        )
    }
}
