package pm.gnosis.heimdall.data.repositories

import im.status.keycard.io.CardChannel
import io.reactivex.Completable
import io.reactivex.Single
import pm.gnosis.crypto.ECDSASignature
import pm.gnosis.model.Solidity

interface KeyCardRepository {

    fun loadKeyCards(): Single<List<KeyCardInfo>>

    fun loadKeyCard(id: String): Single<KeyCardInfo>

    fun pairKeyCard(channel: CardChannel, pin: String, pairingKey: String, label: String): Solidity.Address

    fun signWithKeyCard(channel: CardChannel, hash: ByteArray): Pair<Solidity.Address, ECDSASignature>

    data class KeyCardInfo(val cardId: String, val label: String, val paired: Long, val pairing: String)
}
