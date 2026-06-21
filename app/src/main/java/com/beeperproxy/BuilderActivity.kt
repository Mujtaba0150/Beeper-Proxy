package com.beeperproxy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.beeperproxy.databinding.ActivityBuilderBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BuilderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuilderBinding

    // Which operation is selected
    private var selectedOp = "chats"

    private var testJob: Job? = null

    // Output columns returned by each endpoint, per the API reference doc
    private val opColumns = mapOf(
        "chats" to listOf(
            "roomId", "title", "messagePreview", "senderEntityId", "protocol",
            "isMuted", "unreadCount", "timestamp", "oneToOne"
        ),
        "chats/count" to listOf("count"),
        "messages" to listOf(
            "roomId", "originalId", "senderContactId", "timestamp", "isSentByMe",
            "isDeleted", "type", "text_content", "reactions", "displayName",
            "is_search_match", "paging_offset (openAtUnread only)", "last_read (openAtUnread only)"
        ),
        "messages/count" to listOf("count"),
        "contacts" to listOf(
            "id", "roomIds", "displayName", "contactDisplayName",
            "linkedContactId", "itsMe", "protocol"
        ),
        "contacts/count" to listOf("count"),
        "insert" to listOf("(no cursor) Result URI query params: roomId, messageId — or null on failure")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupOpButtons()
        setupFieldListeners()
        setupActions()
        setupBottomNav()

        // Set initial state
        selectOp("chats")
    }

    // ── Bottom navigation ─────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_builder
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_builder -> true // already here
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_apps -> {
                    startActivity(Intent(this, AppsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    // ── Operation buttons ─────────────────────────────────────────────────────

    private fun setupOpButtons() {
        binding.btnOpChats.setOnClickListener { selectOp("chats") }
        binding.btnOpChatsCount.setOnClickListener { selectOp("chats/count") }
        binding.btnOpMessages.setOnClickListener { selectOp("messages") }
        binding.btnOpMessagesCount.setOnClickListener { selectOp("messages/count") }
        binding.btnOpContacts.setOnClickListener { selectOp("contacts") }
        binding.btnOpContactsCount.setOnClickListener { selectOp("contacts/count") }
        binding.btnOpInsert.setOnClickListener { selectOp("insert") }
    }

    private fun selectOp(op: String) {
        selectedOp = op

        val opButtons = listOf(
            binding.btnOpChats to "chats",
            binding.btnOpChatsCount to "chats/count",
            binding.btnOpMessages to "messages",
            binding.btnOpMessagesCount to "messages/count",
            binding.btnOpContacts to "contacts",
            binding.btnOpContactsCount to "contacts/count",
            binding.btnOpInsert to "insert"
        )
        opButtons.forEach { (btn, id) -> highlightFilledButton(btn, id == op) }

        // Show/hide relevant field groups
        val isInsert = op == "insert"
        val isMessages = op == "messages" || op == "messages/count"
        val isMessagesList = op == "messages"
        val isChats = op == "chats" || op == "chats/count"
        val isContacts = op == "contacts" || op == "contacts/count"

        binding.groupInsert.visibility = if (isInsert) View.VISIBLE else View.GONE
        binding.groupLimit.visibility = if (!isInsert) View.VISIBLE else View.GONE
        binding.groupOffset.visibility = if (!isInsert) View.VISIBLE else View.GONE
        binding.groupColumns.visibility = if (!isInsert) View.VISIBLE else View.GONE
        binding.groupRoomIdsFilter.visibility = if (!isInsert) View.VISIBLE else View.GONE

        binding.groupIsUnread.visibility = if (isChats) View.VISIBLE else View.GONE
        binding.groupIsLowPriority.visibility = if (isChats) View.VISIBLE else View.GONE
        binding.groupIsArchived.visibility = if (isChats) View.VISIBLE else View.GONE
        binding.groupShowInAllChats.visibility = if (isChats) View.VISIBLE else View.GONE

        binding.groupProtocol.visibility = if (isChats || isContacts) View.VISIBLE else View.GONE

        binding.groupMessageQuery.visibility = if (isMessages) View.VISIBLE else View.GONE
        binding.groupSenderId.visibility = if (isMessages) View.VISIBLE else View.GONE
        binding.groupContext.visibility = if (isMessagesList) View.VISIBLE else View.GONE
        binding.groupOpenAtUnread.visibility = if (isMessagesList) View.VISIBLE else View.GONE

        binding.groupContactQuery.visibility = if (isContacts) View.VISIBLE else View.GONE
        binding.groupSenderIds.visibility = if (isContacts) View.VISIBLE else View.GONE

        // Output columns for this op
        val cols = opColumns[op] ?: emptyList()
        binding.tvOutputColumns.text = cols.joinToString("\n") { "• $it" }

        // Reset test result area when switching ops
        binding.svTestResult.visibility = View.GONE
        binding.groupTestLoading.visibility = View.GONE

        regenerate()
    }

    /** Highlight a pill-style op button: accent background when selected. */
    private fun highlightFilledButton(btn: MaterialButton, selected: Boolean) {
        btn.isSelected = selected
        btn.backgroundTintList = ColorStateList.valueOf(
            getColor(if (selected) R.color.accent else R.color.btn_secondary)
        )
        btn.setTextColor(getColor(if (selected) R.color.bg_dark else R.color.text_primary))
    }

    // ── Field change listeners → regenerate on every keystroke ───────────────

    private fun setupFieldListeners() {
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = regenerate()
        }
        binding.etLimit.addTextChangedListener(watcher)
        binding.etOffset.addTextChangedListener(watcher)
        binding.etColumns.addTextChangedListener(watcher)
        binding.etRoomIdsFilter.addTextChangedListener(watcher)
        binding.etProtocol.addTextChangedListener(watcher)
        binding.etMessageQuery.addTextChangedListener(watcher)
        binding.etSenderId.addTextChangedListener(watcher)
        binding.etContextBefore.addTextChangedListener(watcher)
        binding.etContextAfter.addTextChangedListener(watcher)
        binding.etContactQuery.addTextChangedListener(watcher)
        binding.etSenderIds.addTextChangedListener(watcher)
        binding.etRoomId.addTextChangedListener(watcher)
        binding.etMessageText.addTextChangedListener(watcher)

        binding.cbIsUnread.setOnCheckedChangeListener { _, _ -> regenerate() }
        binding.cbIsLowPriority.setOnCheckedChangeListener { _, _ -> regenerate() }
        binding.cbIsArchived.setOnCheckedChangeListener { _, _ -> regenerate() }
        binding.cbShowInAllChats.setOnCheckedChangeListener { _, _ -> regenerate() }
        binding.cbOpenAtUnread.setOnCheckedChangeListener { _, _ -> regenerate() }
    }

    // ── Copy & Test actions ───────────────────────────────────────────────────

    private fun setupActions() {
        binding.btnCopyQuery.setOnClickListener {
            copyToClipboard(binding.tvGeneratedQuery.text.toString(), "Query copied to clipboard")
        }
        binding.btnCopyIntent.setOnClickListener {
            copyToClipboard(binding.tvGeneratedIntent.text.toString(), "Intent command copied to clipboard")
        }

        binding.btnTest.setOnClickListener { runTest() }
    }

    private fun copyToClipboard(text: String, toastMessage: String) {
        if (text.isBlank()) return
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Beeper Proxy", text))
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
    }

    // ── Query generation ──────────────────────────────────────────────────────

    private fun regenerate() {
        val token = AuthTokenManager.getOrCreateToken(this)
        binding.tvGeneratedQuery.text = buildContentUri(token)
        binding.tvGeneratedIntent.text = buildIntentCommand(token)
    }

    private fun buildContentUri(token: String): String {
        val path = if (selectedOp == "insert") "messages" else selectedOp
        val builder = Uri.Builder()
            .scheme("content")
            .authority("com.beeperproxy.provider")
            .appendEncodedPath(path)
            .appendQueryParameter("authToken", token)

        applySharedParams(builder)

        return builder.build().toString()
    }

    private fun buildIntentCommand(token: String): String {
        val action = if (selectedOp == "insert") "com.beeperproxy.INSERT" else "com.beeperproxy.QUERY"
        val path = if (selectedOp == "insert") "messages" else selectedOp

        val sb = StringBuilder()
        sb.append("adb shell am broadcast \\\n")
        sb.append("  -a $action \\\n")
        sb.append("  -n com.beeperproxy/.BeeperIntentReceiver \\\n")
        sb.append("  --es authToken \"$token\" \\\n")
        sb.append("  --es path \"$path\"")

        val params = buildParamsString()
        if (params.isNotEmpty()) {
            sb.append(" \\\n  --es params \"$params\"")
        }

        if (selectedOp != "insert") {
            val columns = binding.etColumns.text.toString().trim()
            if (columns.isNotEmpty()) {
                sb.append(" \\\n  --es columns \"$columns\"")
            }
        }

        if (selectedOp == "insert") {
            val roomId = binding.etRoomId.text.toString().trim()
            val text = binding.etMessageText.text.toString().trim()
            if (roomId.isNotEmpty()) sb.append(" \\\n  --es roomId \"$roomId\"")
            if (text.isNotEmpty()) sb.append(" \\\n  --es text \"$text\"")
        }

        return sb.toString()
    }

    /** Collects every active filter as a list of "key=value" pairs (unencoded). */
    private fun collectParamPairs(): List<Pair<String, String>> {
        if (selectedOp == "insert") {
            val pairs = mutableListOf<Pair<String, String>>()
            val roomId = binding.etRoomId.text.toString().trim()
            val text = binding.etMessageText.text.toString().trim()
            if (roomId.isNotEmpty()) pairs.add("roomId" to roomId)
            if (text.isNotEmpty()) pairs.add("text" to text)
            return pairs
        }

        val isMessages = selectedOp == "messages" || selectedOp == "messages/count"
        val isMessagesList = selectedOp == "messages"
        val isChats = selectedOp == "chats" || selectedOp == "chats/count"
        val isContacts = selectedOp == "contacts" || selectedOp == "contacts/count"

        val pairs = mutableListOf<Pair<String, String>>()

        val limit = binding.etLimit.text.toString().trim()
        if (limit.isNotEmpty()) pairs.add("limit" to limit)

        val offset = binding.etOffset.text.toString().trim()
        if (offset.isNotEmpty()) pairs.add("offset" to offset)

        val roomIds = binding.etRoomIdsFilter.text.toString().trim()
        if (roomIds.isNotEmpty()) pairs.add("roomIds" to roomIds)

        if (isChats) {
            if (binding.cbIsUnread.isChecked) pairs.add("isUnread" to "1")
            if (binding.cbIsLowPriority.isChecked) pairs.add("isLowPriority" to "1")
            if (binding.cbIsArchived.isChecked) pairs.add("isArchived" to "1")
            if (binding.cbShowInAllChats.isChecked) pairs.add("showInAllChats" to "1")
        }

        if (isChats || isContacts) {
            val protocol = binding.etProtocol.text.toString().trim()
            if (protocol.isNotEmpty()) pairs.add("protocol" to protocol)
        }

        if (isMessages) {
            val msgQuery = binding.etMessageQuery.text.toString().trim()
            if (msgQuery.isNotEmpty()) pairs.add("query" to msgQuery)

            val senderId = binding.etSenderId.text.toString().trim()
            if (senderId.isNotEmpty()) pairs.add("senderId" to senderId)
        }

        if (isMessagesList) {
            val before = binding.etContextBefore.text.toString().trim()
            if (before.isNotEmpty()) pairs.add("contextBefore" to before)

            val after = binding.etContextAfter.text.toString().trim()
            if (after.isNotEmpty()) pairs.add("contextAfter" to after)

            if (binding.cbOpenAtUnread.isChecked) pairs.add("openAtUnread" to "true")
        }

        if (isContacts) {
            val contactQuery = binding.etContactQuery.text.toString().trim()
            if (contactQuery.isNotEmpty()) pairs.add("query" to contactQuery)

            val senderIds = binding.etSenderIds.text.toString().trim()
            if (senderIds.isNotEmpty()) pairs.add("senderIds" to senderIds)
        }

        return pairs
    }

    /** Parses the Columns field into a clean array, or null if it's empty (= all columns). */
    private fun parseProjection(): Array<String>? {
        val raw = binding.etColumns.text.toString().trim()
        if (raw.isEmpty()) return null
        val cols = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (cols.isEmpty()) null else cols.toTypedArray()
    }

    private fun applySharedParams(builder: Uri.Builder) {
        collectParamPairs().forEach { (key, value) -> builder.appendQueryParameter(key, value) }
    }

    private fun buildParamsString(): String {
        return collectParamPairs().joinToString("&") { (key, value) -> "$key=$value" }
    }

    // ── In-app test execution ─────────────────────────────────────────────────

    private fun runTest() {
        testJob?.cancel()
        val token = AuthTokenManager.getOrCreateToken(this)

        binding.tvLoadingLabel.text = "Querying $selectedOp…"
        binding.groupTestLoading.visibility = View.VISIBLE
        binding.svTestResult.visibility = View.GONE
        binding.btnTest.isEnabled = false

        testJob = CoroutineScope(Dispatchers.IO).launch {
            val result = if (selectedOp == "insert") {
                runInsertTest(token)
            } else {
                runQueryTest(token)
            }

            withContext(Dispatchers.Main) {
                binding.tvTestResult.text = result
                binding.groupTestLoading.visibility = View.GONE
                binding.svTestResult.visibility = View.VISIBLE
                binding.btnTest.isEnabled = true
            }
        }
    }

    private fun runQueryTest(token: String): String {
        val path = selectedOp
        val uriBuilder = Uri.Builder()
            .scheme("content")
            .authority("com.beeperproxy.provider")
            .appendEncodedPath(path)
            .appendQueryParameter("authToken", token)
        applySharedParams(uriBuilder)

        val projection = parseProjection()

        return try {
            contentResolver.query(uriBuilder.build(), projection, null, null, null).use { cursor ->
                if (cursor == null) {
                    "✗ cursor = null\nCheck Beeper permissions."
                } else {
                    val rowCount = cursor.count
                    val columns = cursor.columnNames.toList()
                    if (rowCount == 0) {
                        "✓ 0 rows returned\nColumns: ${columns.joinToString()}"
                    } else {
                        val sb = StringBuilder("✓ $rowCount row(s) — columns: ${columns.joinToString()}\n")
                        sb.append("─".repeat(40)).append("\n")
                        var row = 0
                        cursor.moveToPosition(-1)
                        while (cursor.moveToNext()) {
                            sb.append("\n── Row ${++row} ──\n")
                            columns.forEachIndexed { i, col ->
                                val value = try {
                                    cursor.getString(i) ?: "<null>"
                                } catch (e: Exception) {
                                    "<error>"
                                }
                                sb.append("$col = $value\n")
                            }
                        }
                        sb.toString()
                    }
                }
            }
        } catch (e: Exception) {
            "✗ ${e.javaClass.simpleName}\n${e.message}"
        }
    }

    private fun runInsertTest(token: String): String {
        val roomId = binding.etRoomId.text.toString().trim()
        val text = binding.etMessageText.text.toString().trim()

        if (roomId.isEmpty() || text.isEmpty()) {
            return "✗ Room ID and message text are required for insert."
        }

        val uri = Uri.Builder()
            .scheme("content")
            .authority("com.beeperproxy.provider")
            .appendPath("messages")
            .appendQueryParameter("authToken", token)
            .appendQueryParameter("roomId", roomId)
            .appendQueryParameter("text", text)
            .build()

        return try {
            val result = contentResolver.insert(uri, null)
            if (result != null) {
                "✓ Message sent\nResult URI: $result"
            } else {
                "✗ Insert returned null\nCheck room ID and permissions."
            }
        } catch (e: Exception) {
            "✗ ${e.javaClass.simpleName}\n${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        testJob?.cancel()
    }
}
