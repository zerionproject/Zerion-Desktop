package chat.zerion.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import chat.zerion.desktop.ZerionDesktopComponent
import chat.zerion.desktop.ui.components.TorUiState
import chat.zerion.desktop.ui.voice.VoiceMemo
import chat.zerion.desktop.ui.theme.ThemeMode
import chat.zerion.desktop.ui.voice.VoiceCallEngine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

import org.zerionproject.app.api.avatar.event.AvatarUpdatedEvent
import org.zerionproject.app.api.channel.event.ChannelPostReceivedEvent
import org.zerionproject.app.api.channel.event.ChannelStateChangedEvent
import org.zerionproject.app.api.conversation.ConversationMessageHeader
import org.zerionproject.app.api.messaging.PrivateMessageFormat
import org.zerionproject.app.api.messaging.PrivateMessageHeader
import org.zerionproject.core.api.sync.GroupId
import org.zerionproject.app.api.messaging.event.GroupMembershipChangedEvent
import org.zerionproject.app.api.messaging.event.GroupPostReceivedEvent
import org.zerionproject.app.api.messaging.event.GroupTrInviteOfferReceivedEvent
import org.zerionproject.app.api.messaging.event.GroupTrLocalStateChangedEvent
import org.zerionproject.app.api.messaging.event.GroupTrSelfRemovedEvent
import org.zerionproject.app.api.messaging.event.PrivateMessageReceivedEvent
import org.zerionproject.app.api.messaging.event.VoiceSignalReceivedEvent
import org.zerionproject.core.api.contact.ContactId
import org.zerionproject.core.api.contact.ContactType
import org.zerionproject.core.api.contact.event.ContactAddedEvent
import org.zerionproject.core.api.contact.event.ContactAliasChangedEvent
import org.zerionproject.core.api.contact.event.ContactRemovedEvent
import org.zerionproject.core.api.contact.event.PendingContactAddedEvent
import org.zerionproject.core.api.contact.event.PendingContactRemovedEvent
import org.zerionproject.core.api.contact.event.PendingContactStateChangedEvent
import org.zerionproject.core.api.event.Event
import org.zerionproject.core.api.event.EventListener
import org.zerionproject.core.api.plugin.Plugin
import org.zerionproject.core.api.plugin.TorConstants
import org.zerionproject.core.api.plugin.event.ContactConnectedEvent
import org.zerionproject.core.api.plugin.event.ContactDisconnectedEvent
import org.zerionproject.core.api.plugin.event.TransportStateEvent
import org.zerionproject.core.api.sync.event.MessagesAckedEvent
import org.zerionproject.core.api.sync.event.MessagesSentEvent

import java.io.ByteArrayInputStream
import java.io.File

/**
 * Holds all UI-facing state for the running app and drives the engine managers.
 *
 * Threading: manager calls do blocking DB I/O and are always run on
 * [Dispatchers.IO]; every state mutation happens back on the Swing (UI) thread.
 * Engine events arrive on the event thread and are marshalled onto [scope]
 * (Swing) before touching state. [close] tears everything down - it removes the
 * event listener, cancels the scope and stops the engine - so nothing leaks
 * when the window closes.
 */
class ZerionModel(
		private val component: ZerionDesktopComponent,
		private val dataDir: File,
) {

	data class ContactItem(
			val id: ContactId,
			val name: String,
			val colorKey: Int,
			val connected: Boolean,
			val unread: Int,
			val verified: Boolean,
			val postQuantum: Boolean,
			val avatar: ByteArray? = null,
	)

	data class UiAttachment(
			val header: org.zerionproject.app.api.attachment.AttachmentHeader,
			val contentType: String,
			val isImage: Boolean,
			val fileName: String,
			val bytes: ByteArray? = null,
			val size: Long = -1L,
	)

	data class UiMessage(
			val id: String,
			val msgId: org.zerionproject.core.api.sync.MessageId,
			val text: String,
			val outgoing: Boolean,
			val timestamp: Long,
			val sent: Boolean,
			val seen: Boolean,
			val attachments: List<UiAttachment> = emptyList(),
			val replyToId: org.zerionproject.core.api.sync.MessageId? = null,
			val replyPreview: String? = null,
			val reactions: Map<String, Int> = emptyMap(),
			val voice: UiVoice? = null,
	) {
		val images: List<ByteArray>
			get() = attachments.mapNotNull { if (it.isImage) it.bytes else null }
	}

	data class UiVoice(
			val durationMs: Int,
			val muLaw: ByteArray?,
			val incomplete: Boolean = false,
	)

	data class PendingItem(
			val id: org.zerionproject.core.api.contact.PendingContactId,
			val name: String,
			val state: String,
	)

	data class GroupItem(
			val id: ByteArray,
			val idHex: String,
			val name: String,
			val members: Int,
			val unread: Int,
			val isCreator: Boolean = false,
	)

	data class GroupPost(
			val id: String,
			val sender: String,
			val text: String,
			val timestamp: Long,
			val outgoing: Boolean,
			val image: ByteArray? = null,
			val voiceOgg: ByteArray? = null,
			val voiceDurationMs: Int = 0,
	)

	data class GroupMemberItem(
			val name: String,
			val role: String,
			val pubKey: ByteArray,
			val isSelf: Boolean,
			val isCreator: Boolean,
	)

	data class GroupInvite(
			val id: ByteArray,
			val idHex: String,
			val name: String,
			val from: String,
	)

	data class ChannelItem(
			val id: ByteArray,
			val idHex: String,
			val name: String,
			val description: String,
			val publisher: Boolean,
			val unread: Int,
	)

	data class ChannelPostItem(
			val id: String,
			val body: String,
			val timestamp: Long,
			val images: List<ByteArray>,
	)

	var localName by mutableStateOf("")
		private set
	var myAvatar by mutableStateOf<ByteArray?>(null)
		private set
	var torState by mutableStateOf(TorUiState.CONNECTING)
		private set
	var contacts by mutableStateOf<List<ContactItem>>(emptyList())
		private set
	var pending by mutableStateOf<List<PendingItem>>(emptyList())
		private set
	var selectedId by mutableStateOf<ContactId?>(null)
		private set
	var messages by mutableStateOf<List<UiMessage>>(emptyList())
		private set
	var myLink by mutableStateOf<String?>(null)
		private set
	var sendError by mutableStateOf<String?>(null)
		private set
	var themeMode by mutableStateOf(ThemeMode.DARK)
		private set
	var offlineMode by mutableStateOf(false)
		private set
	var notificationsEnabled by mutableStateOf(true)
	var notifyPrivate by mutableStateOf(true)
	var notifyGroups by mutableStateOf(true)
	var notifyChannels by mutableStateOf(true)
	var notifySound by mutableStateOf(true)
		private set
	var callsEnabled by mutableStateOf(false)
	var micDevice by mutableStateOf<String?>(null)
	var speakerDevice by mutableStateOf<String?>(null)
	var defaultDisappearingMs by mutableStateOf(-1L)
		private set
	var i2pEnabled by mutableStateOf(false)
	var torNetworkMode by mutableStateOf(TorConstants.PREF_TOR_NETWORK_AUTOMATIC)
	var customBridges by mutableStateOf("")
		private set
	var i2pState by mutableStateOf(TorUiState.OFFLINE)
		private set
	var hasDuress by mutableStateOf(false)
		private set
	var lockedChatIds by mutableStateOf<Set<Int>>(emptySet())
		private set
	var unlockedChatIds by mutableStateOf<Set<Int>>(emptySet())
		private set
	var locksLoaded by mutableStateOf(false)
		private set
	var conversationTimer by mutableStateOf(-1L)
		private set
	var timerSupported by mutableStateOf(false)
		private set
	var groups by mutableStateOf<List<GroupItem>>(emptyList())
		private set
	var groupInvites by mutableStateOf<List<GroupInvite>>(emptyList())
		private set
	var selectedGroupHex by mutableStateOf<String?>(null)
		private set
	var groupPosts by mutableStateOf<List<GroupPost>>(emptyList())
		private set
	var groupMembers by mutableStateOf<List<GroupMemberItem>>(emptyList())
		private set

	val selectedGroup: GroupItem?
		get() = groups.firstOrNull { it.idHex == selectedGroupHex }

	var channels by mutableStateOf<List<ChannelItem>>(emptyList())
		private set
	var selectedChannelHex by mutableStateOf<String?>(null)
		private set
	var channelPosts by mutableStateOf<List<ChannelPostItem>>(emptyList())
		private set
	var channelRefreshing by mutableStateOf(false)
		private set

	val selectedChannel: ChannelItem?
		get() = channels.firstOrNull { it.idHex == selectedChannelHex }

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

	private val listener = EventListener { e -> onEvent(e) }

	private val notifier = DesktopNotifier()

	val call = VoiceCallEngine(component)

	val vault = chat.zerion.desktop.ui.vault.VaultModel(
			File(dataDir, "vault"), runCatching { component.torSocksPort() }.getOrDefault(0))

	private var myPubKey: ByteArray? = null

	fun start() {
		component.eventBus().addListener(listener)
		notifier.install()
		hasDuress = DesktopProfiles.hasDuress(dataDir)
		offlineMode = component.pluginManager().isOfflineMode
		refreshTorState()
		refreshI2pState()
		scope.launch {
			myPubKey = io {
				component.identityManager().localAuthor.publicKey.encoded
			}
			localName = io { component.identityManager().localAuthor.name }
					?: "Zerion"
			themeMode = io {
				val settings = component.settingsManager()
						.getSettings(UI_NAMESPACE)
				notificationsEnabled = settings.getBoolean(KEY_NOTIFY, true)
				notifyPrivate = settings.getBoolean(KEY_NOTIFY_PRIVATE, true)
				notifyGroups = settings.getBoolean(KEY_NOTIFY_GROUPS, true)
				notifyChannels = settings.getBoolean(KEY_NOTIFY_CHANNELS, true)
				notifySound = settings.getBoolean(KEY_NOTIFY_SOUND, true)
				callsEnabled = settings.getBoolean(KEY_CALLS, false)
				micDevice = settings.get(KEY_MIC_DEVICE)?.ifBlank { null }
				speakerDevice = settings.get(KEY_SPEAKER_DEVICE)?.ifBlank { null }
				chat.zerion.desktop.ui.voice.AudioDevice.preferredInput = micDevice
				chat.zerion.desktop.ui.voice.AudioDevice.preferredOutput =
						speakerDevice
				defaultDisappearingMs =
						settings.get(KEY_DEFAULT_TIMER)?.toLongOrNull() ?: -1L
				i2pEnabled = component.settingsManager().getSettings(I2P_NS)
						.getBoolean("enable", false)
				val torSettings = component.settingsManager()
						.getSettings(TorConstants.ID.string)
				torNetworkMode = torSettings.getInt(
						TorConstants.PREF_TOR_NETWORK,
						TorConstants.PREF_TOR_NETWORK_AUTOMATIC)
				customBridges = torSettings.get(
						TorConstants.PREF_TOR_CUSTOM_BRIDGES) ?: ""
				val mode = settings.getInt(KEY_THEME, ThemeMode.DARK.ordinal)
				ThemeMode.entries.getOrElse(mode) { ThemeMode.DARK }
			} ?: ThemeMode.DARK
			if (!loadLocks()) scope.launch { retryLoadLocks() }
		}
		reloadContacts()
		reloadPending()
		reloadGroups()
		reloadGroupInvites()
		reloadChannels()
		loadMyAvatar()
	}

	private suspend fun loadLocks(): Boolean {
		val loaded = io {
			val locks = component.settingsManager().getSettings(LOCK_NS)
			locks.entries.filter { it.value.isNotEmpty() }
					.mapNotNull { it.key.toIntOrNull() }.toSet()
		} ?: return false
		lockedChatIds = loaded
		locksLoaded = true
		return true
	}

	private suspend fun retryLoadLocks() {
		var backoff = 250L
		while (!locksLoaded) {
			kotlinx.coroutines.delay(backoff)
			if (loadLocks()) return
			backoff = (backoff * 2).coerceAtMost(5000L)
		}
	}

	private fun loadMyAvatar() {
		scope.launch {
			myAvatar = io {
				component.db().transactionWithResult<ByteArray?, Exception>(
						true) { txn ->
					val header = component.avatarManager().getMyAvatarHeader(txn)
					if (header == null) null
					else component.attachmentReader().getAttachment(txn, header)
							.stream.use { it.readBytes() }
				}
			}
		}
	}

	fun setMyAvatar(file: File, onResult: (Boolean) -> Unit) {
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					if (file.length() > MAX_SOURCE_BYTES) return@withContext false
					val jpeg = ImageScrubber.scrubToJpeg(file.readBytes())
					component.avatarManager().addAvatar(
							ImageScrubber.OUTPUT_CONTENT_TYPE,
							ByteArrayInputStream(jpeg))
					true
				} catch (e: Exception) {
					false
				}
			}
			if (ok) {
				loadMyAvatar()
				reloadContacts()
			}
			onResult(ok)
		}
	}

	fun isChatVisible(id: ContactId): Boolean =
			locksLoaded && (id.int !in lockedChatIds || id.int in unlockedChatIds)

	fun setChatLock(id: ContactId, password: CharArray, onDone: () -> Unit) {
		scope.launch {
			io {
				val stored = ChatLock.derive(password)
				val settings = org.zerionproject.core.api.settings.Settings()
				settings[id.int.toString()] = stored
				component.settingsManager().mergeSettings(settings, LOCK_NS)
			}
			java.util.Arrays.fill(password, ' ')
			lockedChatIds = lockedChatIds + id.int
			unlockedChatIds = unlockedChatIds + id.int
			onDone()
		}
	}

	fun unlockChat(id: ContactId, password: CharArray,
			onResult: (Boolean) -> Unit) {
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					val stored = component.settingsManager()
							.getSettings(LOCK_NS)[id.int.toString()]
					stored != null && ChatLock.verify(password, stored)
				} catch (e: Exception) {
					false
				} finally {
					java.util.Arrays.fill(password, ' ')
				}
			}
			if (ok) unlockedChatIds = unlockedChatIds + id.int
			onResult(ok)
		}
	}

	fun removeChatLock(id: ContactId, password: CharArray,
			onResult: (Boolean) -> Unit) {
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					val sm = component.settingsManager()
					val stored = sm.getSettings(LOCK_NS)[id.int.toString()]
					if (stored == null || !ChatLock.verify(password, stored)) {
						false
					} else {
						val settings =
								org.zerionproject.core.api.settings.Settings()
						settings[id.int.toString()] = ""
						sm.mergeSettings(settings, LOCK_NS)
						true
					}
				} catch (e: Exception) {
					false
				} finally {
					java.util.Arrays.fill(password, ' ')
				}
			}
			if (ok) {
				lockedChatIds = lockedChatIds - id.int
				unlockedChatIds = unlockedChatIds - id.int
			}
			onResult(ok)
		}
	}

	fun relockChat(id: ContactId) {
		unlockedChatIds = unlockedChatIds - id.int
	}


	fun selectGroup(item: GroupItem) {
		selectedGroupHex = item.idHex
		selectedId = null
		selectedChannelHex = null
		messages = emptyList()
		sendError = null
		groupPosts = emptyList()
		channelPosts = emptyList()
		groupMembers = emptyList()
		loadGroupPosts(item.id)
		loadGroupMembers(item.id)
	}

	fun createGroup(name: String, onResult: (Boolean) -> Unit) {
		val clean = sanitizeName(name)
		if (clean.isEmpty()) return onResult(false)
		scope.launch {
			val ok = io {
				component.groupTrManager().createGroup(clean); true
			} ?: false
			if (ok) reloadGroups()
			onResult(ok)
		}
	}

	fun sendGroupMessage(text: String, onResult: (Boolean) -> Unit) {
		val group = selectedGroup ?: return onResult(false)
		val trimmed = text.trim()
		if (trimmed.isEmpty()) return onResult(false)
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					component.groupTrManager().sendGroupPost(group.id,
							trimmed.toByteArray(Charsets.UTF_8), 0L)
					true
				} catch (e: Exception) {
					false
				}
			}
			if (ok) {
				sendError = null
				loadGroupPosts(group.id)
			} else {
				sendError = "Message not sent. Check your connection and " +
						"try again."
			}
			onResult(ok)
		}
	}

	fun inviteToGroup(groupId: ByteArray, contactId: ContactId,
			onResult: (Boolean) -> Unit) {
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					val contact = component.contactManager().getContact(contactId)
					component.groupTrManager().inviteContactToGroup(groupId,
							contactId, contact.author.publicKey.encoded,
							contact.author.name)
					true
				} catch (e: Exception) {
					false
				}
			}
			onResult(ok)
		}
	}

	fun acceptGroupInvite(groupId: ByteArray) {
		scope.launch {
			io { component.groupTrManager().acceptInvite(groupId) }
			reloadGroups()
			reloadGroupInvites()
		}
	}

	fun declineGroupInvite(groupId: ByteArray) {
		scope.launch {
			io { component.groupTrManager().declineInvite(groupId) }
			reloadGroupInvites()
		}
	}

	fun leaveGroup(groupId: ByteArray) {
		scope.launch {
			io { component.groupTrManager().leaveGroup(groupId) }
			if (selectedGroupHex == groupId.hex()) {
				selectedGroupHex = null
				groupPosts = emptyList()
				groupMembers = emptyList()
			}
			reloadGroups()
		}
	}

	private fun reloadGroups() {
		scope.launch {
			val me = myPubKey
			groups = io {
				val gm = component.groupTrManager()
				gm.groups.filter { !it.isDissolved }.map { g ->
					val creator = me != null && try {
						gm.isCreator(g.groupId, me)
					} catch (e: Exception) {
						false
					}
					GroupItem(g.groupId, g.groupId.hex(), g.name,
							g.members.size, gm.getUnreadCount(g.groupId),
							isCreator = creator)
				}.sortedBy { it.name.lowercase() }
			} ?: emptyList()
		}
	}

	fun promoteMember(groupId: ByteArray, pubKey: ByteArray) =
			groupAdmin(groupId) {
				component.groupTrManager().promoteToAdmin(groupId, pubKey)
			}

	fun demoteMember(groupId: ByteArray, pubKey: ByteArray) =
			groupAdmin(groupId) {
				component.groupTrManager().demoteToMember(groupId, pubKey)
			}

	fun removeGroupMember(groupId: ByteArray, pubKey: ByteArray) =
			groupAdmin(groupId) {
				component.groupTrManager().removeMember(groupId, pubKey)
			}

	fun dissolveGroup(groupId: ByteArray) {
		scope.launch {
			io { component.groupTrManager().dissolveGroup(groupId) }
			if (selectedGroupHex == groupId.hex()) {
				selectedGroupHex = null
				groupPosts = emptyList()
				groupMembers = emptyList()
			}
			reloadGroups()
		}
	}

	private fun groupAdmin(groupId: ByteArray, action: () -> Unit) {
		scope.launch {
			io { action() }
			if (selectedGroupHex == groupId.hex()) loadGroupMembers(groupId)
			reloadGroups()
		}
	}

	private fun reloadGroupInvites() {
		scope.launch {
			groupInvites = io {
				component.groupTrManager().pendingInvites.map { inv ->
					GroupInvite(inv.groupId, inv.groupId.hex(), inv.groupName,
							inv.creatorName)
				}
			} ?: emptyList()
		}
	}

	fun sendGroupImage(file: File, onResult: (Boolean) -> Unit) {
		val group = selectedGroup ?: return onResult(false)
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					if (file.length() > MAX_SOURCE_BYTES) return@withContext false
					val jpeg = ImageScrubber.scrubToJpeg(file.readBytes())
					val body = org.zerionproject.app.api.grouptr.GroupTrBody
							.encodeImage(jpeg, ImageScrubber.OUTPUT_CONTENT_TYPE)
					component.groupTrManager()
							.sendGroupPost(group.id, body, 0L)
					true
				} catch (e: Exception) {
					false
				}
			}
			if (ok) {
				sendError = null
				loadGroupPosts(group.id)
			} else {
				sendError = "Couldn't send the image."
			}
			onResult(ok)
		}
	}

	fun sendGroupVoice(pcm: ByteArray, onResult: (Boolean) -> Unit) {
		val group = selectedGroup ?: return onResult(false)
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					if (pcm.isEmpty()) return@withContext false
					val opus = chat.zerion.desktop.ui.voice.GroupVoice.encode(pcm)
					val durationMs = chat.zerion.desktop.ui.voice.GroupVoice
							.durationMsForPcm(pcm).toLong()
					val body = org.zerionproject.app.api.grouptr.GroupTrBody
							.encodeVoice(opus, durationMs)
					component.groupTrManager().sendGroupPost(group.id, body, 0L)
					true
				} catch (e: Exception) {
					false
				}
			}
			if (ok) { sendError = null; loadGroupPosts(group.id) }
			else sendError = "Couldn't send the voice message."
			onResult(ok)
		}
	}

	fun decodeGroupVoice(ogg: ByteArray, onResult: (ByteArray?) -> Unit) {
		scope.launch {
			onResult(io { chat.zerion.desktop.ui.voice.GroupVoice.decode(ogg) })
		}
	}

	private fun loadGroupPosts(id: ByteArray) {
		val hex = id.hex()
		scope.launch {
			val posts = io {
				component.groupTrManager().getRecentPosts(id)
						.mapIndexed { i, p ->
							val parsed = org.zerionproject.app.api.grouptr
									.GroupTrBody.parse(p.body)
							var text = ""
							var image: ByteArray? = null
							var voiceOgg: ByteArray? = null
							var voiceMs = 0
							when (parsed.kind.name) {
								"IMAGE" -> image = parsed.payload
								"VOICE" -> {
									voiceOgg = parsed.payload
									voiceMs = parsed.durationMs.toInt()
								}
								"VIDEO" -> text = "🎬 Video"
								else -> text = parsed.text
							}
							GroupPost("$i:${p.timestamp}", p.senderName, text,
									p.timestamp, p.isLocal, image, voiceOgg, voiceMs)
						}
			} ?: emptyList()
			if (selectedGroupHex == hex) groupPosts = posts
			io { component.groupTrManager().markGroupRead(id) }
			reloadGroups()
		}
	}

	private fun loadGroupMembers(id: ByteArray) {
		val hex = id.hex()
		scope.launch {
			val me = myPubKey
			val members = io {
				component.groupTrManager().getGroup(id)?.members?.map { m ->
					GroupMemberItem(
							name = m.name,
							role = m.role.name.lowercase()
									.replaceFirstChar { it.uppercase() },
							pubKey = m.pubKey,
							isSelf = me != null && m.pubKey.contentEquals(me),
							isCreator = m.role ==
									org.zerionproject.app.api.grouptr.MemberRole
											.CREATOR)
				} ?: emptyList()
			} ?: emptyList()
			if (selectedGroupHex == hex) groupMembers = members
		}
	}


	fun selectChannel(item: ChannelItem) {
		selectedChannelHex = item.idHex
		selectedId = null
		selectedGroupHex = null
		messages = emptyList()
		groupPosts = emptyList()
		channelPosts = emptyList()
		sendError = null
		loadChannelPosts(item.id, refresh = true)
	}

	fun createChannel(name: String, description: String, publicChannel: Boolean,
			onResult: (Boolean) -> Unit) {
		val clean = sanitizeName(name)
		if (clean.isEmpty()) return onResult(false)
		scope.launch {
			val ok = io {
				component.channelManager().createChannel(clean,
						sanitizeName(description, 1024), publicChannel, false); true
			} ?: false
			if (ok) reloadChannels()
			onResult(ok)
		}
	}

	fun joinChannelFromLink(url: String, onResult: (String?) -> Unit) {
		scope.launch {
			val error = withContext(Dispatchers.IO) {
				try {
					val cm = component.channelManager()
					val link = cm.parseInviteLink(url.trim())
							?: return@withContext "That isn't a valid channel " +
									"link."
					if (cm.getChannel(link.channelId) != null) {
						return@withContext "You're already subscribed to this " +
								"channel."
					}
					cm.joinChannel(link)
					cm.bootstrapChannel(link.channelId)
					null
				} catch (e: Exception) {
					e.message ?: "Couldn't join the channel."
				}
			}
			if (error == null) reloadChannels()
			onResult(error)
		}
	}

	fun publishChannelText(text: String, onResult: (Boolean) -> Unit) {
		val channel = selectedChannel ?: return onResult(false)
		val trimmed = text.trim()
		if (trimmed.isEmpty()) return onResult(false)
		scope.launch {
			val ok = io {
				component.channelManager()
						.publishPost(channel.id, trimmed, 0L); true
			} ?: false
			if (ok) loadChannelPosts(channel.id, refresh = false)
			else sendError = "Couldn't publish the post."
			onResult(ok)
		}
	}

	fun publishChannelImage(file: File, onResult: (Boolean) -> Unit) {
		val channel = selectedChannel ?: return onResult(false)
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					if (file.length() > MAX_SOURCE_BYTES) return@withContext false
					val jpeg = ImageScrubber.scrubToJpeg(file.readBytes())
					val spec = org.zerionproject.app.api.channel.AttachmentSpec(
							ImageScrubber.OUTPUT_CONTENT_TYPE, jpeg, null)
					component.channelManager().publishPostWithAttachments(
							channel.id, "", 0L, listOf(spec))
					true
				} catch (e: Exception) {
					false
				}
			}
			if (ok) loadChannelPosts(channel.id, refresh = false)
			else sendError = "Couldn't publish the image."
			onResult(ok)
		}
	}

	fun exportChannelLink(id: ByteArray, onResult: (String?) -> Unit) {
		scope.launch {
			onResult(io { component.channelManager().exportInviteLink(id) })
		}
	}

	fun refreshChannel(id: ByteArray) {
		channelRefreshing = true
		scope.launch {
			io { component.channelManager().refreshChannel(id) }
			loadChannelPosts(id, refresh = false)
			channelRefreshing = false
		}
	}

	fun leaveChannel(id: ByteArray) {
		scope.launch {
			io { component.channelManager().leaveChannel(id) }
			clearChannelSelection(id)
			reloadChannels()
		}
	}

	fun deleteChannel(id: ByteArray) {
		scope.launch {
			io { component.channelManager().deleteChannel(id) }
			clearChannelSelection(id)
			reloadChannels()
		}
	}


	data class ChannelCommentItem(
			val commentId: Long,
			val author: String,
			val body: String,
			val timestamp: Long,
	)

	data class ChannelSubscriberItem(
			val name: String,
			val ed25519: ByteArray,
			val mlDsa: ByteArray,
	)

	data class ChannelApplicationItem(
			val name: String,
			val ed25519: ByteArray,
	)

	data class ChannelDelegationItem(
			val name: String,
			val delegationSeq: Long,
	)

	fun loadChannelComments(channelId: ByteArray, parentSeq: Long,
			onResult: (List<ChannelCommentItem>) -> Unit) {
		scope.launch {
			val list = io {
				component.channelManager().getComments(channelId, parentSeq)
						.map {
							ChannelCommentItem(it.commentId, it.authorDisplayName,
									it.body, it.timestampHourMs)
						}.sortedBy { it.timestamp }
			} ?: emptyList()
			onResult(list)
		}
	}

	fun postChannelComment(channelId: ByteArray, parentSeq: Long, body: String,
			onResult: (Boolean) -> Unit) {
		val clean = body.trim()
		if (clean.isEmpty()) return onResult(false)
		scope.launch {
			val ok = io {
				try {
					component.channelManager()
							.postComment(channelId, parentSeq, clean)
					true
				} catch (e: Exception) {
					false
				}
			} ?: false
			onResult(ok)
		}
	}

	fun reactToChannelPost(channelId: ByteArray, seqNum: Long, emoji: String) {
		scope.launch {
			io {
				try {
					component.channelManager()
							.reactToPost(channelId, seqNum, emoji)
				} catch (e: Exception) {
				}
			}
		}
	}

	fun loadChannelSubscribers(channelId: ByteArray,
			onResult: (List<ChannelSubscriberItem>) -> Unit) {
		scope.launch {
			val list = io {
				component.channelManager().getAnnouncedSubscribers(channelId)
						.map {
							ChannelSubscriberItem(it.displayName,
									it.ed25519PubKey, it.mlDsaPubKey)
						}
			} ?: emptyList()
			onResult(list)
		}
	}

	fun banChannelSubscriber(channelId: ByteArray, ed25519: ByteArray,
			onDone: () -> Unit) {
		scope.launch {
			io {
				try {
					component.channelManager().banSubscriber(channelId, ed25519)
				} catch (e: Exception) {
				}
			}
			onDone()
		}
	}

	fun loadChannelApplications(channelId: ByteArray,
			onResult: (List<ChannelApplicationItem>) -> Unit) {
		scope.launch {
			val list = io {
				component.channelManager().listPendingApplications(channelId)
						.map {
							ChannelApplicationItem(it.displayName,
									it.applicantEd25519)
						}
			} ?: emptyList()
			onResult(list)
		}
	}

	fun approveChannelApplication(channelId: ByteArray, ed25519: ByteArray,
			onDone: () -> Unit) {
		scope.launch {
			io {
				try {
					component.channelManager()
							.approveApplication(channelId, ed25519)
				} catch (e: Exception) {
				}
			}
			onDone()
		}
	}

	fun denyChannelApplication(channelId: ByteArray, ed25519: ByteArray,
			onDone: () -> Unit) {
		scope.launch {
			io {
				try {
					component.channelManager()
							.denyApplication(channelId, ed25519)
				} catch (e: Exception) {
				}
			}
			onDone()
		}
	}

	fun loadChannelDelegations(channelId: ByteArray,
			onResult: (List<ChannelDelegationItem>) -> Unit) {
		scope.launch {
			val list = io {
				component.channelManager().listActiveDelegations(channelId)
						.map {
							ChannelDelegationItem(
									it.delegateeEd25519PubKey.hex().take(12),
									it.delegationSeq)
						}
			} ?: emptyList()
			onResult(list)
		}
	}

	fun delegateChannelPublisher(channelId: ByteArray, ed25519: ByteArray,
			mlDsa: ByteArray, onDone: () -> Unit) {
		scope.launch {
			io {
				try {
					component.channelManager()
							.delegatePublisher(channelId, ed25519, mlDsa, 0L)
				} catch (e: Exception) {
				}
			}
			onDone()
		}
	}

	fun revokeChannelDelegation(channelId: ByteArray, delegationSeq: Long,
			onDone: () -> Unit) {
		scope.launch {
			io {
				try {
					component.channelManager()
							.revokeDelegation(channelId, delegationSeq)
				} catch (e: Exception) {
				}
			}
			onDone()
		}
	}

	fun rotateChannelJoinKey(channelId: ByteArray, onDone: () -> Unit) {
		scope.launch {
			io {
				try {
					component.channelManager().rotateJoinCapability(channelId)
				} catch (e: Exception) {
				}
			}
			onDone()
		}
	}

	private fun clearChannelSelection(id: ByteArray) {
		if (selectedChannelHex == id.hex()) {
			selectedChannelHex = null
			channelPosts = emptyList()
		}
	}

	private fun reloadChannels() {
		scope.launch {
			channels = io {
				val cm = component.channelManager()
				cm.channels.map { c ->
					ChannelItem(c.channelId, c.channelId.hex(), c.name,
							c.description, c.weArePublisher(),
							cm.getUnreadCount(c.channelId))
				}.sortedBy { it.name.lowercase() }
			} ?: emptyList()
		}
	}

	private fun loadChannelPosts(id: ByteArray, refresh: Boolean) {
		val hex = id.hex()
		scope.launch {
			if (refresh) {
				channelRefreshing = true
				io { component.channelManager().refreshChannel(id) }
				channelRefreshing = false
			}
			val posts = io {
				val cm = component.channelManager()
				cm.getRecentPosts(id, 500L).map { p ->
					val images = p.attachments
							.filter { it.mimeType.startsWith("image/") }
							.mapNotNull { att ->
								try {
									cm.fetchAttachment(id, p.seqNum, att.blobHash)
											?.plaintextBytes
								} catch (e: Exception) {
									null
								}
							}
					ChannelPostItem("${p.seqNum}", p.body.trim(),
							p.timestampHourMs, images)
				}.sortedBy { it.timestamp }
			} ?: emptyList()
			if (selectedChannelHex == hex) channelPosts = posts
			io { component.channelManager().markChannelRead(id) }
			reloadChannels()
		}
	}

	private fun ByteArray.hex(): String =
			joinToString("") { "%02x".format(it) }

	fun close() {
		component.eventBus().removeListener(listener)
		notifier.remove()
		call.shutdown()
		vault.shutdown()
		scope.cancel()
		val lm = component.lifecycleManager()
		Thread {
			try {
				lm.stopServices()
				lm.waitForShutdown()
			} catch (ignored: Exception) {
			}
		}.apply { isDaemon = true; name = "zerion-shutdown" }.start()
	}

	fun shutdownBlocking() {
		component.eventBus().removeListener(listener)
		notifier.remove()
		call.shutdown()
		vault.shutdown()
		scope.cancel()
		try {
			val lm = component.lifecycleManager()
			lm.stopServices()
			lm.waitForShutdown()
		} catch (ignored: Exception) {
		}
	}

	fun startVoiceCall(id: ContactId) {
		if (!callsEnabled) return
		val name = contacts.firstOrNull { it.id == id }?.name ?: "Contact"
		call.startCall(id, name)
	}

	fun inputDevices(): List<String> =
			chat.zerion.desktop.ui.voice.AudioDevice.inputDevices()

	fun outputDevices(): List<String> =
			chat.zerion.desktop.ui.voice.AudioDevice.outputDevices()

	fun applyMicDevice(name: String?) {
		micDevice = name
		chat.zerion.desktop.ui.voice.AudioDevice.preferredInput = name
		persistDevice(KEY_MIC_DEVICE, name)
	}

	fun applySpeakerDevice(name: String?) {
		speakerDevice = name
		chat.zerion.desktop.ui.voice.AudioDevice.preferredOutput = name
		persistDevice(KEY_SPEAKER_DEVICE, name)
	}

	private fun persistDevice(key: String, name: String?) {
		scope.launch {
			io {
				val settings = org.zerionproject.core.api.settings.Settings()
				settings.put(key, name ?: "")
				component.settingsManager().mergeSettings(settings, UI_NAMESPACE)
			}
		}
	}

	fun applyCallsEnabled(enabled: Boolean) {
		callsEnabled = enabled
		if (!enabled && call.inCall) call.hangUp()
		scope.launch {
			io {
				val settings = org.zerionproject.core.api.settings.Settings()
				settings.putBoolean(KEY_CALLS, enabled)
				component.settingsManager().mergeSettings(settings, UI_NAMESPACE)
			}
		}
	}

	fun selectContact(id: ContactId) {
		selectedId = id
		selectedGroupHex = null
		selectedChannelHex = null
		groupPosts = emptyList()
		channelPosts = emptyList()
		sendError = null
		conversationTimer = NO_TIMER
		timerSupported = false
		reloadMessages(id)
		markContactRead(id)
		loadTimer(id)
	}

	private fun loadTimer(id: ContactId) {
		scope.launch {
			val info = io {
				component.db().transactionWithResult<
						Pair<Boolean, Long>, Exception>(true) { txn ->
					val fmt = component.messagingManager()
							.getContactMessageFormat(txn, id)
					val supported = fmt ==
							PrivateMessageFormat.TEXT_IMAGES_AUTO_DELETE ||
							fmt == PrivateMessageFormat.TEXT_IMAGES_CHUNKED
					val timer = if (supported) component.autoDeleteManager()
							.getAutoDeleteTimer(txn, id) else NO_TIMER
					Pair(supported, timer)
				}
			}
			if (selectedId == id) {
				timerSupported = info?.first ?: false
				conversationTimer = info?.second ?: NO_TIMER
			}
		}
	}

	fun setConversationTimer(id: ContactId, timerMs: Long) {
		conversationTimer = timerMs
		scope.launch {
			io {
				component.db().transaction<Exception>(false) { txn ->
					component.autoDeleteManager()
							.setAutoDeleteTimer(txn, id, timerMs)
				}
			}
		}
	}

	fun applyDefaultTimer(timerMs: Long) {
		defaultDisappearingMs = timerMs
		scope.launch {
			io {
				val settings = org.zerionproject.core.api.settings.Settings()
				settings.put(KEY_DEFAULT_TIMER, timerMs.toString())
				component.settingsManager().mergeSettings(settings, UI_NAMESPACE)
			}
		}
	}

	private fun applyDefaultTimerTo(id: ContactId) {
		if (defaultDisappearingMs <= 0L) return
		scope.launch {
			io {
				component.db().transaction<Exception>(false) { txn ->
					component.autoDeleteManager()
							.setAutoDeleteTimer(txn, id, defaultDisappearingMs)
				}
			}
		}
	}

	private class AttachmentSpec(
			val contentType: String,
			val bytes: ByteArray,
			val chunked: Boolean,
	)

	fun sendMessage(text: String, onResult: (Boolean) -> Unit) {
		val id = selectedId ?: return onResult(false)
		val trimmed = text.trim()
		if (trimmed.isEmpty()) return onResult(false)
		sendInternal(id, trimmed, null, null, onResult)
	}

	fun sendReply(text: String, replyTo: org.zerionproject.core.api.sync.MessageId,
			onResult: (Boolean) -> Unit) {
		val id = selectedId ?: return onResult(false)
		val trimmed = text.trim()
		if (trimmed.isEmpty()) return onResult(false)
		sendInternal(id, trimmed, null, replyTo, onResult)
	}

	fun sendVoiceMemo(pcm: ByteArray, durationMs: Int,
			onResult: (Boolean) -> Unit) {
		val id = selectedId ?: return onResult(false)
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					val db = component.db()
					val mm = component.messagingManager()
					val groupBytes = db.transactionWithResult<ByteArray, Exception>(
							true) { txn -> mm.getConversationId(txn, id).bytes }
					val parts = chat.zerion.desktop.ui.voice.VoiceMemo
							.buildMessages(pcm, durationMs, groupBytes)
					for (part in parts) {
						db.transaction<Exception>(false) { txn ->
							val group = mm.getConversationId(txn, id)
							val ts = component.conversationManager()
									.getTimestampForOutgoingMessage(txn, id)
							val pm = component.privateMessageFactory()
									.createLegacyPrivateMessage(group, ts, part)
							mm.addLocalMessage(txn, pm)
						}
					}
					true
				} catch (e: Exception) {
					false
				}
			}
			if (ok) reloadMessages(id)
			onResult(ok)
		}
	}

	fun deleteMessage(msgId: org.zerionproject.core.api.sync.MessageId) {
		val id = selectedId ?: return
		scope.launch {
			io {
				component.conversationManager()
						.deleteMessages(id, listOf(msgId))
			}
			reloadMessages(id)
		}
	}

	fun addReaction(msgId: org.zerionproject.core.api.sync.MessageId,
			emoji: String) {
		val id = selectedId ?: return
		scope.launch {
			io { component.messagingManager().addLocalReaction(id, msgId, emoji) }
			reloadMessages(id)
		}
	}

	fun forwardMessage(toContact: ContactId, text: String,
			onResult: (Boolean) -> Unit) {
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					val db = component.db()
					val mm = component.messagingManager()
					db.transaction<Exception>(false) { txn ->
						val group = mm.getConversationId(txn, toContact)
						val ts = component.conversationManager()
								.getTimestampForOutgoingMessage(txn, toContact)
						val pm = component.privateMessageFactory()
								.createLegacyPrivateMessage(group, ts, text)
						mm.addLocalMessage(txn, pm)
					}
					true
				} catch (e: Exception) {
					false
				}
			}
			if (ok && toContact == selectedId) reloadMessages(toContact)
			onResult(ok)
		}
	}

	private fun sendInternal(
			id: ContactId,
			text: String,
			spec: AttachmentSpec?,
			replyTo: org.zerionproject.core.api.sync.MessageId?,
			onResult: (Boolean) -> Unit,
	) {
		scope.launch {
			val error = withContext(Dispatchers.IO) {
				try {
					val db = component.db()
					val mm = component.messagingManager()
					val group = db.transactionWithResult<GroupId, Exception>(
							false) { txn -> mm.getConversationId(txn, id) }
					val ts = db.transactionWithResult<Long, Exception>(
							false) { txn ->
						component.conversationManager()
								.getTimestampForOutgoingMessage(txn, id)
					}
					val format = db.transactionWithResult<
							PrivateMessageFormat, Exception>(true) { txn ->
						mm.getContactMessageFormat(txn, id)
					}
					if (spec != null && !format.supportsImages()) {
						return@withContext "This contact's app is too old to " +
								"receive attachments."
					}
					if (spec != null && spec.chunked &&
							!format.supportsChunkedAttachments()) {
						return@withContext "This contact's app can't receive " +
								"video yet."
					}
					val headers = when {
						spec == null -> emptyList()
						spec.chunked -> listOf(mm.addLocalAttachmentStreaming(
								group, ts, spec.contentType,
								ByteArrayInputStream(spec.bytes),
								spec.bytes.size.toLong(), null))
						else -> listOf(mm.addLocalAttachment(group, ts,
								spec.contentType,
								ByteArrayInputStream(spec.bytes)))
					}
					db.transaction<Exception>(false) { txn ->
						val pmf = component.privateMessageFactory()
						val autoDelete = format ==
								PrivateMessageFormat.TEXT_IMAGES_AUTO_DELETE ||
								format == PrivateMessageFormat.TEXT_IMAGES_CHUNKED
						val pm = when {
							!format.supportsImages() ->
								pmf.createLegacyPrivateMessage(group, ts, text)
							autoDelete -> {
								val timer = component.autoDeleteManager()
										.getAutoDeleteTimer(txn, id, ts)
								pmf.createPrivateMessage(group, ts,
										text.ifEmpty { null }, headers, timer,
										replyTo)
							}
							else -> pmf.createPrivateMessage(group, ts,
									text.ifEmpty { null }, headers)
						}
						mm.addLocalMessage(txn, pm)
					}
					null
				} catch (e: Exception) {
					"Message not sent. Check your connection and try again."
				}
			}
			if (error == null) {
				sendError = null
				reloadMessages(id)
			} else {
				sendError = error
			}
			onResult(error == null)
		}
	}

	fun sendImage(file: File, caption: String, onResult: (Boolean) -> Unit) {
		val id = selectedId ?: return onResult(false)
		scope.launch {
			val spec = withContext(Dispatchers.IO) {
				try {
					if (file.length() > MAX_SOURCE_BYTES) return@withContext null
					AttachmentSpec(ImageScrubber.OUTPUT_CONTENT_TYPE,
							ImageScrubber.scrubToJpeg(file.readBytes()), false)
				} catch (e: Exception) {
					null
				}
			}
			if (spec == null) {
				sendError = "Couldn't prepare the image (unsupported or " +
						"too large)."
				onResult(false)
			} else {
				sendInternal(id, caption.trim(), spec, null, onResult)
			}
		}
	}

	fun sendDocument(file: File, onResult: (Boolean) -> Unit) {
		val id = selectedId ?: return onResult(false)
		scope.launch {
			val spec = withContext(Dispatchers.IO) {
				try {
					if (file.length() > MAX_SOURCE_BYTES) return@withContext null
					val scrubbed = DocScrubber.scrubPdf(file.readBytes())
					if (scrubbed.size > MAX_ATTACHMENT_BYTES) {
						null
					} else {
						AttachmentSpec("application/pdf", scrubbed,
								scrubbed.size > CHUNK_BYTES)
					}
				} catch (e: Exception) {
					null
				}
			}
			if (spec == null) {
				sendError = "Couldn't prepare the document (must be a PDF " +
						"under 10 MB)."
				onResult(false)
			} else {
				sendInternal(id, "", spec, null, onResult)
			}
		}
	}

	fun sendVideo(file: File, onResult: (Boolean) -> Unit) {
		val id = selectedId ?: return onResult(false)
		scope.launch {
			val spec = withContext(Dispatchers.IO) {
				try {
					if (file.length() > MAX_SOURCE_BYTES) return@withContext null
					val scrubbed = VideoScrubber.scrubMp4(file)
					if (scrubbed.size > MAX_ATTACHMENT_BYTES) {
						null
					} else {
						AttachmentSpec("video/mp4", scrubbed, true)
					}
				} catch (e: Exception) {
					null
				}
			}
			if (spec == null) {
				sendError = "Couldn't prepare the video (must be an MP4 " +
						"under 10 MB)."
				onResult(false)
			} else {
				sendInternal(id, "", spec, null, onResult)
			}
		}
	}

	private fun loadAttachments(pm: PrivateMessageHeader): List<UiAttachment> =
			pm.attachmentHeaders.mapIndexed { i, ah ->
				val isImage = ah.contentType.startsWith("image/")
				val bytes = if (isImage) try {
					component.attachmentReader().getAttachment(ah)
							.stream.use { it.readBytes() }
				} catch (e: Exception) {
					null
				} else null
				val size = bytes?.size?.toLong() ?: try {
					component.attachmentReader().getAttachment(ah)
							.stream.use { streamLength(it) }
				} catch (e: Exception) {
					-1L
				}
				UiAttachment(ah, ah.contentType, isImage,
						attachmentFileName(pm, i, ah.contentType), bytes, size)
			}

	private fun streamLength(input: java.io.InputStream): Long {
		val buf = ByteArray(1 shl 16)
		var total = 0L
		while (true) {
			val n = input.read(buf)
			if (n < 0) break
			total += n
		}
		return total
	}

	private fun attachmentFileName(pm: PrivateMessageHeader, idx: Int,
			contentType: String): String {
		val raw = when {
			contentType == "application/pdf" -> "pdf"
			contentType.startsWith("video/") ->
				contentType.substringAfter('/').ifEmpty { "mp4" }
			contentType.startsWith("audio/") ->
				contentType.substringAfter('/').ifEmpty { "m4a" }
			contentType.startsWith("image/") ->
				contentType.substringAfter('/').ifEmpty { "jpg" }
			else -> "bin"
		}
		val ext = raw.filter { it.isLetterOrDigit() }.take(5).ifEmpty { "bin" }
		return "attachment-${pm.id.toString().take(8)}-$idx.$ext"
	}

	fun saveAttachment(att: UiAttachment, dest: File, onResult: (Boolean) -> Unit) {
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					if (att.bytes != null) {
						dest.writeBytes(att.bytes)
					} else {
						component.attachmentReader().getAttachment(att.header)
								.stream.use { input ->
									dest.outputStream().use { input.copyTo(it) }
								}
					}
					true
				} catch (e: Exception) {
					false
				}
			}
			onResult(ok)
		}
	}

	fun openAttachment(att: UiAttachment, onResult: (Boolean) -> Unit) {
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					val bytes = att.bytes ?: component.attachmentReader()
							.getAttachment(att.header).stream.use { it.readBytes() }
					OpenCache.open(att.fileName, bytes)
				} catch (e: Exception) {
					false
				}
			}
			onResult(ok)
		}
	}

	fun addContactFromLink(link: String, alias: String, onResult: (String?) -> Unit) {
		scope.launch {
			val error = withContext(Dispatchers.IO) {
				try {
					component.contactManager()
							.addPendingContact(link.trim(), alias.trim())
					null
				} catch (e: Exception) {
					e.message ?: e.javaClass.simpleName
				}
			}
			if (error == null) reloadPending()
			onResult(error)
		}
	}

	fun loadMyLink() {
		if (myLink != null) return
		scope.launch {
			myLink = io {
				component.contactManager().getHandshakeLink(ContactType.ZERION)
			}
		}
	}

	fun removeContact(id: ContactId) {
		scope.launch {
			io { component.contactManager().removeContact(id) }
			if (selectedId == id) {
				selectedId = null
				messages = emptyList()
			}
			reloadContacts()
		}
	}

	fun clearChat(id: ContactId, onDone: () -> Unit) {
		scope.launch {
			io { component.conversationManager().deleteAllMessages(id) }
			if (selectedId == id) messages = emptyList()
			reloadContacts()
			onDone()
		}
	}

	fun renameContact(id: ContactId, alias: String) {
		val clean = sanitizeName(alias)
		scope.launch {
			io {
				component.contactManager().setContactAlias(id,
						clean.ifEmpty { null })
			}
			reloadContacts()
		}
	}

	fun selectTheme(mode: ThemeMode) {
		themeMode = mode
		scope.launch {
			io {
				val settings = org.zerionproject.core.api.settings.Settings()
				settings.putInt(KEY_THEME, mode.ordinal)
				component.settingsManager().mergeSettings(settings, UI_NAMESPACE)
			}
		}
	}

	fun applyNotifications(enabled: Boolean) {
		notificationsEnabled = enabled
		persistNotifyFlag(KEY_NOTIFY, enabled)
	}

	fun applyNotifyPrivate(enabled: Boolean) {
		notifyPrivate = enabled
		persistNotifyFlag(KEY_NOTIFY_PRIVATE, enabled)
	}

	fun applyNotifyGroups(enabled: Boolean) {
		notifyGroups = enabled
		persistNotifyFlag(KEY_NOTIFY_GROUPS, enabled)
	}

	fun applyNotifyChannels(enabled: Boolean) {
		notifyChannels = enabled
		persistNotifyFlag(KEY_NOTIFY_CHANNELS, enabled)
	}

	fun applyNotifySound(enabled: Boolean) {
		notifySound = enabled
		persistNotifyFlag(KEY_NOTIFY_SOUND, enabled)
	}

	private fun persistNotifyFlag(key: String, enabled: Boolean) {
		scope.launch {
			io {
				val settings = org.zerionproject.core.api.settings.Settings()
				settings.putBoolean(key, enabled)
				component.settingsManager().mergeSettings(settings, UI_NAMESPACE)
			}
		}
	}

	fun setDuressPassword(password: CharArray) {
		scope.launch {
			io { DesktopProfiles.setDuress(dataDir, password) }
			hasDuress = true
		}
	}

	fun removeDuressPassword() {
		scope.launch {
			io { DesktopProfiles.removeDuress(dataDir) }
			hasDuress = false
		}
	}

	fun applyTorNetwork(mode: Int, bridges: String) {
		torNetworkMode = mode
		customBridges = bridges
		scope.launch {
			io {
				val settings = org.zerionproject.core.api.settings.Settings()
				settings.putInt(TorConstants.PREF_TOR_NETWORK, mode)
				settings.put(TorConstants.PREF_TOR_CUSTOM_BRIDGES, bridges.trim())
				component.settingsManager()
						.mergeSettings(settings, TorConstants.ID.string)
			}
			torState = TorUiState.CONNECTING
			repeat(6) {
				kotlinx.coroutines.delay(5000)
				refreshTorState()
			}
		}
	}

	fun applyI2p(enabled: Boolean) {
		i2pEnabled = enabled
		i2pState = if (enabled) TorUiState.CONNECTING else TorUiState.OFFLINE
		scope.launch {
			io {
				val settings = org.zerionproject.core.api.settings.Settings()
				settings.putBoolean("enable", enabled)
				component.settingsManager().mergeSettings(settings, I2P_NS)
			}
			repeat(6) {
				kotlinx.coroutines.delay(5000)
				refreshI2pState()
			}
		}
	}

	fun applyOfflineMode(offline: Boolean) {
		offlineMode = offline
		scope.launch {
			io { component.pluginManager().setOfflineMode(offline) }
			refreshTorState()
			kotlinx.coroutines.delay(1200)
			refreshTorState()
		}
	}

	fun changePassword(
			oldPassword: CharArray,
			newPassword: CharArray,
			onResult: (String?) -> Unit,
	) {
		scope.launch {
			val error = withContext(Dispatchers.IO) {
				try {
					component.accountManager()
							.changePassword(oldPassword, newPassword)
					null
				} catch (e: org.zerionproject.core.api.crypto
						.DecryptionException) {
					"Current password is incorrect."
				} catch (e: Exception) {
					e.message ?: "Could not change the password."
				} finally {
					java.util.Arrays.fill(oldPassword, ' ')
					java.util.Arrays.fill(newPassword, ' ')
				}
			}
			onResult(error)
		}
	}

	private fun onEvent(e: Event) {
		scope.launch {
			when (e) {
				is TransportStateEvent -> {
					if (e.transportId == TorConstants.ID) refreshTorState()
					if (e.transportId == org.zerionproject.core.api.plugin
									.I2pConstants.ID) refreshI2pState()
				}
				is ContactAddedEvent -> {
					reloadContacts()
					applyDefaultTimerTo(e.contactId)
				}
				is ContactRemovedEvent,
				is ContactAliasChangedEvent -> reloadContacts()
				is AvatarUpdatedEvent -> {
					reloadContacts()
					loadMyAvatar()
				}
				is ContactConnectedEvent -> setConnected(e.contactId, true)
				is ContactDisconnectedEvent -> setConnected(e.contactId, false)
				is PendingContactAddedEvent, is PendingContactRemovedEvent,
				is PendingContactStateChangedEvent -> reloadPending()
				is PrivateMessageReceivedEvent -> {
					reloadContacts()
					if (e.contactId == selectedId) {
						reloadMessages(e.contactId)
						markContactRead(e.contactId)
					} else if (notificationsEnabled && notifyPrivate) {
						notifier.notify("Zerion", "New message", notifySound)
					}
				}
				is VoiceSignalReceivedEvent -> {
					val header = e.signalHeader
					if (!header.isLocal) {
						val isOffer = header.signalType ==
								org.zerionproject.app.api.messaging
										.VoiceSignalType.CALL_OFFER ||
								header.signalType == org.zerionproject.app.api
										.messaging.VoiceSignalType.VIDEO_OFFER
						if (isOffer && !callsEnabled) {
							call.rejectIncomingOffer(header.callId, e.contactId)
						} else {
							val name = contacts.firstOrNull {
								it.id == e.contactId
							}?.name ?: "Unknown"
							val wasIdle = !call.inCall
							call.onSignal(header, e.contactId, name)
							if (wasIdle && call.phase ==
									VoiceCallEngine.Phase.INCOMING &&
									notificationsEnabled) {
								notifier.notify("Zerion",
										"Incoming call", notifySound)
							}
						}
					}
				}
				is MessagesSentEvent ->
					if (e.contactId == selectedId) reloadMessages(e.contactId)
				is MessagesAckedEvent ->
					if (e.contactId == selectedId) reloadMessages(e.contactId)
				is GroupPostReceivedEvent -> {
					reloadGroups()
					if (selectedGroupHex == e.groupId.hex()) {
						loadGroupPosts(e.groupId)
					} else if (notificationsEnabled && notifyGroups) {
						notifier.notify("Zerion", "New group message",
								notifySound)
					}
				}
				is GroupTrLocalStateChangedEvent -> {
					reloadGroups()
					reloadGroupInvites()
				}
				is GroupTrInviteOfferReceivedEvent -> reloadGroupInvites()
				is GroupMembershipChangedEvent -> {
					reloadGroups()
					selectedGroup?.let { loadGroupMembers(it.id) }
				}
				is GroupTrSelfRemovedEvent -> {
					if (selectedGroupHex == e.groupId.hex()) {
						selectedGroupHex = null
						groupPosts = emptyList()
					}
					reloadGroups()
				}
				is ChannelPostReceivedEvent -> {
					reloadChannels()
					if (selectedChannelHex == e.channelId.hex()) {
						loadChannelPosts(e.channelId, refresh = false)
					} else if (notificationsEnabled && notifyChannels &&
							!e.isLocal) {
						notifier.notify("Zerion", "New channel post",
								notifySound)
					}
				}
				is ChannelStateChangedEvent -> reloadChannels()
			}
		}
	}

	private fun setConnected(id: ContactId, connected: Boolean) {
		contacts = contacts.map {
			if (it.id == id) it.copy(connected = connected) else it
		}
	}

	private fun refreshTorState() {
		val plugin: Plugin? = component.pluginManager().getPlugin(TorConstants.ID)
		val next = when (plugin?.state) {
			Plugin.State.ACTIVE -> TorUiState.CONNECTED
			Plugin.State.ENABLING, Plugin.State.STARTING_STOPPING ->
				TorUiState.CONNECTING
			else -> TorUiState.OFFLINE
		}
		if (next != torState) {
		}
		torState = next
	}

	private fun refreshI2pState() {
		if (!i2pEnabled) {
			i2pState = TorUiState.OFFLINE
			return
		}
		val plugin = component.pluginManager().getPlugin(
				org.zerionproject.core.api.plugin.I2pConstants.ID)
		val next = when (plugin?.state) {
			Plugin.State.ACTIVE -> TorUiState.CONNECTED
			Plugin.State.ENABLING, Plugin.State.STARTING_STOPPING ->
				TorUiState.CONNECTING
			else -> TorUiState.OFFLINE
		}
		if (next != i2pState) {
		}
		i2pState = next
	}

	private fun reloadContacts() {
		scope.launch {
			contacts = io {
				val cm = component.contactManager()
				val cr = component.connectionRegistry()
				val conv = component.conversationManager()
				cm.contacts.map { c ->
					val name = c.alias ?: c.author.name
					val unread = try {
						conv.getGroupCount(c.id).unreadCount
					} catch (e: Exception) {
						0
					}
					val avatar = try {
						component.db().transactionWithResult<
								ByteArray?, Exception>(true) { txn ->
							val h = component.avatarManager()
									.getAvatarHeader(txn, c)
							if (h == null) null
							else component.attachmentReader()
									.getAttachment(txn, h).stream
									.use { it.readBytes() }
						}
					} catch (e: Exception) {
						null
					}
					ContactItem(
							id = c.id,
							name = name,
							colorKey = c.id.int,
							connected = cr.isConnected(c.id),
							unread = unread,
							verified = c.isVerified,
							postQuantum = c.isPostQuantum,
							avatar = avatar)
				}.sortedBy { it.name.lowercase() }
			} ?: emptyList()
		}
	}

	fun cancelPendingContact(
			id: org.zerionproject.core.api.contact.PendingContactId) {
		scope.launch {
			io { component.contactManager().removePendingContact(id) }
			reloadPending()
		}
	}

	private fun reloadPending() {
		scope.launch {
			pending = io {
				component.contactManager().pendingContacts.map { pair ->
					PendingItem(pair.first.id, pair.first.alias,
							friendlyPendingState(pair.second))
				}
			} ?: emptyList()
		}
	}

	private fun friendlyPendingState(
			state: org.zerionproject.core.api.contact.PendingContactState,
	): String = when (state.name) {
		"WAITING_FOR_CONNECTION" -> "Waiting to connect…"
		"OFFLINE" -> "Waiting (you're offline)"
		"CONNECTING" -> "Connecting…"
		"ADDING_CONTACT" -> "Finishing up…"
		"FAILED" -> "Failed — try re-adding"
		else -> state.name
	}

	private fun reloadMessages(id: ContactId) {
		scope.launch {
			messages = io {
				val conv = component.conversationManager()
				val mm = component.messagingManager()
				val db = component.db()
				val groupBytes = try {
					db.transactionWithResult<ByteArray, Exception>(true) { txn ->
						mm.getConversationId(txn, id).bytes
					}
				} catch (e: Exception) {
					ByteArray(0)
				}
				val headers: Collection<ConversationMessageHeader> =
						conv.getMessageHeaders(id)
				val texts = mm.getMessageTexts(id)
				val reactions = try {
					mm.getReactions(id)
				} catch (e: Exception) {
					emptyMap()
				}
				val pmHeaders = headers.mapNotNull { it as? PrivateMessageHeader }
				val vm = chat.zerion.desktop.ui.voice.VoiceMemo

				val partsByMemo = HashMap<String,
						MutableList<Pair<PrivateMessageHeader, VoiceMemo.Part>>>()
				for (pm in pmHeaders) {
					val p = vm.parsePart(texts[pm.id] ?: "")
					if (p != null)
						partsByMemo.getOrPut(p.memoId) { mutableListOf() }
								.add(pm to p)
				}
				val hiddenParts =
						HashSet<org.zerionproject.core.api.sync.MessageId>()
				val voiceByRep = HashMap<
						org.zerionproject.core.api.sync.MessageId, UiVoice>()
				for ((_, list) in partsByMemo) {
					val rep = list.maxByOrNull { it.first.timestamp }!!.first
					for (pr in list) if (pr.first.id != rep.id)
						hiddenParts.add(pr.first.id)
					val total = list.first().second.total
					val duration = list.first().second.durationMs
					val bySeq = HashMap<Int, String>()
					for (pr in list) bySeq[pr.second.seq] = pr.second.slice
					voiceByRep[rep.id] = if (bySeq.size == total &&
							(0 until total).all { bySeq.containsKey(it) }) {
						decodeVoiceText(vm.reassemble(duration,
								(0 until total).map { bySeq[it]!! }), groupBytes)
					} else {
						UiVoice(duration, null, incomplete = true)
					}
				}

				pmHeaders.mapNotNull { pm ->
					if (pm.id in hiddenParts) return@mapNotNull null
					val rawText = texts[pm.id] ?: ""
					val voice = when {
						voiceByRep.containsKey(pm.id) -> voiceByRep[pm.id]
						vm.isVoiceMessage(rawText) ->
							decodeVoiceText(rawText, groupBytes)
						else -> null
					}
					val replyTo = pm.replyToId
					UiMessage(
							id = pm.id.toString(),
							msgId = pm.id,
							text = if (voice != null) "" else rawText,
							outgoing = pm.isLocal,
							timestamp = pm.timestamp,
							sent = pm.isSent,
							seen = pm.isSeen,
							attachments = if (voice != null) emptyList()
									else loadAttachments(pm),
							replyToId = replyTo,
							replyPreview = replyTo?.let { texts[it] },
							reactions = reactions[pm.id] ?: emptyMap(),
							voice = voice)
				}.sortedBy { it.timestamp }
			} ?: emptyList()
		}
	}

	private fun decodeVoiceText(text: String, groupBytes: ByteArray): UiVoice {
		val vm = chat.zerion.desktop.ui.voice.VoiceMemo
		val parsed = vm.parseVoice(text)
				?: return UiVoice(0, null, incomplete = true)
		return try {
			UiVoice(parsed.durationMs,
					vm.decodeToMuLaw(parsed.payload, groupBytes))
		} catch (e: Exception) {
			UiVoice(parsed.durationMs, null)
		}
	}

	private fun markContactRead(id: ContactId) {
		scope.launch {
			io {
				val conv = component.conversationManager()
				conv.getMessageHeaders(id).forEach { h ->
					if (!h.isRead) {
						try {
							conv.setReadFlag(h.groupId, h.id, true)
						} catch (ignored: Exception) {
						}
					}
				}
			}
		}
	}

	private suspend fun <T> io(block: () -> T): T? = withContext(Dispatchers.IO) {
		try {
			block()
		} catch (e: Exception) {
			null
		}
	}

	private companion object {
		const val UI_NAMESPACE = "chat.zerion.desktop.ui"
		const val KEY_THEME = "theme"
		const val KEY_NOTIFY = "notifications"
		const val KEY_NOTIFY_PRIVATE = "notifications_private"
		const val KEY_NOTIFY_GROUPS = "notifications_groups"
		const val KEY_NOTIFY_CHANNELS = "notifications_channels"
		const val KEY_NOTIFY_SOUND = "notifications_sound"
		const val KEY_CALLS = "calls_enabled"
		const val KEY_MIC_DEVICE = "call_mic_device"
		const val KEY_SPEAKER_DEVICE = "call_speaker_device"
		const val KEY_DEFAULT_TIMER = "default_disappearing_ms"
		const val LOCK_NS = "chat.zerion.desktop.chatlock"
		const val I2P_NS = "org.zerionproject.core.i2p"
		const val MAX_SOURCE_BYTES = 25L * 1024 * 1024
		const val MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
		const val CHUNK_BYTES = 512 * 1024
		const val NO_TIMER = -1L
	}
}
