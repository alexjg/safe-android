package pm.gnosis.heimdall.data.repositories.models

import pm.gnosis.heimdall.data.db.models.ERC20TokenDb
import pm.gnosis.model.Solidity
import pm.gnosis.utils.stringWithNoTrailingZeroes
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.math.RoundingMode.DOWN

data class ERC20Token(
    val address: Solidity.Address,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val logoUrl: String = "",
    val displayDecimals: Int = DEFAULT_DISPLAY_DECIMALS
) {
    fun convertAmount(unscaledAmount: BigInteger): BigDecimal =
        BigDecimal(unscaledAmount).setScale(decimals).div(BigDecimal.TEN.pow(decimals))

    fun displayString(amount: BigInteger, showSymbol: Boolean = true, decimalsToDisplay: Int = displayDecimals, roundingMode: RoundingMode = DOWN) =
        "${convertAmount(amount).setScale(decimalsToDisplay, roundingMode).stringWithNoTrailingZeroes()}${if(showSymbol) " $symbol" else ""}"

    companion object {
        const val DEFAULT_DISPLAY_DECIMALS = 5
        val ETHER_TOKEN = ERC20Token(Solidity.Address(BigInteger.ZERO), decimals = 18, symbol = "ETH", name = "Ether")
    }
}

data class ERC20TokenWithBalance(val token: ERC20Token, val balance: BigInteger? = null) {
    //FIXME: should symbol be always part of the balance string or should it be presenters job to decide how to display it
    fun displayString(showSymbol: Boolean = true, roundingMode: RoundingMode = DOWN) =
        balance?.let {
            token.displayString(it, showSymbol, roundingMode = roundingMode)
        } ?: "-"
}

fun ERC20Token.toDb() = ERC20TokenDb(address, name, symbol, decimals, logoUrl)
fun ERC20TokenDb.fromDb() = ERC20Token(address, name, symbol, decimals, logoUrl)
