package chat.zerion.desktop.ui.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import chat.zerion.desktop.ui.vault.VaultArgon2
import chat.zerion.desktop.ui.vault.VaultCrypto
import chat.zerion.desktop.ui.vault.VaultManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Convert
import org.web3j.utils.Numeric

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Arrays
import java.util.Base64
import java.util.UUID

/**
 * Multi-wallet, multi-chain state holder inside the Vault.
 *
 * Each wallet is fully independent: its own BIP39 seed and its own wallet
 * password. Within a wallet the user switches chains (Ethereum implemented;
 * Bitcoin and Monero staged). Ethereum supports multiple derived accounts,
 * fresh per-transaction receive addresses, ERC-20 tokens (built-in and custom),
 * EIP-1559 sends that draw from every funded address, live balances and prices,
 * and full transaction history — all over Tor.
 */
class WalletModel internal constructor(
		private val manager: VaultManager,
		private val socksPort: Int,
		private val moneroBaseDir: java.io.File,
) {

	enum class Chain(val label: String, val implemented: Boolean) {
		ETH("Ethereum", true), BTC("Bitcoin", true), XMR("Monero", true)
	}

	enum class Builtin(val symbol: String, val contract: String?, val decimals: Int,
			val priceId: String) {
		ETH("ETH", null, 18, "ethereum"),
		USDC("USDC", "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", 6, "usd-coin"),
		USDT("USDT", "0xdac17f958d2ee523a2206206994597c13d831ec7", 6, "tether"),
		DAI("DAI", "0x6b175474e89094c44da98b954eedeac495271d0f", 18, "dai"),
	}

	data class WalletMeta(val id: String, val name: String, val coin: String = "MULTI")
	data class AccountUi(val index: Int, val name: String)
	data class ReceiveAddress(val index: Int, val address: String)
	data class TokenSpec(val symbol: String, val contract: String?, val decimals: Int)
	data class AssetBalance(val symbol: String, val amount: BigDecimal,
			val formatted: String, val usd: String?)
	data class TxRecord(val hash: String, val to: String, val amount: String,
			val symbol: String, val timestamp: Long, val incoming: Boolean,
			val status: String, val confirmations: Int = -1)
	data class Contact(val label: String, val address: String, val coin: String)
	data class Recipient(val address: String, val amount: String)
	data class BtcUtxo(val txid: String, val vout: Int, val valueSat: Long,
			val address: String, val confirmations: Int)

	private data class Acct(val index: Int, val name: String, val receiveIndex: Int,
			val btcRecv: Int = 0, val btcChange: Int = 0)
	private data class SentTx(val hash: String, val amount: BigDecimal)

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

	private val openSeeds = java.util.concurrent.ConcurrentHashMap<String, String>()

	init {
		scope.launch { withContext(Dispatchers.IO) { runCatching { sweepMoneroResidue() } } }
	}

	private var acctList = mutableListOf(Acct(0, "Account 1", 0))
	private var localTx = mutableListOf<Pair<Int, TxRecord>>()
	private var customTokens = mutableListOf<TokenSpec>()
	private var remoteHistory = listOf<TxRecord>()
	private var prices = mapOf<String, Double>()
	private var walletSalt = ByteArray(0)
	private var walletKdf = VaultArgon2.DEFAULT

	var wallets by mutableStateOf<List<WalletMeta>>(emptyList())
		private set
	var listLoaded by mutableStateOf(false)
		private set
	var activeWalletId by mutableStateOf<String?>(null)
		private set
	var walletLocked by mutableStateOf(true)
		private set
	var selectedChain by mutableStateOf(Chain.ETH)
		private set
	val xmrAvailable: Boolean = MoneroRpc.binary() != null
	var accounts by mutableStateOf(listOf(AccountUi(0, "Account 1")))
		private set
	var selectedAccount by mutableStateOf(0)
		private set
	var primaryAddress by mutableStateOf("")
		private set
	var receiveAddresses by mutableStateOf<List<ReceiveAddress>>(emptyList())
		private set
	var balanceEth by mutableStateOf<String?>(null)
		private set
	var assetBalances by mutableStateOf<List<AssetBalance>>(emptyList())
		private set
	var totalUsd by mutableStateOf<String?>(null)
		private set
	var history by mutableStateOf<List<TxRecord>>(emptyList())
		private set
	var nodeUrl by mutableStateOf(DEFAULT_NODE)
		private set
	var busy by mutableStateOf(false)
		private set
	var error by mutableStateOf<String?>(null)
		private set
	var pendingMnemonic by mutableStateOf<String?>(null)
		private set
	var pendingCoin by mutableStateOf("ETH")
		private set
	var creatingXmr by mutableStateOf(false)
		private set

	var btcBalance by mutableStateOf<String?>(null)
		private set
	var btcUsd by mutableStateOf<String?>(null)
		private set
	var btcReceiveAddress by mutableStateOf("")
		private set
	var btcSilentAddress by mutableStateOf("")
		private set
	data class SpUtxo(val txid: String, val vout: Int, val valueSat: Long,
			val xonlyHex: String, val tweakHex: String)
	var spOracle by mutableStateOf("")
		private set
	var spBirthday by mutableStateOf(0)
		private set
	var spScannedHeight by mutableStateOf(0)
		private set
	var spScanning by mutableStateOf(false)
		private set
	var spUtxos by mutableStateOf<List<SpUtxo>>(emptyList())
		private set
	var btcHistory by mutableStateOf<List<TxRecord>>(emptyList())
		private set
	var btcServer by mutableStateOf(DEFAULT_ELECTRUM)
		private set
	var contacts by mutableStateOf<List<Contact>>(emptyList())
		private set
	var btcUtxos by mutableStateOf<List<BtcUtxo>>(emptyList())
		private set
	var btcUtxosLoading by mutableStateOf(false)
		private set
	var fiatCurrency by mutableStateOf("USD")
		private set
	var require2fa by mutableStateOf(true)
		private set

	var xmrBalance by mutableStateOf<String?>(null)
		private set
	var xmrUsd by mutableStateOf<String?>(null)
		private set
	var xmrAddress by mutableStateOf("")
		private set
	var xmrHistory by mutableStateOf<List<TxRecord>>(emptyList())
		private set
	var xmrStatus by mutableStateOf<String?>(null)
		private set
	var xmrNode by mutableStateOf(DEFAULT_MONERO_NODE)
		private set
	var xmrTrusted by mutableStateOf(false)
		private set
	data class XmrAccountUi(val index: Int, val label: String)
	var xmrAccounts by mutableStateOf(listOf(XmrAccountUi(0, "Account 1")))
		private set
	var xmrAccount by mutableStateOf(0)
		private set

	@Volatile
	private var moneroRpc: MoneroRpc? = null
	private var lastXmrHeight = 0L
	private var xmrRpcPassword = ""
	private var xmrRestoreHeight = 0L
	private var pendingXmrRpcPw = ""
	private var pendingXmrHeight = 0L

	private val fiatSymbol: String get() = FIATS[fiatCurrency] ?: "$"

	val fiatCurrencies: List<String> get() = FIATS.keys.toList()

	val hasAnyWallet: Boolean get() = wallets.isNotEmpty()
	val activeWalletName: String
		get() = wallets.firstOrNull { it.id == activeWalletId }?.name ?: "Wallet"

	private val activeMnemonic: String? get() = activeWalletId?.let { openSeeds[it] }
	private val currentAcct get() = acctList.first { it.index == selectedAccount }

	private val activeCoin: String
		get() = wallets.firstOrNull { it.id == activeWalletId }?.coin ?: "MULTI"
	val activeIsMultiChain: Boolean get() = activeCoin == "MULTI"

	private fun applyCoinChain() {
		when (activeCoin) {
			"BTC" -> selectedChain = Chain.BTC
			"ETH" -> selectedChain = Chain.ETH
			"XMR" -> selectedChain = Chain.XMR
		}
	}

	private fun allTokens(): List<TokenSpec> =
			Builtin.entries.map { TokenSpec(it.symbol, it.contract, it.decimals) } +
					customTokens

	val tokenSymbols: List<String> get() = allTokens().map { it.symbol }


	fun refreshWallets() {
		scope.launch {
			val list = io { loadIndex() } ?: emptyList()
			wallets = list
			if (activeWalletId == null && list.isNotEmpty()) {
				val first = list.first().id
				activeWalletId = first
				walletLocked = !openSeeds.containsKey(first)
				if (!walletLocked) loadActiveState()
			}
			contacts = io { readContacts() } ?: emptyList()
			listLoaded = true
		}
	}


	fun contactsFor(coin: String): List<Contact> = contacts.filter { it.coin == coin }

	fun addContact(label: String, address: String, coin: String) {
		val addr = address.trim()
		if (addr.isEmpty()) return
		val clean = label.trim().replace(Regex("[\\t\\n\\r]"), " ")
				.ifBlank { addr.take(8) + "…" }
		if (contacts.any { it.address == addr && it.coin == coin }) return
		contacts = contacts + Contact(clean, addr, coin)
		scope.launch { withContext(Dispatchers.IO) { runCatching { writeContacts() } } }
	}

	fun removeContact(c: Contact) {
		contacts = contacts.filterNot {
			it.address == c.address && it.coin == c.coin && it.label == c.label
		}
		scope.launch { withContext(Dispatchers.IO) { runCatching { writeContacts() } } }
	}

	private fun readContacts(): List<Contact> {
		val raw = manager.getSecret(ADDRESS_BOOK)?.toString(Charsets.UTF_8) ?: return emptyList()
		return raw.lineSequence().mapNotNull { line ->
			if (line.startsWith("c=")) {
				val p = line.substring(2).split('\t')
				if (p.size >= 3) Contact(p[1], p[2], p[0]) else null
			} else null
		}.toList()
	}

	private fun writeContacts() {
		val sb = StringBuilder()
		contacts.forEach {
			sb.append("c=").append(it.coin).append('\t').append(it.label)
					.append('\t').append(it.address).append('\n')
		}
		manager.putSecret(ADDRESS_BOOK, sb.toString().toByteArray(Charsets.UTF_8))
	}


	fun exportBackup(password: CharArray, onResult: (ByteArray?) -> Unit) {
		scope.launch {
			val out = withContext(Dispatchers.IO) {
				try {
					val names = (manager.listSecretNames("wallet") + INDEX + ADDRESS_BOOK)
							.distinct()
					val payload = java.io.ByteArrayOutputStream()
					java.io.DataOutputStream(payload).use { d ->
						val entries = names.mapNotNull { n -> manager.getSecret(n)?.let { n to it } }
						d.writeInt(entries.size)
						for ((n, v) in entries) {
							val nb = n.toByteArray(Charsets.UTF_8)
							d.writeInt(nb.size); d.write(nb)
							d.writeInt(v.size); d.write(v)
						}
					}
					val salt = VaultCrypto.randomBytes(32)
					val kdf = VaultArgon2.choose()
					val key = VaultArgon2.deriveKey(password, salt, kdf)
					val enc = try {
						VaultCrypto.encrypt(payload.toByteArray(), key, BACKUP_AAD).toBytes()
					} finally { Arrays.fill(key, 0) }
					val file = java.io.ByteArrayOutputStream()
					java.io.DataOutputStream(file).use { d ->
						d.write(BACKUP_MAGIC); d.writeInt(1)
						d.writeInt(salt.size); d.write(salt)
						d.writeInt(kdf.memoryKb); d.writeInt(kdf.iterations)
						d.writeInt(kdf.parallelism)
						d.writeInt(enc.size); d.write(enc)
					}
					file.toByteArray()
				} catch (e: Exception) { null }
				finally { Arrays.fill(password, ' ') }
			}
			onResult(out)
		}
	}

	fun importBackup(data: ByteArray, password: CharArray, onDone: (String?) -> Unit) {
		scope.launch {
			val err = withContext(Dispatchers.IO) {
				try {
					val din = java.io.DataInputStream(java.io.ByteArrayInputStream(data))
					val magic = ByteArray(4); din.readFully(magic)
					if (!magic.contentEquals(BACKUP_MAGIC))
						return@withContext "That isn't a Zerion wallet backup file."
					din.readInt()
					val salt = ByteArray(din.readInt()); din.readFully(salt)
					val kdf = VaultArgon2.Params(din.readInt(), din.readInt(), din.readInt())
					val enc = ByteArray(din.readInt()); din.readFully(enc)
					val key = VaultArgon2.deriveKey(password, salt, kdf)
					val payload = try {
						VaultCrypto.decrypt(VaultCrypto.EncryptedData.fromBytes(enc), key,
								BACKUP_AAD)
					} finally { Arrays.fill(key, 0) }
					val pin = java.io.DataInputStream(java.io.ByteArrayInputStream(payload))
					val n = pin.readInt()
					repeat(n) {
						val nb = ByteArray(pin.readInt()); pin.readFully(nb)
						val vb = ByteArray(pin.readInt()); pin.readFully(vb)
						manager.putSecret(String(nb, Charsets.UTF_8), vb)
					}
					null
				} catch (e: Exception) {
					"Couldn't restore this backup (wrong password or corrupt file)."
				}
				finally { Arrays.fill(password, ' ') }
			}
			if (err == null) {
				openSeeds.clear()
				activeWalletId = null; walletLocked = true
				refreshWallets()
			}
			onDone(err)
		}
	}

	fun selectWallet(id: String) {
		if (id == activeWalletId && !walletLocked) return
		stopXmr()
		activeWalletId = id
		error = null
		if (openSeeds.containsKey(id)) {
			walletLocked = false
			loadActiveState()
		} else {
			walletLocked = true
			clearActiveDisplay()
		}
	}

	fun selectChain(chain: Chain) {
		selectedChain = chain
		val m = activeMnemonic ?: return
		if (walletLocked) return
		if (chain == Chain.BTC) {
			val acct = currentAcct
			scope.launch {
				val a = io { BtcKeys.address(m, acct.index, acct.btcRecv) }
				if (a != null && btcReceiveAddress.isEmpty()) btcReceiveAddress = a
			}
			refreshBtc(true)
		} else refreshBalance()
	}

	fun applyBtcServer(server: String) {
		val clean = server.trim().replace(Regex("[\\t\\n\\r]"), "")
		if (clean.isEmpty()) return
		btcServer = clean
		persistThen { refreshBtc(true) }
	}


	fun refreshBtc() = refreshBtc(true)

	private data class BtcRefresh(val totalSat: Long, val hist: List<TxRecord>,
			val recvAddr: String, val newChangeIndex: Int)

	private fun refreshBtc(showBusy: Boolean) {
		val m = activeMnemonic ?: return
		val acct = currentAcct
		if (showBusy) busy = true
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					val (host, port) = parseServer(btcServer)
					ElectrumClient(host, port, socksPort).use { c ->
						val tip = try { c.blockHeight() } catch (e: Exception) { 0 }
						data class Ref(val change: Boolean, val index: Int, val address: String)
						val refs = (0..acct.btcRecv).map {
							Ref(false, it, BtcKeys.address(m, acct.index, it))
						} + (0..acct.btcChange).map {
							Ref(true, it, BtcKeys.changeAddress(m, acct.index, it))
						}
						val our = refs.map { it.address }.toSet()
						var totalSat = 0L
						val txids = LinkedHashSet<String>()
						val heights = HashMap<String, Int>()
						val usedChange = HashSet<Int>()
						for (r in refs) {
							val sh = if (r.change)
								BtcKeys.changeScriptHash(m, acct.index, r.index)
							else BtcKeys.scriptHash(m, acct.index, r.index)
							totalSat += c.getBalanceSat(sh)
							val hist = c.getHistory(sh)
							hist.forEach { heights[it.txHash] = it.height; txids.add(it.txHash) }
							if (r.change && hist.isNotEmpty()) usedChange.add(r.index)
						}
						val hist = buildBtcHistory(c, txids.toList().takeLast(25), our,
								heights, tip)
						val newChange = if (acct.btcChange in usedChange)
							acct.btcChange + 1 else -1
						BtcRefresh(totalSat, hist,
								BtcKeys.address(m, acct.index, acct.btcRecv), newChange)
					}
				} catch (e: Exception) {
					null
				}
			}
			if (showBusy) {
				busy = false
				if (result == null)
					error = "Couldn't reach the Bitcoin node over Tor."
			}
			if (result != null) {
				val btc = BigDecimal(result.totalSat).movePointLeft(8)
				btcBalance = btc.setScale(8, RoundingMode.DOWN).stripTrailingZeros()
						.toPlainString()
				btcHistory = result.hist
				btcReceiveAddress = result.recvAddr
				val price = prices["BTC"]
				btcUsd = if (price != null) fiatFormat(btc, price) else null
				error = null
				if (result.newChangeIndex >= 0 &&
						acct.index == selectedAccount) {
					acctList = acctList.map {
						if (it.index == acct.index) it.copy(btcChange = result.newChangeIndex)
						else it
					}.toMutableList()
					withContext(Dispatchers.IO) { runCatching { persistConfig() } }
				}
			}
		}
	}

	private fun buildBtcHistory(c: ElectrumClient, txids: List<String>,
			our: Set<String>, heights: Map<String, Int>, tip: Int): List<TxRecord> {
		val out = mutableListOf<TxRecord>()
		val prevCache = HashMap<String, org.bitcoinj.core.Transaction>()
		for (txid in txids) {
			try {
				val tx = org.bitcoinj.core.Transaction(BtcKeys.params,
						org.bitcoinj.core.Utils.HEX.decode(c.getTransaction(txid)))
				var received = 0L
				var counterparty = ""
				for (o in tx.outputs) {
					val a = outAddr(o)
					if (a != null && a in our) received += o.value.value
					else if (a != null) counterparty = a
				}
				var sent = 0L
				for (inp in tx.inputs) {
					val op = inp.outpoint
					val prevId = op.hash.toString()
					val prev = prevCache.getOrPut(prevId) {
						org.bitcoinj.core.Transaction(BtcKeys.params,
								org.bitcoinj.core.Utils.HEX.decode(c.getTransaction(prevId)))
					}
					val prevOut = prev.getOutput(op.index)
					val a = outAddr(prevOut)
					if (a != null && a in our) sent += prevOut.value.value
				}
				val net = received - sent
				val incoming = net >= 0
				val amount = BigDecimal(Math.abs(net)).movePointLeft(8)
						.setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
				val h = heights[txid] ?: 0
				val conf = if (h <= 0 || tip <= 0) 0 else (tip - h + 1).coerceAtLeast(0)
				out.add(TxRecord(txid, counterparty, amount, "BTC", 0L, incoming,
						if (conf <= 0) "pending" else "confirmed", conf))
			} catch (e: Exception) {
			}
		}
		return out.asReversed()
	}

	private fun outAddr(o: org.bitcoinj.core.TransactionOutput): String? = try {
		o.scriptPubKey.getToAddress(BtcKeys.params, true).toString()
	} catch (e: Exception) { null }

	fun newBtcReceiveAddress() {
		val m = activeMnemonic ?: return
		val acct = currentAcct
		acctList = acctList.map {
			if (it.index == acct.index) it.copy(btcRecv = it.btcRecv + 1) else it
		}.toMutableList()
		val newIndex = currentAcct.btcRecv
		scope.launch {
			val a = io { BtcKeys.address(m, acct.index, newIndex) }
			if (a != null) btcReceiveAddress = a
			withContext(Dispatchers.IO) { runCatching { persistConfig() } }
		}
	}

	fun sendBtc(recipients: List<Recipient>, feeRate: Double?,
			selectedUtxos: Set<String>?, sweep: Boolean = false,
			onDone: (String?) -> Unit) {
		val m = activeMnemonic ?: return onDone("Wallet locked.")
		val acct = currentAcct
		if (recipients.isEmpty()) return onDone("Add at least one recipient.")
		if (recipients.size == 1 && SilentPayment.isSilentAddress(recipients[0].address)) {
			return sendBtcSilent(m, acct, recipients[0], feeRate, selectedUtxos, onDone)
		}
		val outputs = mutableListOf<BtcTx.Output>()
		for (r in recipients) {
			val addr = r.address.trim()
			if (!BtcKeys.isValidBtcAddress(addr)) {
				return onDone("Not a valid Bitcoin address: ${addr.take(14)}… " +
						"BTC can only be sent to a Bitcoin address (bc1…/1…/3…).")
			}
			if (!sweep) {
				val amt = r.amount.trim().toBigDecimalOrNull()
				if (amt == null || amt <= BigDecimal.ZERO)
					return onDone("Enter a valid amount for each recipient.")
				outputs.add(BtcTx.Output(addr, amt.movePointRight(8).toBigInteger().toLong()))
			} else {
				outputs.add(BtcTx.Output(addr, 0L))
			}
		}
		if (sweep && recipients.size != 1)
			return onDone("Max can only be used with a single recipient.")
		busy = true; error = null
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				doSendBtc(m, acct, outputs, feeRate, selectedUtxos, sweep)
			}
			busy = false
			if (result.first != null) { refreshBtc(false); onDone(null) }
			else onDone(result.second)
		}
	}

	private fun doSendBtc(m: String, acct: Acct, outputs: List<BtcTx.Output>,
			feeRate: Double?, selectedUtxos: Set<String>?, sweep: Boolean): Pair<String?, String?> {
		return try {
			val (host, port) = parseServer(btcServer)
			ElectrumClient(host, port, socksPort).use { c ->
				val spend = assembleBtcTx(c, m, acct, outputs, null, feeRate,
						selectedUtxos, null, sweep)
				broadcastTracked(c, spend, outputs.sumOf { it.valueSat }) to null
			}
		} catch (e: Exception) {
			null to "Transaction failed: ${e.message ?: "network error"}"
		}
	}


	private data class PendingTx(val id: String, val txid: String,
			val rawHex: String, val outpoints: List<String>, val state: String,
			val createdAt: Long, val netSat: Long)

	private fun pendingKey(walletId: String) = "wallet.$walletId.pending"

	private fun loadPending(): List<PendingTx> {
		val wid = activeWalletId ?: return emptyList()
		val raw = manager.getSecret(pendingKey(wid))?.toString(Charsets.UTF_8)
				?: return emptyList()
		return raw.split("\n").mapNotNull { line ->
			if (line.isBlank()) return@mapNotNull null
			val p = line.split("|", limit = 6)
			if (p.size < 6) return@mapNotNull null
			val ops = if (p[4].isEmpty()) emptyList() else p[4].split(",")
			PendingTx(p[0], p[1], p[5], ops, p[2],
					p[3].toLongOrNull() ?: 0L, 0L)
		}
	}

	private fun savePending(list: List<PendingTx>) {
		val wid = activeWalletId ?: return
		val cutoff = System.currentTimeMillis() - PENDING_RETENTION_MS
		val kept = list.filter {
			it.state == BROADCASTING || it.state == POSSIBLY_SENT ||
					it.createdAt >= cutoff
		}
		if (kept.isEmpty()) {
			manager.deleteSecret(pendingKey(wid))
			return
		}
		val sb = StringBuilder()
		for (p in kept) {
			sb.append(p.id).append('|').append(p.txid).append('|')
					.append(p.state).append('|').append(p.createdAt).append('|')
					.append(p.outpoints.joinToString(",")).append('|')
					.append(p.rawHex).append('\n')
		}
		manager.putSecret(pendingKey(wid), sb.toString().toByteArray(Charsets.UTF_8))
	}

	private val walletFileLock = Any()

	private fun putPending(tx: PendingTx) {
		synchronized(walletFileLock) {
			val list = loadPending().filter { it.id != tx.id }
			savePending(list + tx)
		}
	}

	private fun broadcastTracked(c: ElectrumClient, spend: BtcSpend,
			netSat: Long): String =
			broadcastRaw(c, spend.rawHex, spend.ourInputs.keys.toList(), netSat)

	private fun broadcastRaw(c: ElectrumClient, rawHex: String,
			outpoints: List<String>, netSat: Long): String {
		val txid = org.bitcoinj.core.Transaction(BtcKeys.params,
				org.bitcoinj.core.Utils.HEX.decode(rawHex)).txId.toString()
		val pending = PendingTx(java.util.UUID.randomUUID().toString(), txid,
				rawHex, outpoints, BROADCASTING,
				System.currentTimeMillis(), netSat)
		putPending(pending)
		return try {
			val accepted = c.broadcast(rawHex)
			putPending(pending.copy(state = SENT))
			accepted
		} catch (e: java.io.IOException) {
			putPending(pending.copy(state = POSSIBLY_SENT))
			throw java.io.IOException("The network didn't confirm the broadcast. " +
					"The transaction may already be sent; its coins are held until " +
					"the next refresh confirms it.", e)
		}
	}

	private fun reconcilePending(c: ElectrumClient,
			liveOutpoints: Set<String>): Set<String> {
		val snapshot = synchronized(walletFileLock) { loadPending() }
		if (snapshot.isEmpty()) return emptySet()
		val reserved = HashSet<String>()
		val decisions = HashMap<String, String>()
		val now = System.currentTimeMillis()
		for (p in snapshot) {
			if (p.state != BROADCASTING && p.state != POSSIBLY_SENT) continue
			val onChain = try {
				c.getTransaction(p.txid); true
			} catch (e: Exception) {
				false
			}
			val anyInputLive = p.outpoints.any { it in liveOutpoints }
			when {
				onChain || !anyInputLive -> decisions[p.id] = SENT
				now - p.createdAt > PENDING_GRACE_MS -> decisions[p.id] = FAILED
				else -> reserved.addAll(p.outpoints)
			}
		}
		if (decisions.isNotEmpty()) synchronized(walletFileLock) {
			val fresh = loadPending().map { p ->
				val newState = decisions[p.id]
				if (newState != null &&
						(p.state == BROADCASTING || p.state == POSSIBLY_SENT))
					p.copy(state = newState)
				else p
			}
			savePending(fresh)
		}
		return reserved
	}


	data class BtcPlanInfo(val fingerprint: String, val amountSat: Long,
			val feeSat: Long, val changeSat: Long, val inputCount: Int,
			val feeRateSatPerVb: Double, val privacyLevel: String = "HIGH",
			val privacyNote: String? = null)

	private val preparedSpends = java.util.concurrent.ConcurrentHashMap<String, BtcSpend>()

	var extremePrivacy by mutableStateOf(false)
		private set

	private fun strictKey(w: String) = "wallet.$w.strict"

	fun applyExtremePrivacy(on: Boolean) {
		val w = activeWalletId ?: return
		extremePrivacy = on
		scope.launch {
			io {
				if (on) manager.putSecret(strictKey(w), byteArrayOf(1))
				else manager.deleteSecret(strictKey(w))
			}
		}
	}

	fun isSilentPaymentAddress(a: String): Boolean =
			SilentPayment.isSilentAddress(a.trim())

	fun prepareBtcSend(recipients: List<Recipient>, feeRate: Double?,
			selectedUtxos: Set<String>?, sweep: Boolean,
			onDone: (BtcPlanInfo?, String?) -> Unit) {
		val m = activeMnemonic ?: return onDone(null, "Wallet locked.")
		val acct = currentAcct
		if (recipients.isEmpty()) return onDone(null, "Add at least one recipient.")
		if (recipients.size == 1 &&
				SilentPayment.isSilentAddress(recipients[0].address))
			return onDone(null, "Silent-payment sends are confirmed directly.")
		val outputs = mutableListOf<BtcTx.Output>()
		for (r in recipients) {
			val addr = r.address.trim()
			if (!BtcKeys.isValidBtcAddress(addr))
				return onDone(null, "Not a valid Bitcoin address: ${addr.take(14)}…")
			if (!sweep) {
				val amt = r.amount.trim().toBigDecimalOrNull()
				if (amt == null || amt <= BigDecimal.ZERO)
					return onDone(null, "Enter a valid amount for each recipient.")
				outputs.add(BtcTx.Output(addr,
						amt.movePointRight(8).toBigInteger().toLong()))
			} else {
				outputs.add(BtcTx.Output(addr, 0L))
			}
		}
		if (sweep && recipients.size != 1)
			return onDone(null, "Max can only be used with a single recipient.")
		busy = true; error = null
		scope.launch {
			val res = withContext(Dispatchers.IO) {
				try {
					val (host, port) = parseServer(btcServer)
					ElectrumClient(host, port, socksPort).use { c ->
						val spend = assembleBtcTx(c, m, acct, outputs, null,
								feeRate, selectedUtxos, null, sweep)
						val inSat = spend.ourInputs.values.sumOf { it.valueSat }
						val outSat = spend.signedTx.outputs.sumOf { it.value.value }
						val changeSat = if (spend.changeOutputIndex >= 0)
							spend.signedTx.getOutput(
									spend.changeOutputIndex.toLong())
									.value.value else 0L
						val inAddrs = spend.ourInputs.values.map {
							org.bitcoinj.core.SegwitAddress.fromKey(
									BtcKeys.params, it.key).toString()
						}
						val distinct = inAddrs.toSet()
						val reused = inAddrs.size != distinct.size
						val merging = distinct.size > 1
						if (extremePrivacy && merging)
							return@use (null as BtcPlanInfo?) to
									("Extreme privacy is on: this send would link " +
									"${distinct.size} of your addresses. Use coin " +
									"control to spend from a single address, or turn " +
									"off Extreme Privacy.")
						val level = when {
							merging -> "LOW"
							reused -> "MEDIUM"
							else -> "HIGH"
						}
						val note = buildString {
							if (merging) append("Links ${distinct.size} of your " +
									"addresses in one transaction. ")
							if (reused) append("Spends more than one output from " +
									"the same address.")
						}.trim().ifEmpty { null }
						val fp = sha256Hex(spend.rawHex)
						preparedSpends.clear()
						preparedSpends[fp] = spend
						BtcPlanInfo(fp, outSat - changeSat, inSat - outSat,
								changeSat, spend.ourInputs.size,
								spend.feeRateSatPerVb, level, note) to null
					}
				} catch (e: Exception) {
					null to "Couldn't prepare the transaction: " +
							(e.message ?: "network error")
				}
			}
			busy = false
			onDone(res.first, res.second)
		}
	}

	fun cancelPreparedSend() {
		preparedSpends.clear()
	}

	fun confirmBtcSend(plan: BtcPlanInfo, onDone: (String?) -> Unit) {
		val spend = preparedSpends[plan.fingerprint]
				?: return onDone("This review expired. Please review again.")
		busy = true; error = null
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					if (sha256Hex(spend.rawHex) != plan.fingerprint)
						return@withContext "The transaction changed; review again."
					val (host, port) = parseServer(btcServer)
					ElectrumClient(host, port, socksPort).use { c ->
						broadcastTracked(c, spend, plan.amountSat)
					}
					null
				} catch (e: Exception) {
					"Transaction failed: ${e.message ?: "network error"}"
				}
			}
			preparedSpends.remove(plan.fingerprint)
			busy = false
			if (result == null) { refreshBtc(false); onDone(null) }
			else onDone(result)
		}
	}

	private fun sha256Hex(s: String): String =
			java.security.MessageDigest.getInstance("SHA-256")
					.digest(s.toByteArray(Charsets.UTF_8))
					.joinToString("") { "%02x".format(it) }


	var btcFrozen by mutableStateOf<Set<String>>(emptySet())
		private set
	var btcLabels by mutableStateOf<Map<String, String>>(emptyMap())
		private set

	private fun frozenKey(w: String) = "wallet.$w.frozen"
	private fun labelsKey(w: String) = "wallet.$w.labels"

	private fun loadFrozen(): Set<String> {
		val w = activeWalletId ?: return emptySet()
		val raw = manager.getSecret(frozenKey(w))?.toString(Charsets.UTF_8)
				?: return emptySet()
		return raw.split("\n").filter { it.isNotBlank() }.toSet()
	}

	private fun loadLabels(): Map<String, String> {
		val w = activeWalletId ?: return emptyMap()
		val raw = manager.getSecret(labelsKey(w))?.toString(Charsets.UTF_8)
				?: return emptyMap()
		return raw.split("\n").mapNotNull {
			val p = it.split("\t", limit = 2)
			if (p.size == 2) p[0] to p[1] else null
		}.toMap()
	}

	fun loadCoinMeta() {
		scope.launch {
			val f = io { loadFrozen() }
			val l = io { loadLabels() }
			val s = io {
				val w = activeWalletId
				w != null && manager.getSecret(strictKey(w)) != null
			}
			if (f != null) btcFrozen = f
			if (l != null) btcLabels = l
			if (s != null) extremePrivacy = s
		}
	}

	fun freezeUtxo(outpoint: String, frozen: Boolean) {
		val w = activeWalletId ?: return
		scope.launch {
			val next = io {
				synchronized(walletFileLock) {
					val cur = loadFrozen().toMutableSet()
					if (frozen) cur.add(outpoint) else cur.remove(outpoint)
					if (cur.isEmpty()) manager.deleteSecret(frozenKey(w))
					else manager.putSecret(frozenKey(w),
							cur.joinToString("\n").toByteArray(Charsets.UTF_8))
					cur.toSet()
				}
			}
			if (next != null) btcFrozen = next
		}
	}

	fun setUtxoLabel(outpoint: String, label: String) {
		val w = activeWalletId ?: return
		scope.launch {
			val next = io {
				synchronized(walletFileLock) {
					val cur = loadLabels().toMutableMap()
					val t = label.trim().replace("\t", " ").replace("\n", " ")
					if (t.isEmpty()) cur.remove(outpoint) else cur[outpoint] = t
					if (cur.isEmpty()) manager.deleteSecret(labelsKey(w))
					else manager.putSecret(labelsKey(w), cur.entries
							.joinToString("\n") { "${it.key}\t${it.value}" }
							.toByteArray(Charsets.UTF_8))
					cur.toMap()
				}
			}
			if (next != null) btcLabels = next
		}
	}

	private fun sendBtcSilent(m: String, acct: Acct, r: Recipient, feeRate: Double?,
			selectedUtxos: Set<String>?, onDone: (String?) -> Unit) {
		val addr = SilentPayment.decodeAddress(r.address.trim())
				?: return onDone("That silent payment address is invalid.")
		if (!SilentPayment.selfTest())
			return onDone("Silent Payments self-check failed; refusing to send.")
		val amt = r.amount.trim().toBigDecimalOrNull()
		if (amt == null || amt <= BigDecimal.ZERO) return onDone("Enter a valid amount.")
		val amountSat = amt.movePointRight(8).toBigInteger().toLong()
		busy = true; error = null
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					val (host, port) = parseServer(btcServer)
					ElectrumClient(host, port, socksPort).use { c ->
						val spend = assembleBtcTx(c, m, acct, emptyList(), null,
								feeRate, selectedUtxos,
								Triple(addr.scanPub, addr.spendPub, amountSat))
						val distinct = spend.ourInputs.values.map {
							org.bitcoinj.core.SegwitAddress.fromKey(
									BtcKeys.params, it.key).toString()
						}.toSet()
						if (extremePrivacy && distinct.size > 1)
							return@use null to ("Extreme privacy is on: this send " +
									"would link ${distinct.size} of your addresses. " +
									"Use coin control to spend from a single address, " +
									"or turn off Extreme Privacy.")
						broadcastTracked(c, spend, amountSat) to null
					}
				} catch (e: Exception) {
					null to "Transaction failed: ${e.message ?: "network error"}"
				}
			}
			busy = false
			if (result.first != null) { refreshBtc(false); onDone(null) }
			else onDone(result.second)
		}
	}


	fun loadBtcUtxos() {
		val m = activeMnemonic ?: return
		val acct = currentAcct
		btcUtxosLoading = true
		scope.launch {
			val list = withContext(Dispatchers.IO) {
				try {
					val (host, port) = parseServer(btcServer)
					ElectrumClient(host, port, socksPort).use { c ->
						val tip = try { c.blockHeight() } catch (e: Exception) { 0 }
						val out = mutableListOf<BtcUtxo>()
						for (i in 0..acct.btcRecv) {
							val addr = BtcKeys.address(m, acct.index, i)
							c.listUnspent(BtcKeys.scriptHash(m, acct.index, i)).forEach {
								val conf = if (it.height <= 0 || tip <= 0) 0
										else tip - it.height + 1
								out.add(BtcUtxo(it.txHash, it.txPos, it.value, addr, conf))
							}
						}
						for (i in 0..acct.btcChange) {
							val addr = BtcKeys.changeAddress(m, acct.index, i)
							c.listUnspent(BtcKeys.changeScriptHash(m, acct.index, i)).forEach {
								val conf = if (it.height <= 0 || tip <= 0) 0
										else tip - it.height + 1
								out.add(BtcUtxo(it.txHash, it.txPos, it.value, addr, conf))
							}
						}
						val live = out.map { "${it.txid}:${it.vout}" }.toSet()
						val reserved = reconcilePending(c, live)
						out.filter { "${it.txid}:${it.vout}" !in reserved }
								.sortedByDescending { it.valueSat }
					}
				} catch (e: Exception) { null }
			}
			btcUtxosLoading = false
			if (list != null) btcUtxos = list
		}
	}


	val spBalance: String
		get() = BigDecimal(spUtxos.sumOf { it.valueSat }).movePointLeft(8)
				.stripTrailingZeros().toPlainString()

	fun applySpConfig(oracle: String, birthday: Int) {
		spOracle = oracle.trim().replace(Regex("[\\t\\n\\r]"), "")
		spBirthday = birthday.coerceAtLeast(0)
		if (spScannedHeight < spBirthday - 1)
			spScannedHeight = (spBirthday - 1).coerceAtLeast(0)
		persistThen { }
	}

	fun scanSilentPayments(onDone: (String?) -> Unit) {
		val m = activeMnemonic ?: return onDone("Wallet locked.")
		val acct = currentAcct
		if (spOracle.isBlank()) return onDone("Set a silent-payment oracle URL first.")
		if (spScanning) return
		spScanning = true; error = null
		scope.launch {
			var errMsg: String? = null
			val res = withContext(Dispatchers.IO) {
				try {
					val scanPriv = BtcKeys.silentScanPriv(m, acct.index)
					val spendPub = BtcKeys.silentSpendPub(m, acct.index)
					val tip = SilentPaymentScanner.tipHeight(spOracle, socksPort)
							?: run { errMsg = "Couldn't reach the oracle over Tor."
								return@withContext null }
					var from = maxOf(spScannedHeight + 1, spBirthday)
					if (from < 1) from = 1
					val cap = minOf(tip, from + MAX_SP_BLOCKS_PER_SCAN - 1)
					val found = mutableListOf<SpUtxo>()
					var h = from
					while (h <= cap) {
						SilentPaymentScanner.scanBlock(spOracle, h, scanPriv, spendPub, socksPort)
								.forEach { found.add(SpUtxo(it.txid, it.vout, it.valueSat,
										hexOf(it.xonly), hexOf(it.tweak))) }
						h++
					}
					Pair(cap, found as List<SpUtxo>)
				} catch (e: Exception) {
					errMsg = "Scan failed: ${e.message ?: "network error"}"; null
				}
			}
			spScanning = false
			if (res != null) {
				spScannedHeight = res.first
				spUtxos = (spUtxos + res.second).distinctBy { "${it.txid}:${it.vout}" }
				withContext(Dispatchers.IO) { runCatching { persistConfig() } }
				onDone(null)
			} else onDone(errMsg ?: "Scan failed.")
		}
	}

	fun sweepSilentPayments(toAddress: String, feeRate: Double?, onDone: (String?) -> Unit) {
		val m = activeMnemonic ?: return onDone("Wallet locked.")
		val acct = currentAcct
		val toAddr = toAddress.trim()
		if (!BtcKeys.isValidBtcAddress(toAddr)) return onDone("Enter a valid Bitcoin address.")
		if (spUtxos.isEmpty()) return onDone("No received silent payments to move.")
		if (!SilentPayment.selfTest() || !TaprootSign.selfTest())
			return onDone("Silent Payments self-check failed; refusing to spend.")
		busy = true; error = null
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					val spendPriv = BtcKeys.silentSpendPriv(m, acct.index)
					val curveN = org.bitcoinj.core.ECKey.CURVE.n
					val (host, port) = parseServer(btcServer)
					ElectrumClient(host, port, socksPort).use { c ->
						val inputs = mutableListOf<BtcTx.TaprootInput>()
						var sumIn = 0L
						for (u in spUtxos) {
							val spk = hexToBytesLocal("5120" + u.xonlyHex)
							val stillUnspent = c.listUnspent(scriptHashOfBytes(spk))
									.any { it.txHash == u.txid && it.txPos == u.vout }
							if (!stillUnspent) continue
							val priv = spendPriv.add(BigInteger(1,
									hexToBytesLocal(u.tweakHex))).mod(curveN)
							inputs.add(BtcTx.TaprootInput(u.txid, u.vout, u.valueSat, priv, spk))
							sumIn += u.valueSat
						}
						if (inputs.isEmpty()) return@withContext "No unspent silent payments found."
						val rate = feeRate?.coerceAtLeast(1.0)
								?: maxOf(c.estimateFeeBtcPerKb(4) * 1e8 / 1000.0, 2.0)
						val vbytes = 11 + inputs.size * 58 + 31
						val fee = Math.ceil(vbytes * rate).toLong()
						val swept = sumIn - fee
						if (swept <= 294L) return@withContext "Amount too low after fee."
						c.broadcast(BtcTx.buildAndSignTaproot(inputs,
								listOf(BtcTx.Output(toAddr, swept))))
						"OK"
					}
				} catch (e: Exception) { "Sweep failed: ${e.message ?: "network error"}" }
			}
			busy = false
			if (result == "OK") { spUtxos = emptyList(); refreshBtc(false); onDone(null) }
			else onDone(result)
		}
	}

	private fun hexOf(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
	private fun hexToBytesLocal(h: String): ByteArray {
		val out = ByteArray(h.length / 2)
		for (i in out.indices) out[i] = h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
		return out
	}
	private fun scriptHashOfBytes(spk: ByteArray): String =
			org.bitcoinj.core.Sha256Hash.hash(spk).reversedArray()
					.joinToString("") { "%02x".format(it) }

	fun fetchBtcFeeRates(onDone: (Triple<Double, Double, Double>?) -> Unit) {
		scope.launch {
			val rates = withContext(Dispatchers.IO) {
				try {
					val (host, port) = parseServer(btcServer)
					ElectrumClient(host, port, socksPort).use { c ->
						fun rate(target: Int) =
								maxOf(c.estimateFeeBtcPerKb(target) * 1e8 / 1000.0, 1.0)
						Triple(rate(25), rate(6), rate(2))
					}
				} catch (e: Exception) { null }
			}
			onDone(rates)
		}
	}

	fun bumpBtcFee(txHash: String, onDone: (String?) -> Unit) {
		val m = activeMnemonic ?: return onDone("Wallet locked.")
		val acct = currentAcct
		busy = true; error = null
		scope.launch {
			val result = withContext(Dispatchers.IO) { doBumpBtcFee(m, acct, txHash) }
			busy = false
			if (result.first != null) { refreshBtc(false); onDone(null) }
			else onDone(result.second)
		}
	}

	private fun doBumpBtcFee(m: String, acct: Acct, txHash: String): Pair<String?, String?> {
		return try {
			val (host, port) = parseServer(btcServer)
			ElectrumClient(host, port, socksPort).use { c ->
				val tx = org.bitcoinj.core.Transaction(BtcKeys.params,
						org.bitcoinj.core.Utils.HEX.decode(c.getTransaction(txHash)))
				val ourKeys = HashMap<String, org.bitcoinj.crypto.DeterministicKey>()
				for (i in 0..acct.btcRecv) ourKeys[BtcKeys.address(m, acct.index, i)] =
						BtcKeys.receiveKey(m, acct.index, i)
				for (i in 0..acct.btcChange) ourKeys[BtcKeys.changeAddress(m, acct.index, i)] =
						BtcKeys.changeKey(m, acct.index, i)
				val inputs = mutableListOf<BtcTx.Input>()
				var sumIn = 0L
				for (inp in tx.inputs) {
					val prevId = inp.outpoint.hash.toString()
					val prev = org.bitcoinj.core.Transaction(BtcKeys.params,
							org.bitcoinj.core.Utils.HEX.decode(c.getTransaction(prevId)))
					val prevOut = prev.getOutput(inp.outpoint.index)
					val addr = outAddr(prevOut)
							?: return null to "This transaction can't be fee-bumped here."
					val key = ourKeys[addr]
							?: return null to "This transaction has inputs from another " +
									"wallet and can't be bumped here."
					inputs.add(BtcTx.Input(prevId, inp.outpoint.index.toInt(),
							prevOut.value.value, key))
					sumIn += prevOut.value.value
				}
				var recipient: String? = null
				var recipientVal = 0L
				for (o in tx.outputs) {
					val a = outAddr(o) ?: return null to
							"This transaction can't be fee-bumped here."
					if (a in ourKeys.keys) continue
					if (recipient != null) return null to
							"This transaction has multiple recipients and can't be bumped here."
					recipient = a; recipientVal = o.value.value
				}
				if (recipient == null) return null to "Nothing to fee-bump."
				val sumOut = tx.outputs.sumOf { it.value.value }
				val origFee = sumIn - sumOut
				val vbytes = BtcTx.estimateVBytes(inputs.size, 2)
				val curRate = maxOf(c.estimateFeeBtcPerKb(2) * 1e8 / 1000.0, 2.0)
				val newFee = maxOf(Math.ceil(curRate * vbytes).toLong(), origFee + vbytes)
				val change = sumIn - recipientVal - newFee
				if (change < 0) return null to
						"Not enough headroom to raise the fee. Send a new higher-fee " +
						"payment instead."
				val outputs = mutableListOf(BtcTx.Output(recipient, recipientVal))
				if (change > 294L) outputs.add(BtcTx.Output(
						BtcKeys.changeAddress(m, acct.index, acct.btcChange), change))
				c.broadcast(BtcTx.buildAndSign(inputs, outputs)) to null
			}
		} catch (e: Exception) {
			null to "Fee bump failed: ${e.message ?: "network error"}"
		}
	}

	private data class BtcSpend(
			val signedTx: org.bitcoinj.core.Transaction,
			val rawHex: String,
			val ourInputs: Map<String, PayJoin.InInfo>,
			val changeOutputIndex: Int,
			val feeRateSatPerVb: Double)

	private fun assembleBtcTx(c: ElectrumClient, m: String, acct: Acct,
			recipients: List<BtcTx.Output>, opReturn: ByteArray?,
			feeRateOverride: Double? = null, selectedUtxos: Set<String>? = null,
			silent: Triple<ByteArray, ByteArray, Long>? = null,
			sweep: Boolean = false): BtcSpend {
		data class U(val utxo: ElectrumClient.Utxo, val key: org.bitcoinj.crypto.DeterministicKey)
		val pool = mutableListOf<U>()
		for (i in 0..acct.btcRecv) {
			c.listUnspent(BtcKeys.scriptHash(m, acct.index, i)).forEach {
				pool.add(U(it, BtcKeys.receiveKey(m, acct.index, i)))
			}
		}
		for (i in 0..acct.btcChange) {
			c.listUnspent(BtcKeys.changeScriptHash(m, acct.index, i)).forEach {
				pool.add(U(it, BtcKeys.changeKey(m, acct.index, i)))
			}
		}
		val liveOutpoints = pool.map { "${it.utxo.txHash}:${it.utxo.txPos}" }
				.toSet()
		val reserved = reconcilePending(c, liveOutpoints)
		val spendablePool = if (reserved.isEmpty()) pool
				else pool.filter { "${it.utxo.txHash}:${it.utxo.txPos}" !in reserved }
		val frozen = loadFrozen()
		val available = if (selectedUtxos != null) {
			val chosen = spendablePool.filter {
				"${it.utxo.txHash}:${it.utxo.txPos}" in selectedUtxos
			}
			if (chosen.any { "${it.utxo.txHash}:${it.utxo.txPos}" in frozen })
				throw IllegalStateException(
						"A frozen coin can't be spent. Unfreeze it first.")
			chosen
		} else {
			spendablePool.filter { "${it.utxo.txHash}:${it.utxo.txPos}" !in frozen }
		}
		if (available.isEmpty())
			throw IllegalStateException(if (selectedUtxos != null)
					"Select at least one coin that isn't reserved or frozen."
					else "No spendable coins; funds may be reserved or frozen.")
		val sorted = available.sortedByDescending { it.utxo.value }
		val feeRate = feeRateOverride?.coerceAtLeast(1.0)
				?: maxOf(c.estimateFeeBtcPerKb(4) * 1e8 / 1000.0, 2.0)
		val dust = 294L
		val opExtra = (if (opReturn != null) opReturn.size + 11 else 0) +
				(if (silent != null) 12 else 0)
		val amountTotal = recipients.sumOf { it.valueSat } + (silent?.third ?: 0L)
		val numOutBase = recipients.size + (if (silent != null) 1 else 0) + 1 +
				(if (opReturn != null) 1 else 0)

		val selected = mutableListOf<U>()
		var inSat = 0L
		var fee: Long
		if (sweep) {
			if (recipients.size != 1 || silent != null)
				throw IllegalStateException("Max applies to a single recipient.")
			selected.addAll(sorted)
			inSat = selected.sumOf { it.utxo.value }
			if (selected.isEmpty()) throw IllegalStateException("No spendable coins.")
			val vbytes = BtcTx.estimateVBytes(selected.size, 1) + opExtra
			fee = Math.ceil(vbytes * feeRate).toLong()
			val swept = inSat - fee
			if (swept <= dust)
				throw IllegalStateException("Balance is too low to send after the fee.")
			val rawHex = BtcTx.buildAndSign(
					selected.map { BtcTx.Input(it.utxo.txHash, it.utxo.txPos, it.utxo.value, it.key) },
					listOf(BtcTx.Output(recipients[0].address, swept)), opReturn)
			val signedTx = org.bitcoinj.core.Transaction(BtcKeys.params,
					org.bitcoinj.core.Utils.HEX.decode(rawHex))
			val ourInputs = selected.associate {
				val script = org.bitcoinj.script.ScriptBuilder.createOutputScript(
						org.bitcoinj.core.SegwitAddress.fromKey(BtcKeys.params, it.key)).program
				("${it.utxo.txHash}:${it.utxo.txPos}") to
						PayJoin.InInfo(it.utxo.value, it.key, script)
			}
			return BtcSpend(signedTx, rawHex, ourInputs, -1, feeRate)
		}
		if (selectedUtxos != null) {
			selected.addAll(sorted)
			inSat = selected.sumOf { it.utxo.value }
			val vbytes = BtcTx.estimateVBytes(selected.size, numOutBase) + opExtra
			fee = Math.ceil(vbytes * feeRate).toLong()
			if (inSat < amountTotal + fee)
				throw IllegalStateException("Selected coins don't cover the amount plus fee.")
		} else {
			var need: Long
			do {
				val vbytes = BtcTx.estimateVBytes(selected.size.coerceAtLeast(1),
						numOutBase) + opExtra
				fee = Math.ceil(vbytes * feeRate).toLong()
				need = amountTotal + fee
				if (inSat >= need) break
				val next = sorted.getOrNull(selected.size)
						?: throw IllegalStateException("Insufficient BTC for amount plus fee.")
				selected.add(next); inSat += next.utxo.value
			} while (true)
		}

		val change = inSat - amountTotal - fee
		val outputs = recipients.toMutableList()
		if (silent != null) {
			if (!SilentPayment.selfTest())
				throw IllegalStateException("Silent Payments self-check failed.")
			val privs = selected.map { it.key.privKeyBytes }
			val outpoints = selected.map {
				org.bitcoinj.core.Utils.HEX.decode(it.utxo.txHash).reversedArray() +
						byteArrayOf(it.utxo.txPos.toByte(), (it.utxo.txPos ushr 8).toByte(),
								(it.utxo.txPos ushr 16).toByte(), (it.utxo.txPos ushr 24).toByte())
			}
			val xonly = SilentPayment.deriveOutputs(privs, outpoints, silent.first,
					silent.second, 1)[0]
			val p2tr = byteArrayOf(0x51, 0x20) + xonly
			outputs.add(BtcTx.Output(null, silent.third, p2tr))
		}
		var changeIndex = -1
		if (change > dust) {
			outputs.add(BtcTx.Output(
					BtcKeys.changeAddress(m, acct.index, acct.btcChange), change))
			changeIndex = outputs.size - 1
		}
		val inputs = selected.map {
			BtcTx.Input(it.utxo.txHash, it.utxo.txPos, it.utxo.value, it.key)
		}
		val rawHex = BtcTx.buildAndSign(inputs, outputs, opReturn)
		val signedTx = org.bitcoinj.core.Transaction(BtcKeys.params,
				org.bitcoinj.core.Utils.HEX.decode(rawHex))
		val ourInputs = selected.associate {
			val script = org.bitcoinj.script.ScriptBuilder.createOutputScript(
					org.bitcoinj.core.SegwitAddress.fromKey(BtcKeys.params, it.key)).program
			("${it.utxo.txHash}:${it.utxo.txPos}") to
					PayJoin.InInfo(it.utxo.value, it.key, script)
		}
		return BtcSpend(signedTx, rawHex, ourInputs, changeIndex, feeRate)
	}


	fun isPayjoinUri(uri: String): Boolean = PayJoin.parseUri(uri) != null

	fun sendBtcPayjoin(uri: String, onDone: (String?, Boolean) -> Unit) {
		val m = activeMnemonic ?: return onDone("Wallet locked.", false)
		val acct = currentAcct
		val endpoint = PayJoin.parseUri(uri)
				?: return onDone("That isn't a PayJoin (BIP21 pj=) URI.", false)
		if (!BtcKeys.isValidBtcAddress(endpoint.address)) {
			return onDone("The PayJoin URI has an invalid Bitcoin address.", false)
		}
		if (endpoint.amountSat <= 0) {
			return onDone("The PayJoin URI has no amount. Ask for a URI that " +
					"includes the amount.", false)
		}
		busy = true; error = null
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					val (host, port) = parseServer(btcServer)
					ElectrumClient(host, port, socksPort).use { c ->
						val spend = assembleBtcTx(c, m, acct,
								listOf(BtcTx.Output(endpoint.address, endpoint.amountSat)), null)
						val ourOutpoints = spend.ourInputs.keys.toList()
						val finalHex = try {
							PayJoin.request(spend.signedTx, spend.ourInputs,
									spend.changeOutputIndex, spend.feeRateSatPerVb,
									endpoint, socksPort)
						} catch (e: Exception) {
							null
						}
						val payjoined = if (finalHex != null) {
							broadcastRaw(c, finalHex, ourOutpoints, endpoint.amountSat)
							true
						} else {
							broadcastRaw(c, spend.rawHex, ourOutpoints,
									endpoint.amountSat)
							false
						}
						Triple(true, payjoined,
								BigDecimal(endpoint.amountSat).movePointLeft(8))
					}
				} catch (e: Exception) {
					Triple(false, false, BigDecimal.ZERO)
				}
			}
			busy = false
			if (result.first) {
				refreshBtc(false)
				onDone(null, result.second)
			} else onDone("PayJoin send failed over Tor. Nothing was broadcast.", false)
		}
	}


	fun applyXmrNode(node: String) {
		val clean = node.trim().replace(Regex("[\\t\\n\\r]"), "")
		if (clean.isEmpty() || clean == xmrNode) return
		xmrNode = clean
		val restart = selectedChain == Chain.XMR && activeMnemonic != null
		persistThen {
			if (restart) scope.launch {
				withContext(Dispatchers.IO) { stopXmrBlocking() }
				startXmr()
			}
		}
	}

	fun applyXmrTrusted(enabled: Boolean) {
		if (enabled == xmrTrusted) return
		xmrTrusted = enabled
		val restart = selectedChain == Chain.XMR && activeMnemonic != null
		persistThen {
			if (restart) scope.launch {
				withContext(Dispatchers.IO) { stopXmrBlocking() }
				startXmr()
			}
		}
	}


	enum class NodeHealth { CHECKING, GREEN, ORANGE, RED }

	var nodeHealth by mutableStateOf<Map<String, NodeHealth>>(emptyMap())
		private set

	private fun setHealth(url: String, h: NodeHealth) {
		nodeHealth = nodeHealth + (url to h)
	}

	fun checkNodeHealth(chain: Chain, rawUrl: String) {
		val url = rawUrl.trim()
		if (url.isEmpty()) return
		setHealth(url, NodeHealth.CHECKING)
		scope.launch {
			val h = withContext(Dispatchers.IO) {
				val start = System.currentTimeMillis()
				try {
					when (chain) {
						Chain.BTC -> {
							val (host, port) = parseServer(url)
							ElectrumClient(host, port, socksPort).use { it.blockHeight() }
						}
						Chain.XMR -> {
							val body = TorHttp.get(xmrHealthUrl(url), socksPort)
									?: error("no response")
							if (!body.contains("\"height\"")) error("bad response")
						}
						else -> EthRpc(url, socksPort).chainId()
					}
					if (System.currentTimeMillis() - start < 6000) NodeHealth.GREEN
					else NodeHealth.ORANGE
				} catch (e: Exception) {
					NodeHealth.RED
				}
			}
			setHealth(url, h)
		}
	}

	private fun xmrHealthUrl(node: String): String {
		val base = if (node.startsWith("http")) node else "http://$node"
		return base.trimEnd('/') + "/get_height"
	}

	fun refreshXmr() = refreshXmr(true)

	private fun startXmr() {
		val m = activeMnemonic ?: return
		val id = activeWalletId ?: return
		if (moneroRpc?.isRunning == true) { refreshXmr(true); return }
		lastXmrHeight = 0L
		xmrStatus = "Starting Monero…"
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					stopXmrBlocking()
					val dir = java.io.File(moneroBaseDir, id)
					val rpc = MoneroRpc(dir, xmrNode, socksPort, xmrTrusted)
					try {
						rpc.start()
						try {
							rpc.openWallet("w", xmrRpcPassword)
						} catch (e: Exception) {
							rpc.restoreWallet("w", xmrRpcPassword, m, xmrRestoreHeight)
						}
						moneroRpc = rpc
						true
					} catch (e: Exception) {
						try { rpc.stop() } catch (ignored: Exception) {}
						false
					}
				} catch (e: Exception) {
					false
				}
			}
			if (ok) { xmrStatus = "Syncing…"; refreshXmr(true) }
			else { xmrStatus = null; error = "Couldn't open the Monero wallet over Tor." }
		}
	}

	private fun stopXmr() {
		scope.launch { withContext(Dispatchers.IO) { stopXmrBlocking() } }
	}

	private fun stopXmrBlocking() {
		val rpc = moneroRpc
		moneroRpc = null
		try { rpc?.stop() } catch (e: Exception) {}
	}

	private fun refreshXmr(showBusy: Boolean) {
		val rpc = moneroRpc ?: return
		if (showBusy) busy = true
		scope.launch {
			val acct = xmrAccount
			val res = withContext(Dispatchers.IO) {
				try {
					val bal = rpc.balance(acct)
					val addr = rpc.primaryAddress(acct)
					val hist = rpc.transfers(acct).map { t ->
						TxRecord(t.hash, "", BigDecimal(t.amount).movePointLeft(12)
								.setScale(12, RoundingMode.DOWN).stripTrailingZeros()
								.toPlainString(), "XMR", t.timestamp, t.incoming,
								if (t.confirmed) "confirmed" else "pending")
					}.sortedByDescending { it.timestamp }
					val accts = try {
						rpc.accounts().map { XmrAccountUi(it.index, it.label) }
					} catch (e: Exception) { null }
					val height = try { rpc.walletHeight() } catch (e: Exception) { 0L }
					listOf(bal, addr, hist, accts, height)
				} catch (e: Exception) { null }
			}
			if (showBusy) busy = false
			if (res != null) {
				@Suppress("UNCHECKED_CAST")
				val bal = res[0] as MoneroRpc.Balance
				val xmr = BigDecimal(bal.total).movePointLeft(12)
				xmrBalance = xmr.setScale(12, RoundingMode.DOWN).stripTrailingZeros()
						.toPlainString()
				if (xmrAddress.isEmpty()) xmrAddress = res[1] as String
				@Suppress("UNCHECKED_CAST")
				xmrHistory = res[2] as List<TxRecord>
				@Suppress("UNCHECKED_CAST")
				(res[3] as? List<XmrAccountUi>)?.let { if (it.isNotEmpty()) xmrAccounts = it }
				val price = prices["XMR"]
				xmrUsd = if (price != null) fiatFormat(xmr, price) else null
				val height = res[4] as Long
				val prev = lastXmrHeight
				lastXmrHeight = height
				val syncing = height > 0 && (prev == 0L || height - prev > 2)
				xmrStatus = if (syncing) "Syncing block %,d…".format(height) else null
				error = null
			} else if (showBusy) {
				xmrStatus = null
				error = "Couldn't reach the Monero wallet over Tor. Try another " +
						"node in Monero settings."
			}
		}
	}

	fun newXmrAddress() {
		val rpc = moneroRpc
		if (rpc == null) {
			error = "The Monero wallet is still starting. Try again in a moment."
			return
		}
		val acct = xmrAccount
		busy = true
		scope.launch {
			val a = withContext(Dispatchers.IO) {
				try { rpc.createSubaddress(acct) } catch (e: Exception) { null }
			}
			busy = false
			if (!a.isNullOrEmpty()) xmrAddress = a
			else error = "Couldn't create a new Monero address. Check the node in " +
					"Monero settings."
		}
	}

	fun addXmrAccount() {
		val rpc = moneroRpc ?: return
		busy = true
		scope.launch {
			val idx = withContext(Dispatchers.IO) {
				try { rpc.createAccount() } catch (e: Exception) { null }
			}
			busy = false
			if (idx != null) selectXmrAccount(idx)
		}
	}

	fun selectXmrAccount(index: Int) {
		if (index == xmrAccount && xmrAddress.isNotEmpty()) return
		xmrAccount = index
		xmrAddress = ""; xmrBalance = null; xmrUsd = null; xmrHistory = emptyList()
		refreshXmr(true)
	}

	data class XmrPlanInfo(val fingerprint: String,
			val destinations: List<Pair<String, String>>,
			val amountAtomic: BigInteger, val feeAtomic: BigInteger)

	private val xmrAuth = XmrSendAuthorizer()

	fun cancelXmrSend() {
		xmrAuth.cancel()
	}

	fun prepareXmrSend(recipients: List<Recipient>, priority: Int,
			onDone: (XmrPlanInfo?, String?) -> Unit) {
		xmrAuth.cancel()
		val rpc = moneroRpc ?: return onDone(null, "Monero wallet not ready.")
		if (recipients.isEmpty()) return onDone(null, "Add at least one recipient.")
		val dests = mutableListOf<Pair<String, BigInteger>>()
		for (r in recipients) {
			val amount = r.amount.trim().toBigDecimalOrNull()
			if (amount == null || amount <= BigDecimal.ZERO)
				return onDone(null, "Enter a valid amount for each recipient.")
			dests.add(r.address.trim() to amount.movePointRight(12).toBigInteger())
		}
		busy = true; error = null
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					for ((addr, _) in dests) {
						if (!rpc.validateAddress(addr))
							return@withContext (null as XmrPlanInfo?) to
									"Not a valid Monero address: ${addr.take(12)}…"
					}
					val prep = rpc.prepareTransfer(dests, priority, xmrAccount)
					val displayDests = dests.map { (a, v) ->
						a to v.toBigDecimal().movePointLeft(12).toPlainString()
					}
					val planBody = dests.joinToString("|") { (a, v) -> "$a:$v" }
					val fp = sha256Hex(prep.metadata + "|" + planBody + "|" +
							prep.fee.toString() + "|" + xmrAccount)
					xmrAuth.store(XmrSendAuthorizer.Prepared(fp, prep.metadata,
							prep.amount, prep.fee, displayDests))
					XmrPlanInfo(fp, displayDests, prep.amount, prep.fee) to null
				} catch (e: Exception) {
					(null as XmrPlanInfo?) to
							"Couldn't prepare the transaction: ${e.message ?: "error"}"
				}
			}
			busy = false
			onDone(result.first, result.second)
		}
	}

	fun confirmXmrSend(plan: XmrPlanInfo, password: CharArray,
			onDone: (String?) -> Unit) {
		verifyWalletPassword(password) { ok ->
			val rpc = moneroRpc
			busy = true; error = null
			scope.launch {
				val result = withContext(Dispatchers.IO) {
					xmrAuth.confirm(plan.fingerprint, ok && rpc != null) { meta ->
						rpc!!.relayTx(meta)
					}
				}
				busy = false
				when (result) {
					XmrSendAuthorizer.Result.SENT -> { refreshXmr(false); onDone(null) }
					XmrSendAuthorizer.Result.AUTH_FAILED ->
						onDone("Incorrect wallet password.")
					XmrSendAuthorizer.Result.EXPIRED ->
						onDone("This review expired. Please review again.")
					XmrSendAuthorizer.Result.RELAY_FAILED ->
						onDone("Transaction failed. Please review and try again.")
				}
			}
		}
	}
	private fun parseServer(s: String): Pair<String, Int> {
		val idx = s.lastIndexOf(':')
		return if (idx > 0) s.substring(0, idx) to (s.substring(idx + 1).toIntOrNull() ?: 50001)
		else s to 50001
	}


	fun beginCreate(coin: String) {
		pendingCoin = coin
		if (coin != "XMR") {
			pendingMnemonic = WalletKeys.generateMnemonic()
			return
		}
		busy = true; error = null; creatingXmr = true
		pendingXmrHeight = XMR_HEIGHT_FALLBACK
		scope.launch {
			val gen = withContext(Dispatchers.IO) {
				val dir = java.io.File(moneroBaseDir, "tmp-" + UUID.randomUUID())
				var rpc: MoneroRpc? = null
				try {
					rpc = MoneroRpc(dir, "", 0)
					rpc.start()
					val pw = Base64.getUrlEncoder().withoutPadding()
							.encodeToString(VaultCrypto.randomBytes(18))
					rpc.createWallet("gen", pw)
					rpc.mnemonic() to pw
				} catch (e: Exception) {
					null
				} finally {
					try { rpc?.stop() } catch (e: Exception) {}
					secureWipeTree(dir)
				}
			}
			busy = false; creatingXmr = false
			if (gen == null) {
				error = "Couldn't generate the Monero wallet. Please try again."
				return@launch
			}
			pendingMnemonic = gen.first
			pendingXmrRpcPw = gen.second
			withContext(Dispatchers.IO) {
				val h = fetchXmrHeight(xmrNode, socksPort)
				if (h > 0) pendingXmrHeight = h
			}
		}
	}

	private fun fetchXmrHeight(node: String, socks: Int): Long {
		if (socks <= 0) return 0L
		val resp = TorHttp.get("http://$node/get_height", socks) ?: return 0L
		val m = "\"height\":"
		val i = resp.indexOf(m); if (i < 0) return 0L
		var s = i + m.length; var e = s
		while (e < resp.length && resp[e].isDigit()) e++
		return resp.substring(s, e).toLongOrNull() ?: 0L
	}
	fun cancelCreate() { pendingMnemonic = null; creatingXmr = false }

	fun finishCreate(name: String, password: CharArray, onDone: (Boolean) -> Unit) {
		val m = pendingMnemonic ?: return onDone(false)
		createWallet(name, pendingCoin, m, password) { ok ->
			if (ok) pendingMnemonic = null; onDone(ok)
		}
	}

	fun importWallet(name: String, coin: String, words: String, password: CharArray,
			onDone: (Boolean) -> Unit) {
		val m = words.trim().lowercase().replace(Regex("\\s+"), " ")
		if (!WalletKeys.isValidMnemonic(m)) {
			Arrays.fill(password, ' ')
			error = "That doesn't look like a valid recovery phrase."
			return onDone(false)
		}
		createWallet(name, coin, m, password, onDone)
	}

	fun importXmrWallet(name: String, seed: String, restoreHeight: Long,
			password: CharArray, onDone: (Boolean) -> Unit) {
		val words = seed.trim().lowercase().replace(Regex("\\s+"), " ")
		val count = if (words.isEmpty()) 0 else words.split(' ').size
		if (count != 25 && count != 24 && count != 13) {
			Arrays.fill(password, ' ')
			error = "A Monero recovery phrase is 25 words (some are 13 or 24)."
			return onDone(false)
		}
		val height = restoreHeight.coerceAtLeast(0)
		busy = true; error = null
		scope.launch {
			val valid = withContext(Dispatchers.IO) {
				val dir = java.io.File(moneroBaseDir, "chk-" + UUID.randomUUID())
				var rpc: MoneroRpc? = null
				try {
					rpc = MoneroRpc(dir, "", 0)
					rpc.start()
					val chkPw = Base64.getUrlEncoder().withoutPadding()
							.encodeToString(VaultCrypto.randomBytes(18))
					rpc.restoreWallet("chk", chkPw, words, height)
					true
				} catch (e: Exception) {
					false
				}
				finally { try { rpc?.stop() } catch (e: Exception) {}; secureWipeTree(dir) }
			}
			if (!valid) {
				busy = false
				Arrays.fill(password, ' ')
				error = "That Monero phrase didn't validate (check the words and node)."
				return@launch onDone(false)
			}
			pendingXmrRpcPw = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(VaultCrypto.randomBytes(18))
			pendingXmrHeight = height
			createWallet(name, "XMR", words, password, onDone)
		}
	}

	private fun createWallet(name: String, coin: String, m: String,
			password: CharArray, onDone: (Boolean) -> Unit) {
		val clean = name.trim().ifBlank { "Wallet ${wallets.size + 1}" }
				.replace(Regex("[\\t\\n\\r]"), " ")
		busy = true; error = null
		scope.launch {
			val id = UUID.randomUUID().toString()
			val ok = withContext(Dispatchers.IO) {
				try {
					walletSalt = VaultCrypto.randomBytes(32)
					walletKdf = VaultArgon2.choose()
					val key = VaultArgon2.deriveKey(password, walletSalt, walletKdf)
					try {
						val blob = VaultCrypto.encrypt(m.toByteArray(Charsets.UTF_8),
								key, SEED_AAD).toBytes()
						manager.putSecret(seedKey(id), Base64.getEncoder().encode(blob))
					} finally { Arrays.fill(key, 0) }
					acctList = mutableListOf(Acct(0, "Account 1", 0))
					localTx = mutableListOf(); customTokens = mutableListOf()
					selectedAccount = 0; nodeUrl = DEFAULT_NODE
					if (coin == "XMR") {
						xmrRpcPassword = pendingXmrRpcPw
						xmrRestoreHeight = pendingXmrHeight
					}
					manager.putSecret(cfgKey(id), buildCfg().toByteArray(Charsets.UTF_8))
					true
				} catch (e: Exception) { false }
				finally { Arrays.fill(password, ' ') }
			}
			busy = false
			if (ok) {
				openSeeds[id] = m
				wallets = wallets + WalletMeta(id, clean, coin)
				withContext(Dispatchers.IO) { runCatching { saveIndex() } }
				activeWalletId = id; walletLocked = false
				applyCoinChain()
				publishAccounts(); refreshView()
			} else error = "Couldn't create the wallet."
			onDone(ok)
		}
	}

	fun unlockActive(password: CharArray, onDone: (Boolean) -> Unit) {
		val id = activeWalletId ?: run { Arrays.fill(password, ' '); return onDone(false) }
		busy = true; error = null
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					parseCfg(manager.getSecret(cfgKey(id))?.toString(Charsets.UTF_8))
					val key = VaultArgon2.deriveKey(password, walletSalt, walletKdf)
					val m = try {
						val blob = Base64.getDecoder().decode(manager.getSecret(seedKey(id)))
						String(VaultCrypto.decrypt(
								VaultCrypto.EncryptedData.fromBytes(blob), key, SEED_AAD),
								Charsets.UTF_8)
					} finally { Arrays.fill(key, 0) }
					openSeeds[id] = m
					true
				} catch (e: Exception) { false }
				finally { Arrays.fill(password, ' ') }
			}
			busy = false
			if (ok) { walletLocked = false; applyCoinChain(); publishAccounts(); refreshView() }
			else error = "Incorrect wallet password."
			onDone(ok)
		}
	}

	fun deleteWallet(id: String) {
		scope.launch {
			withContext(Dispatchers.IO) {
				runCatching { manager.deleteSecret(seedKey(id)) }
				runCatching { manager.deleteSecret(cfgKey(id)) }
			}
			openSeeds.remove(id)
			wallets = wallets.filter { it.id != id }
			withContext(Dispatchers.IO) { runCatching { saveIndex() } }
			if (activeWalletId == id) {
				val next = wallets.firstOrNull()?.id
				if (next == null) {
					activeWalletId = null; walletLocked = true; clearActiveDisplay()
				} else selectWallet(next)
			}
		}
	}

	fun renameWallet(name: String) {
		val id = activeWalletId ?: return
		val clean = name.trim().replace(Regex("[\\t\\n\\r]"), " ").ifBlank { return }
		wallets = wallets.map { if (it.id == id) it.copy(name = clean) else it }
		scope.launch { withContext(Dispatchers.IO) { runCatching { saveIndex() } } }
	}

	fun applyFiatCurrency(code: String) {
		fiatCurrency = code.uppercase()
		prices = emptyMap()
		persistThen {
			fetchPrices()
			if (selectedChain == Chain.BTC) refreshBtc(false)
		}
	}

	fun verifyWalletPassword(password: CharArray, onResult: (Boolean) -> Unit) {
		val id = activeWalletId
		val active = activeMnemonic
		if (id == null || active == null) {
			Arrays.fill(password, ' '); return onResult(false)
		}
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				try {
					val key = VaultArgon2.deriveKey(password, walletSalt, walletKdf)
					val m = try {
						val blob = Base64.getDecoder().decode(manager.getSecret(seedKey(id)))
						String(VaultCrypto.decrypt(
								VaultCrypto.EncryptedData.fromBytes(blob), key, SEED_AAD),
								Charsets.UTF_8)
					} finally { Arrays.fill(key, 0) }
					m == active
				} catch (e: Exception) { false }
				finally { Arrays.fill(password, ' ') }
			}
			onResult(ok)
		}
	}

	fun revealSeed(password: CharArray, onResult: (String?) -> Unit) {
		val active = activeMnemonic
		verifyWalletPassword(password) { ok -> onResult(if (ok) active else null) }
	}

	fun changeWalletPassword(old: CharArray, new: CharArray, onDone: (String?) -> Unit) {
		val id = activeWalletId
		val m = activeMnemonic
		if (id == null || m == null) {
			Arrays.fill(old, ' '); Arrays.fill(new, ' '); return onDone("Wallet locked.")
		}
		scope.launch {
			val err = withContext(Dispatchers.IO) {
				try {
					val oldKey = VaultArgon2.deriveKey(old, walletSalt, walletKdf)
					val stored = try {
						val blob = Base64.getDecoder().decode(manager.getSecret(seedKey(id)))
						String(VaultCrypto.decrypt(
								VaultCrypto.EncryptedData.fromBytes(blob), oldKey, SEED_AAD),
								Charsets.UTF_8)
					} finally { Arrays.fill(oldKey, 0) }
					if (stored != m) return@withContext "Current password is incorrect."
					val newSalt = VaultCrypto.randomBytes(32)
					val newKdf = VaultArgon2.choose()
					val newKey = VaultArgon2.deriveKey(new, newSalt, newKdf)
					val blob = try {
						VaultCrypto.encrypt(m.toByteArray(Charsets.UTF_8), newKey, SEED_AAD)
								.toBytes()
					} finally { Arrays.fill(newKey, 0) }
					manager.putSecret(seedKey(id), Base64.getEncoder().encode(blob))
					walletSalt = newSalt; walletKdf = newKdf
					persistConfig()
					null
				} catch (e: Exception) { "Couldn't change the password." }
				finally { Arrays.fill(old, ' '); Arrays.fill(new, ' ') }
			}
			onDone(err)
		}
	}

	private fun loadActiveState() {
		val id = activeWalletId ?: return
		scope.launch {
			io { parseCfg(manager.getSecret(cfgKey(id))?.toString(Charsets.UTF_8)) }
			applyCoinChain()
			publishAccounts(); refreshView()
		}
	}


	fun addAccount(name: String) {
		if (activeMnemonic == null) return
		val clean = name.trim().ifBlank { "Account ${acctList.size + 1}" }
				.replace(Regex("[\\t\\n\\r]"), " ")
		val nextIndex = (acctList.maxOfOrNull { it.index } ?: -1) + 1
		acctList.add(Acct(nextIndex, clean, 0))
		selectedAccount = nextIndex
		persistThen { publishAccounts(); refreshView() }
	}

	fun selectAccount(index: Int) {
		if (index == selectedAccount) return
		selectedAccount = index
		persistThen { refreshView() }
	}

	fun newReceiveAddress() {
		val acct = currentAcct
		acctList = acctList.map {
			if (it.index == acct.index) it.copy(receiveIndex = it.receiveIndex + 1) else it
		}.toMutableList()
		persistThen { deriveAddresses() }
	}

	fun setNode(url: String) {
		val clean = url.trim().replace(Regex("[\\t\\n\\r]"), "")
		if (clean.isEmpty()) return
		nodeUrl = clean
		persistThen { refreshBalance() }
	}

	fun addCustomToken(contract: String, onDone: (String?) -> Unit) {
		val c = contract.trim().lowercase()
		if (!WalletKeys.isValidEthAddress(c)) return onDone("Not a valid contract address.")
		if (allTokens().any { it.contract?.lowercase() == c }) return onDone("Token already added.")
		scope.launch {
			val spec = withContext(Dispatchers.IO) {
				try {
					val rpc = EthRpc(nodeUrl, socksPort)
					val decimals = rpc.ethCall(c, "0x313ce567").toInt()
					val symbol = rpc.decodeAbiString(rpc.ethCallString(c, "0x95d89b41"))
							.ifBlank { "TOKEN" }
					TokenSpec(symbol, c, decimals)
				} catch (e: Exception) { null }
			}
			if (spec == null) onDone("Couldn't read that token over Tor.")
			else {
				customTokens.add(spec)
				persistThen { refreshBalance() }
				onDone(null)
			}
		}
	}


	private fun refreshView() {
		fetchPrices()
		startPolling()
		when (selectedChain) {
			Chain.BTC -> {
				val m = activeMnemonic; val acct = currentAcct
				if (m != null) scope.launch {
					val a = io { BtcKeys.address(m, acct.index, acct.btcRecv) }
					if (a != null) btcReceiveAddress = a
					val sp = io { BtcKeys.silentPaymentAddress(m, acct.index) }
					if (sp != null) btcSilentAddress = sp
				}
				refreshBtc(true)
			}
			Chain.XMR -> startXmr()
			else -> {
				deriveAddresses()
				refreshBalance()
				fetchRemoteHistory()
				recomputeHistory()
			}
		}
	}

	private fun deriveAddresses() {
		val m = activeMnemonic ?: return
		val acct = currentAcct
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					val primary = WalletKeys.ethAddress(m, acct.index, 0)
					val recv = (0..acct.receiveIndex).map {
						ReceiveAddress(it, WalletKeys.ethAddress(m, acct.index, it))
					}
					primary to recv
				} catch (e: Exception) { null }
			}
			if (result != null) {
				primaryAddress = result.first; receiveAddresses = result.second
			}
		}
	}

	fun refreshBalance() = refreshBalance(showBusy = true)

	private fun refreshBalance(showBusy: Boolean) {
		val m = activeMnemonic ?: return
		val acct = currentAcct
		if (showBusy) busy = true
		scope.launch {
			val result = withContext(Dispatchers.IO) {
				try {
					val rpc = EthRpc(nodeUrl, socksPort)
					val addresses = (0..acct.receiveIndex).map {
						WalletKeys.ethAddress(m, acct.index, it)
					}
					allTokens().map { token ->
						var raw = BigInteger.ZERO
						for (addr in addresses) {
							raw = raw.add(if (token.contract == null)
								rpc.getBalanceWei(addr)
							else rpc.ethCall(token.contract, erc20BalanceOfData(addr)))
						}
						BigDecimal(raw).movePointLeft(token.decimals) to token
					}
				} catch (e: Exception) { null }
			}
			if (showBusy) busy = false
			if (result != null) {
				assetBalances = result.map { (amount, token) ->
					makeAssetBalance(token, amount)
				}
				balanceEth = assetBalances.firstOrNull { it.symbol == "ETH" }?.formatted
				recomputeTotalUsd()
				error = null
			} else if (showBusy) error = "Couldn't reach the node over Tor."
		}
	}

	private fun makeAssetBalance(token: TokenSpec, amount: BigDecimal): AssetBalance {
		val price = prices[token.symbol]
		val usd = if (price != null) fiatFormat(amount, price) else null
		return AssetBalance(token.symbol, amount, formatAmount(amount, token), usd)
	}

	private fun recomputeTotalUsd() {
		if (prices.isEmpty()) { totalUsd = null; return }
		var sum = BigDecimal.ZERO
		assetBalances.forEach { ab ->
			prices[ab.symbol]?.let { sum = sum.add(ab.amount.multiply(BigDecimal(it))) }
		}
		totalUsd = fiatSymbol + sum.setScale(2, RoundingMode.DOWN).toPlainString()
	}

	private fun fetchPrices() {
		scope.launch {
			val map = withContext(Dispatchers.IO) {
				val ids = Builtin.entries.joinToString(",") { it.priceId } + ",bitcoin,monero"
				val cur = fiatCurrency.lowercase()
				val resp = TorHttp.get("https://api.coingecko.com/api/v3/simple/" +
						"price?ids=$ids&vs_currencies=$cur", socksPort) ?: return@withContext null
				val m = Builtin.entries.mapNotNull { b ->
					extractPrice(resp, b.priceId, cur)?.let { b.symbol to it }
				}.toMutableList()
				extractPrice(resp, "bitcoin", cur)?.let { m.add("BTC" to it) }
				extractPrice(resp, "monero", cur)?.let { m.add("XMR" to it) }
				m.toMap()
			}
			if (!map.isNullOrEmpty()) {
				prices = map
				assetBalances = assetBalances.map { ab ->
					val price = prices[ab.symbol]
					ab.copy(usd = if (price != null) fiatFormat(ab.amount, price) else ab.usd)
				}
				recomputeTotalUsd()
			}
		}
	}

	private fun fetchRemoteHistory() {
		val m = activeMnemonic ?: return
		val acct = currentAcct
		scope.launch {
			val list = withContext(Dispatchers.IO) {
				try {
					val ours = (0..acct.receiveIndex)
							.map { WalletKeys.ethAddress(m, acct.index, it).lowercase() }
							.toSet()
					val out = mutableListOf<TxRecord>()
					for (addr in ours) {
						TorHttp.get("https://eth.blockscout.com/api?module=account" +
								"&action=txlist&address=$addr&sort=desc&page=1&offset=20",
								socksPort)?.let { parseBlockscout(it, ours, out, false) }
						TorHttp.get("https://eth.blockscout.com/api?module=account" +
								"&action=tokentx&address=$addr&sort=desc&page=1&offset=20",
								socksPort)?.let { parseBlockscout(it, ours, out, true) }
					}
					out.distinctBy { it.hash + it.symbol + it.amount }
				} catch (e: Exception) { null }
			}
			if (list != null) { remoteHistory = list; recomputeHistory() }
		}
	}

	private fun recomputeHistory() {
		val local = localTx.filter { it.first == selectedAccount }.map { it.second }
		val remoteHashes = remoteHistory.map { it.hash.lowercase() }.toSet()
		val pendingLocal = local.filter { it.hash.lowercase() !in remoteHashes }
		history = (remoteHistory + pendingLocal).sortedByDescending { it.timestamp }
	}


	fun send(assetSymbol: String, to: String, amountStr: String, onDone: (String?) -> Unit) {
		val m = activeMnemonic ?: return onDone("Wallet locked.")
		val acct = currentAcct
		val token = allTokens().firstOrNull { it.symbol == assetSymbol }
				?: return onDone("Unknown asset.")
		val toAddr = to.trim()
		if (!WalletKeys.isValidEthAddress(toAddr)) {
			return onDone("That is not a valid Ethereum address. ${token.symbol} can " +
					"only be sent to a 0x… Ethereum address — not a Bitcoin, Solana " +
					"or other-chain address.")
		}
		val amount = amountStr.trim().toBigDecimalOrNull()
		if (amount == null || amount <= BigDecimal.ZERO) return onDone("Enter a valid amount.")
		busy = true; error = null
		scope.launch {
			val result = withContext(Dispatchers.IO) { doSend(m, acct, token, toAddr, amount) }
			busy = false
			val txs = result.first
			if (txs != null) {
				txs.forEach { sent ->
					recordTx(acct.index, TxRecord(sent.hash, toAddr,
							sent.amount.toPlainString(), token.symbol,
							System.currentTimeMillis(), false, "pending"))
				}
				recomputeHistory()
				refreshBalance(showBusy = false)
			}
			onDone(result.second)
		}
	}

	private fun doSend(m: String, acct: Acct, token: TokenSpec, toAddr: String,
			amount: BigDecimal): Pair<List<SentTx>?, String?> {
		return try {
			val rpc = EthRpc(nodeUrl, socksPort)
			val chainId = rpc.chainId().toLong()
			val priority = rpc.maxPriorityFeePerGas()
			val base = rpc.baseFeePerGas()
			val maxFee = base.multiply(BigInteger.TWO).add(priority)
					.max(priority.max(rpc.gasPrice()))

			if (token.contract == null) {
				sendEthMultiInput(rpc, m, acct, toAddr,
						Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger(),
						chainId, priority, maxFee)
			} else {
				sendToken(rpc, m, acct, token, toAddr,
						amount.movePointRight(token.decimals).toBigInteger(),
						chainId, priority, maxFee)
			}
		} catch (e: Exception) {
			null to "Transaction failed: ${e.message ?: "network error"}"
		}
	}

	private fun sendEthMultiInput(rpc: EthRpc, m: String, acct: Acct, toAddr: String,
			valueWei: BigInteger, chainId: Long, priority: BigInteger,
			maxFee: BigInteger): Pair<List<SentTx>?, String?> {
		val feeWei = GAS_LIMIT.multiply(maxFee)
		data class Src(val index: Int, val spendable: BigInteger)
		val sources = (0..acct.receiveIndex).mapNotNull { i ->
			val bal = rpc.getBalanceWei(WalletKeys.ethAddress(m, acct.index, i))
			val spendable = bal.subtract(feeWei)
			if (spendable > BigInteger.ZERO) Src(i, spendable) else null
		}.sortedByDescending { it.spendable }
		val available = sources.fold(BigInteger.ZERO) { a, s -> a.add(s.spendable) }
		if (available < valueWei) return null to "Insufficient ETH for amount plus gas."

		val sent = mutableListOf<SentTx>()
		var remaining = valueWei
		try {
			for (s in sources) {
				if (remaining <= BigInteger.ZERO) break
				val portion = remaining.min(s.spendable)
				val creds = WalletKeys.ethCredentials(m, acct.index, s.index)
				val nonce = rpc.transactionCount(creds.address)
				val raw = RawTransaction.createTransaction(chainId, nonce, GAS_LIMIT,
						toAddr, portion, "", priority, maxFee)
				val hash = rpc.sendRawTransaction(
						Numeric.toHexString(TransactionEncoder.signMessage(raw, creds)))
				sent.add(SentTx(hash, BigDecimal(portion).movePointLeft(18)))
				remaining = remaining.subtract(portion)
			}
		} catch (e: Exception) {
			if (sent.isNotEmpty()) return sent to ("Partly sent: ${sent.size} " +
					"transfer(s) went through but one failed. Check Transactions.")
			return null to "Transaction failed: ${e.message ?: "network error"}"
		}
		return sent to null
	}

	private fun sendToken(rpc: EthRpc, m: String, acct: Acct, token: TokenSpec,
			toAddr: String, units: BigInteger, chainId: Long, priority: BigInteger,
			maxFee: BigInteger): Pair<List<SentTx>?, String?> {
		val feeWei = TOKEN_GAS.multiply(maxFee)
		for (i in 0..acct.receiveIndex) {
			val addr = WalletKeys.ethAddress(m, acct.index, i)
			val tokenBal = rpc.ethCall(token.contract!!, erc20BalanceOfData(addr))
			if (tokenBal >= units && rpc.getBalanceWei(addr) >= feeWei) {
				val creds = WalletKeys.ethCredentials(m, acct.index, i)
				val nonce = rpc.transactionCount(creds.address)
				val data = erc20TransferData(toAddr, units)
				val raw = RawTransaction.createTransaction(chainId, nonce, TOKEN_GAS,
						token.contract, BigInteger.ZERO, data, priority, maxFee)
				val hash = rpc.sendRawTransaction(
						Numeric.toHexString(TransactionEncoder.signMessage(raw, creds)))
				return listOf(SentTx(hash,
						BigDecimal(units).movePointLeft(token.decimals))) to null
			}
		}
		return null to "No single address holds enough ${token.symbol} plus ETH for gas."
	}


	private var pollJob: Job? = null

	private fun startPolling() {
		pollJob?.cancel()
		pollJob = scope.launch {
			while (true) {
				delay(POLL_MS)
				if (walletLocked || activeMnemonic == null) continue
				fetchPrices()
				when (selectedChain) {
					Chain.ETH -> {
						refreshBalance(showBusy = false)
						fetchRemoteHistory()
						updatePendingStatuses()
					}
					Chain.BTC -> refreshBtc(showBusy = false)
					Chain.XMR -> refreshXmr(false)
				}
			}
		}
	}

	private fun stopPolling() { pollJob?.cancel(); pollJob = null }

	private fun updatePendingStatuses() {
		val pending = localTx.filter { it.second.status == "pending" }
		if (pending.isEmpty()) return
		scope.launch {
			val updates = withContext(Dispatchers.IO) {
				val rpc = try { EthRpc(nodeUrl, socksPort) }
						catch (e: Exception) { return@withContext emptyList<Pair<String, String>>() }
				pending.mapNotNull { (_, tx) ->
					val status = try { rpc.txStatus(tx.hash) } catch (e: Exception) { null }
					if (status != null) tx.hash to status else null
				}
			}
			var changed = false
			for ((hash, status) in updates) {
				val pos = localTx.indexOfFirst { it.second.hash == hash }
				if (pos >= 0 && localTx[pos].second.status != status) {
					localTx[pos] = localTx[pos].first to localTx[pos].second.copy(status = status)
					changed = true
				}
			}
			if (changed) {
				runCatching { withContext(Dispatchers.IO) { persistConfig() } }
				fetchRemoteHistory(); recomputeHistory()
			}
		}
	}

	fun clearSecrets() {
		stopPolling()
		stopXmr()
		openSeeds.clear()
		xmrAuth.cancel()
		xmrRpcPassword = ""; pendingXmrRpcPw = ""
		activeWalletId = null; walletLocked = true; wallets = emptyList()
		pendingMnemonic = null
		remoteHistory = emptyList(); prices = emptyMap()
		contacts = emptyList(); btcUtxos = emptyList()
		clearActiveDisplay()
	}

	private fun secureWipeTree(dir: java.io.File) = MoneroResidue.wipeTree(dir)

	private fun sweepMoneroResidue() = MoneroResidue.sweep(moneroBaseDir)

	fun shutdown() {
		stopPolling()
		stopXmrBlocking()
		clearSecrets()
	}


	private fun clearActiveDisplay() {
		accounts = emptyList(); balanceEth = null; assetBalances = emptyList()
		totalUsd = null; receiveAddresses = emptyList(); primaryAddress = ""
		history = emptyList()
		btcBalance = null; btcUsd = null; btcReceiveAddress = ""; btcSilentAddress = ""
		btcHistory = emptyList()
		spUtxos = emptyList(); spScanning = false
		xmrBalance = null; xmrUsd = null; xmrAddress = ""; xmrHistory = emptyList()
		xmrStatus = null
		xmrAccount = 0; xmrAccounts = listOf(XmrAccountUi(0, "Account 1"))
	}

	private fun persistThen(after: () -> Unit) {
		scope.launch {
			withContext(Dispatchers.IO) { runCatching { persistConfig() } }
			after()
		}
	}

	private fun persistConfig() {
		val id = activeWalletId ?: return
		manager.putSecret(cfgKey(id), buildCfg().toByteArray(Charsets.UTF_8))
	}

	private fun recordTx(account: Int, tx: TxRecord) {
		localTx.add(account to tx)
		persistThen { }
	}

	private fun publishAccounts() {
		accounts = acctList.map { AccountUi(it.index, it.name) }
	}

	private fun buildCfg(): String {
		val sb = StringBuilder()
		sb.append("node=").append(nodeUrl).append('\n')
		sb.append("selected=").append(selectedAccount).append('\n')
		sb.append("wsalt=").append(Base64.getEncoder().encodeToString(walletSalt)).append('\n')
		sb.append("wkdf=").append(walletKdf.memoryKb).append(',')
				.append(walletKdf.iterations).append(',').append(walletKdf.parallelism)
				.append('\n')
		sb.append("fiat=").append(fiatCurrency).append('\n')
		sb.append("2fa=").append(if (require2fa) 1 else 0).append('\n')
		sb.append("btcserver=").append(btcServer).append('\n')
		sb.append("sporacle=").append(spOracle).append('\n')
		sb.append("spbirthday=").append(spBirthday).append('\n')
		sb.append("spscanned=").append(spScannedHeight).append('\n')
		spUtxos.forEach {
			sb.append("sputxo=").append(it.txid).append('\t').append(it.vout).append('\t')
					.append(it.valueSat).append('\t').append(it.xonlyHex).append('\t')
					.append(it.tweakHex).append('\n')
		}
		sb.append("xmrpw=").append(xmrRpcPassword).append('\n')
		sb.append("xmrheight=").append(xmrRestoreHeight).append('\n')
		sb.append("xmrnode=").append(xmrNode).append('\n')
		sb.append("xmrtrusted=").append(if (xmrTrusted) 1 else 0).append('\n')
		acctList.forEach {
			sb.append("acct=").append(it.index).append('\t').append(it.name)
					.append('\t').append(it.receiveIndex).append('\t').append(it.btcRecv)
					.append('\t').append(it.btcChange).append('\n')
		}
		customTokens.forEach {
			sb.append("token=").append(it.symbol).append('\t').append(it.contract)
					.append('\t').append(it.decimals).append('\n')
		}
		localTx.forEach { (acc, tx) ->
			sb.append("tx=").append(acc).append('\t').append(tx.hash).append('\t')
					.append(tx.to).append('\t').append(tx.amount).append('\t')
					.append(tx.symbol).append('\t').append(tx.status).append('\t')
					.append(tx.timestamp).append('\n')
		}
		return sb.toString()
	}

	private fun parseCfg(cfg: String?) {
		if (cfg == null) return
		val accts = mutableListOf<Acct>()
		val txs = mutableListOf<Pair<Int, TxRecord>>()
		val tokens = mutableListOf<TokenSpec>()
		val sps = mutableListOf<SpUtxo>()
		spOracle = ""; spBirthday = 0; spScannedHeight = 0
		var sel = 0
		cfg.lineSequence().forEach { line ->
			when {
				line.startsWith("node=") -> nodeUrl = line.substring(5)
				line.startsWith("sporacle=") -> spOracle = line.substring(9)
				line.startsWith("spbirthday=") ->
					spBirthday = line.substring(11).toIntOrNull() ?: 0
				line.startsWith("spscanned=") ->
					spScannedHeight = line.substring(10).toIntOrNull() ?: 0
				line.startsWith("sputxo=") -> {
					val p = line.substring(7).split('\t')
					if (p.size >= 5) sps.add(SpUtxo(p[0], p[1].toIntOrNull() ?: 0,
							p[2].toLongOrNull() ?: 0, p[3], p[4]))
				}
				line.startsWith("selected=") -> sel = line.substring(9).toIntOrNull() ?: 0
				line.startsWith("wsalt=") ->
					runCatching { walletSalt = Base64.getDecoder().decode(line.substring(6)) }
				line.startsWith("wkdf=") -> {
					val p = line.substring(5).split(',')
					if (p.size == 3) walletKdf = VaultArgon2.Params(p[0].toInt(),
							p[1].toInt(), p[2].toInt())
				}
				line.startsWith("fiat=") -> fiatCurrency = line.substring(5)
				line.startsWith("2fa=") -> require2fa = true
				line.startsWith("btcserver=") -> btcServer = line.substring(10)
				line.startsWith("xmrpw=") -> xmrRpcPassword = line.substring(6)
				line.startsWith("xmrheight=") ->
					xmrRestoreHeight = line.substring(10).toLongOrNull() ?: 0L
				line.startsWith("xmrnode=") -> xmrNode = line.substring(8)
				line.startsWith("xmrtrusted=") -> xmrTrusted = line.substring(11) == "1"
				line.startsWith("acct=") -> {
					val p = line.substring(5).split('\t')
					if (p.size >= 3) accts.add(Acct(p[0].toInt(), p[1],
							p[2].toIntOrNull() ?: 0,
							p.getOrNull(3)?.toIntOrNull() ?: 0,
							p.getOrNull(4)?.toIntOrNull() ?: 0))
				}
				line.startsWith("token=") -> {
					val p = line.substring(6).split('\t')
					if (p.size >= 3) tokens.add(TokenSpec(p[0], p[1], p[2].toIntOrNull() ?: 18))
				}
				line.startsWith("tx=") -> {
					val p = line.substring(3).split('\t')
					if (p.size >= 7) txs.add(p[0].toInt() to TxRecord(p[1], p[2], p[3],
							p[4], p[6].toLongOrNull() ?: 0, false, p[5]))
				}
			}
		}
		acctList = if (accts.isNotEmpty()) accts else mutableListOf(Acct(0, "Account 1", 0))
		localTx = txs
		customTokens = tokens
		spUtxos = sps
		selectedAccount = if (acctList.any { it.index == sel }) sel else acctList.first().index
	}

	private fun saveIndex() {
		val sb = StringBuilder()
		wallets.forEach {
			sb.append("w=").append(it.id).append('\t').append(it.name).append('\t')
					.append(it.coin).append('\n')
		}
		manager.putSecret(INDEX, sb.toString().toByteArray(Charsets.UTF_8))
	}

	private fun loadIndex(): List<WalletMeta> {
		val cfg = manager.getSecret(INDEX)?.toString(Charsets.UTF_8) ?: return emptyList()
		return cfg.lineSequence().mapNotNull { line ->
			if (line.startsWith("w=")) {
				val p = line.substring(2).split('\t')
				if (p.size >= 2) WalletMeta(p[0], p[1], p.getOrNull(2) ?: "MULTI")
				else null
			} else null
		}.toList()
	}

	private fun parseBlockscout(resp: String, ours: Set<String>,
			out: MutableList<TxRecord>, isToken: Boolean) {
		val start = resp.indexOf("\"result\":[")
		if (start < 0) return
		val body = resp.substring(start)
		Regex("\\{[^{}]*}").findAll(body).forEach { match ->
			val obj = match.value
			val hash = field(obj, "hash") ?: return@forEach
			val to = field(obj, "to") ?: ""
			val from = field(obj, "from") ?: ""
			val value = field(obj, "value")?.toBigIntegerOrNull() ?: BigInteger.ZERO
			val ts = (field(obj, "timeStamp")?.toLongOrNull() ?: 0L) * 1000L
			val incoming = to.lowercase() in ours
			val symbol: String
			val decimals: Int
			if (isToken) {
				symbol = field(obj, "tokenSymbol")?.take(10) ?: "TOKEN"
				decimals = field(obj, "tokenDecimal")?.toIntOrNull() ?: 18
			} else {
				if (value == BigInteger.ZERO) return@forEach
				symbol = "ETH"; decimals = 18
			}
			val amount = BigDecimal(value).movePointLeft(decimals)
					.setScale(6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
			out.add(TxRecord(hash, if (incoming) from else to, amount, symbol, ts,
					incoming, "confirmed"))
		}
	}

	private fun field(json: String, key: String): String? {
		val marker = "\"$key\":\""
		val i = json.indexOf(marker); if (i < 0) return null
		val start = i + marker.length; val end = json.indexOf('"', start)
		return if (end < 0) null else json.substring(start, end)
	}

	private fun extractPrice(resp: String, id: String, cur: String): Double? {
		val marker = "\"$id\":{\"$cur\":"
		val i = resp.indexOf(marker); if (i < 0) return null
		val start = i + marker.length
		var end = start
		while (end < resp.length && (resp[end].isDigit() || resp[end] == '.')) end++
		return resp.substring(start, end).toDoubleOrNull()
	}

	private fun fiatFormat(amount: BigDecimal, price: Double): String =
			fiatSymbol + amount.multiply(BigDecimal(price))
					.setScale(2, RoundingMode.DOWN).toPlainString()

	private fun erc20BalanceOfData(owner: String): String =
			"0x70a08231" + owner.removePrefix("0x").lowercase().padStart(64, '0')

	private fun erc20TransferData(to: String, units: BigInteger): String =
			"0xa9059cbb" + to.removePrefix("0x").lowercase().padStart(64, '0') +
					units.toString(16).padStart(64, '0')

	private fun formatAmount(amount: BigDecimal, token: TokenSpec): String =
			if (token.contract == null)
				amount.setScale(6, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
			else amount.setScale(2, RoundingMode.DOWN).toPlainString()

	private fun seedKey(id: String) = "wallet.$id.seed"
	private fun cfgKey(id: String) = "wallet.$id.cfg"

	private suspend fun <T> io(block: () -> T): T? = withContext(Dispatchers.IO) {
		try { block() } catch (e: Exception) { null }
	}

	private companion object {
		const val DEFAULT_NODE = "https://ethereum-rpc.publicnode.com"
		const val DEFAULT_ELECTRUM = "electrum.blockstream.info:50001"
		const val DEFAULT_MONERO_NODE = "node.monerodevs.org:18089"
		const val XMR_HEIGHT_FALLBACK = 3_400_000L
		val FIATS = linkedMapOf(
				"USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥",
				"CHF" to "Fr", "CAD" to "$", "AUD" to "$", "CNY" to "¥",
				"INR" to "₹", "BRL" to "R$", "RUB" to "₽", "KRW" to "₩",
				"TRY" to "₺", "MXN" to "$", "ZAR" to "R", "SEK" to "kr",
				"NOK" to "kr", "PLN" to "zł", "SGD" to "$", "AED" to "AED ")
		const val INDEX = "wallets.index"
		const val ADDRESS_BOOK = "wallet.addressbook"
		const val BROADCASTING = "broadcasting"
		const val POSSIBLY_SENT = "possibly_sent"
		const val SENT = "sent"
		const val FAILED = "failed"
		const val PENDING_GRACE_MS = 10L * 60 * 1000
		const val PENDING_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
		const val MAX_SP_BLOCKS_PER_SCAN = 1000
		val BACKUP_MAGIC = byteArrayOf(0x5A, 0x57, 0x42, 0x4B)
		val BACKUP_AAD = "zerion-wallet-backup".toByteArray(Charsets.UTF_8)
		const val POLL_MS = 12_000L
		val GAS_LIMIT: BigInteger = BigInteger.valueOf(21000)
		val TOKEN_GAS: BigInteger = BigInteger.valueOf(90000)
		val SEED_AAD = "wallet-seed-v1".toByteArray(Charsets.UTF_8)
	}
}
