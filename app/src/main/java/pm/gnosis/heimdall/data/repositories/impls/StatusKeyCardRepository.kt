package pm.gnosis.heimdall.data.repositories.impls

import im.status.keycard.applet.*
import im.status.keycard.io.CardChannel
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import pm.gnosis.crypto.ECDSASignature
import pm.gnosis.crypto.KeyPair
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.heimdall.data.repositories.KeyCardRepository
import pm.gnosis.heimdall.helpers.AppPreferencesManager
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.edit
import pm.gnosis.svalinn.security.EncryptionManager
import pm.gnosis.utils.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusKeyCardRepository @Inject constructor(
    private val encryptionManager: EncryptionManager,
    appPreferencesManager: AppPreferencesManager
) : KeyCardRepository {

    private val pairingPreferences = appPreferencesManager.get(PREFERENCES_PAIRING_INFO)

    override fun loadKeyCards(): Single<List<KeyCardRepository.KeyCardInfo>> =
        Single.fromCallable {
            pairingPreferences.all.mapNotNull {
                (it.value as? String)?.let { encoded -> decodeCardInfo(it.key, encoded) }
            }
        }
            .subscribeOn(Schedulers.io())

    private fun decodeCardInfo(id: String, encoded: String) =
        encoded.split(";").let {
            val pairing = it.getOrNull(0)?.let { crypted ->
                encryptionManager.decrypt(EncryptionManager.CryptoData.fromString(crypted)).utf8String()
            } ?: return@let null
            val name = it.getOrNull(1) ?: "Unknown card"
            val date = it.getOrNull(2)?.toLongOrNull() ?: 0
            KeyCardRepository.KeyCardInfo(id, name, date, pairing)
        }

    override fun loadKeyCard(id: String): Single<KeyCardRepository.KeyCardInfo> =
        Single.fromCallable {
            getKeyCardInfo(id) ?: throw NoSuchElementException()
        }
            .subscribeOn(Schedulers.io())

    private fun getKeyCardInfo(id: String) =
        pairingPreferences.getString(id, null)?.let { decodeCardInfo(id, it) }

    private fun addKeyCard(id: String, pairing: String, label: String) {
        val encryptedPairing = encryptionManager.encrypt(pairing.toByteArray()).toString()
        val cleanLabel = label.replace(";", " ")
        pairingPreferences.edit { putString(id, "$encryptedPairing;$cleanLabel;${System.currentTimeMillis()}") }
    }

    override fun pairKeyCard(channel: CardChannel, pin: String, pairingKey: String, label: String): Solidity.Address {
        var cardId: String? = null
        var cmdSet: KeycardCommandSet? = null
        try {
            cmdSet = KeycardCommandSet(channel)
            val info = ApplicationInfo(cmdSet.select().checkOK().data)
            if (!info.isInitializedCard) throw IllegalStateException("Card not initialized")

            val cid = info.instanceUID.toHex()
            cardId = cid
            if (info.hasSecureChannelCapability()) {
                getKeyCardInfo(info.instanceUID.toHex())?.let {
                    cmdSet.pairing = Pairing(it.pairing)
                } ?: run {
                    cmdSet.autoPair(pairingKey)
                    addKeyCard(cid, cmdSet.pairing.toBase64(), label)
                }
                cmdSet.autoOpenSecureChannel()
            }

            cmdSet.verifyPIN(pin).checkAuthOK()

            if (!info.hasMasterKey() && info.hasKeyManagementCapability()) {
                cmdSet.generateKey()
            }

            cmdSet.setPinlessPath(PINLESS_PATH).checkOK()

            cmdSet.setNDEF(byteArrayOf())

            val hash = Sha3Utils.keccak("Gnosis".toByteArray())
            val signature = RecoverableSignature(hash, cmdSet.signPinless(hash).checkOK().data)
            val walletPublicKey = BIP32KeyPair(null, null, signature.publicKey)
            val walletAddress = walletPublicKey.toEthereumAddress().toHexString().asEthereumAddress()

            KeyPair.recoverFromSignature(
                signature.recId,
                ECDSASignature(signature.r.asBigInteger(), signature.s.asBigInteger()),
                hash
            )?.address?.asBigInteger()?.let {
                if (Solidity.Address(it) != walletAddress) throw IllegalStateException("Illegal card address")
            } ?: throw IllegalStateException("Could not recover signature")

            return walletAddress ?: throw IllegalStateException("Illegal card address")
        } catch (e: Throwable) {
            if (cmdSet?.pairing != null) cmdSet.autoUnpair()
            cardId?.let { pairingPreferences.edit { remove(cardId) } }
            throw e
        }
    }

    override fun signWithKeyCard(channel: CardChannel, hash: ByteArray): Pair<Solidity.Address, ECDSASignature> {
        val cmdSet = KeycardCommandSet(channel)
        val info = ApplicationInfo(cmdSet.select().checkOK().data)
        if (!info.isInitializedCard) throw IllegalStateException("Card not initialized")

        val cardInfo = getKeyCardInfo(info.instanceUID.toHex()) ?: throw IllegalStateException("Unknown card")
        if (info.hasSecureChannelCapability()) {
            cmdSet.pairing = Pairing(cardInfo.pairing)
            cmdSet.autoOpenSecureChannel()
        }

        // cmdSet.setPinlessPath(PINLESS_PATH).checkOK() // Required PIN

        val signature = RecoverableSignature(hash, cmdSet.signPinless(hash).checkOK().data)
        val walletPublicKey = BIP32KeyPair(null, null, signature.publicKey)
        val walletAddress =
            walletPublicKey.toEthereumAddress().toHexString().asEthereumAddress() ?: throw IllegalStateException("Illegal card address")

        return walletAddress to ECDSASignature(signature.r.asBigInteger(), signature.s.asBigInteger()).apply { v = (signature.recId + 27).toByte() }
    }

    companion object {
        private const val PREFERENCES_PAIRING_INFO = "preferences.status_key_card_repository.pairing_info"
        private const val PINLESS_PATH = "m/44'/60'/0'/0/0"
    }

}
