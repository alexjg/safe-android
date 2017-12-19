package pm.gnosis.heimdall.ui.transactions.details.assets

import android.content.Context
import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.toOptional
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.*
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.heimdall.StandardToken
import pm.gnosis.heimdall.common.utils.DataResult
import pm.gnosis.heimdall.common.utils.ErrorResult
import pm.gnosis.heimdall.common.utils.Result
import pm.gnosis.heimdall.data.repositories.*
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.data.repositories.models.ERC20Token.Companion.ETHER_TOKEN
import pm.gnosis.heimdall.data.repositories.models.ERC20TokenWithBalance
import pm.gnosis.heimdall.ui.transactions.details.assets.AssetTransferDetailsContract.*
import pm.gnosis.heimdall.ui.transactions.exceptions.TransactionInputException
import pm.gnosis.heimdall.ui.transactions.exceptions.TransactionInputException.Companion.AMOUNT_FIELD
import pm.gnosis.heimdall.ui.transactions.exceptions.TransactionInputException.Companion.TOKEN_FIELD
import pm.gnosis.heimdall.ui.transactions.exceptions.TransactionInputException.Companion.TO_FIELD
import pm.gnosis.heimdall.ui.transactions.exceptions.TransactionInputException.Companion.VALUE_FIELD
import pm.gnosis.model.Solidity
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.tests.utils.ImmediateSchedulersRule
import pm.gnosis.tests.utils.MockUtils
import pm.gnosis.tests.utils.mockGetString
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class AssetTransferDetailsViewModelTest {
    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var detailsRepository: TransactionDetailsRepository

    @Mock
    lateinit var tokenRepository: TokenRepository

    private lateinit var viewModel: AssetTransferDetailsViewModel

    @Before
    fun setUp() {
        mockContext.mockGetString()
        viewModel = AssetTransferDetailsViewModel(mockContext, detailsRepository, tokenRepository)
    }

    @Test
    fun loadFormDataNoTransaction() {
        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(null, false).subscribe(testObserver)

        testObserver.assertResult(FormData())
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadFormDataLoadingDetailsError() {
        val transaction = Transaction(BigInteger.ZERO)
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.error(IllegalStateException()))

        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(transaction, false).subscribe(testObserver)

        testObserver.assertResult(FormData())
        then(detailsRepository).should().loadTransactionDetails(transaction)
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadFormDataLoadingEtherTransfer() {
        val transaction = Transaction(BigInteger.ZERO, value = Wei(BigInteger.TEN))
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.ETHER_TRANSFER, null, transaction)))

        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(transaction, false).subscribe(testObserver)

        testObserver.assertResult(FormData(ETHER_TOKEN.address, BigInteger.ZERO, BigInteger.TEN, ETHER_TOKEN))
        then(detailsRepository).should().loadTransactionDetails(transaction)
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadFormDataLoadingGeneric() {
        val transaction = Transaction(BigInteger.ZERO)
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.GENERIC, null, transaction)))

        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(transaction, false).subscribe(testObserver)

        testObserver.assertResult(FormData(ETHER_TOKEN.address, BigInteger.ZERO, BigInteger.ZERO, ETHER_TOKEN))
        then(detailsRepository).should().loadTransactionDetails(transaction)
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadFormDataLoadingTokenTransferNoData() {
        val transaction = Transaction(BigInteger.ZERO)
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.TOKEN_TRANSFER, null, transaction)))

        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(transaction, false).subscribe(testObserver)

        testObserver.assertResult(FormData(ETHER_TOKEN.address, BigInteger.ZERO, BigInteger.ZERO, ETHER_TOKEN))
        then(detailsRepository).should().loadTransactionDetails(transaction)
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadFormDataLoadingTokenTransferKnownToken() {
        val token = ERC20Token(BigInteger.ZERO, decimals = 42)
        val transaction = Transaction(token.address)
        val transferData = TokenTransferData(BigInteger.ONE, BigInteger.TEN)
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.TOKEN_TRANSFER, transferData, transaction)))
        given(tokenRepository.loadToken(token.address))
                .willReturn(Single.just(token))

        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(transaction, false).subscribe(testObserver)

        testObserver.assertResult(FormData(token.address, BigInteger.ONE, BigInteger.TEN, token))
        then(detailsRepository).should().loadTransactionDetails(transaction)
        then(tokenRepository).should().loadToken(token.address)
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadFormDataLoadingTokenTransferUnknownToken() {
        val token = ERC20Token(BigInteger.ZERO, decimals = 42)
        val transaction = Transaction(token.address)
        val transferData = TokenTransferData(BigInteger.ONE, BigInteger.TEN)
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.TOKEN_TRANSFER, transferData, transaction)))
        given(tokenRepository.loadToken(token.address))
                .willReturn(Single.error(NoSuchElementException()))

        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(transaction, false).subscribe(testObserver)

        testObserver.assertResult(FormData(token.address, BigInteger.ONE, BigInteger.TEN, null))
        then(detailsRepository).should().loadTransactionDetails(transaction)
        then(tokenRepository).should().loadToken(token.address)
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadFormDataLoadingTokenTransferKeepDefaults() {
        val token = ERC20Token(BigInteger.ZERO, decimals = 42)
        val transaction = Transaction(token.address)
        val transferData = TokenTransferData(BigInteger.ZERO, BigInteger.ZERO)
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.TOKEN_TRANSFER, transferData, transaction)))
        given(tokenRepository.loadToken(token.address))
                .willReturn(Single.error(NoSuchElementException()))

        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(transaction, false).subscribe(testObserver)

        testObserver.assertResult(FormData(token.address, BigInteger.ZERO, BigInteger.ZERO, null))
        then(detailsRepository).should().loadTransactionDetails(transaction)
        then(tokenRepository).should().loadToken(token.address)
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun loadFormDataLoadingTokenTransferClearDefaults() {
        val token = ERC20Token(BigInteger.ZERO, decimals = 42)
        val transaction = Transaction(token.address)
        val transferData = TokenTransferData(BigInteger.ZERO, BigInteger.ZERO)
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.TOKEN_TRANSFER, transferData, transaction)))
        given(tokenRepository.loadToken(token.address))
                .willReturn(Single.error(NoSuchElementException()))

        val testObserver = TestObserver<FormData>()
        viewModel.loadFormData(transaction, true).subscribe(testObserver)

        testObserver.assertResult(FormData(token.address, null, null, null))
        then(detailsRepository).should().loadTransactionDetails(transaction)
        then(tokenRepository).should().loadToken(token.address)
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun observeTokens() {
        val verifiedTokenNoBalance = ERC20Token(BigInteger.valueOf(42), verified = true, decimals = 7)
        val verifiedTokenBalance = ERC20Token(BigInteger.valueOf(43), verified = true, decimals = 9)
        val verifiedTokenZeroBalance = ERC20Token(BigInteger.valueOf(41), verified = true, decimals = 10)
        given(tokenRepository.loadTokens())
                .willReturn(Single.just(listOf(verifiedTokenNoBalance, verifiedTokenBalance, verifiedTokenZeroBalance)))
        given(tokenRepository.loadTokenBalances(MockUtils.any(), MockUtils.any()))
                .willReturn(Observable.just(listOf(ETHER_TOKEN to null, verifiedTokenNoBalance to null, verifiedTokenBalance to BigInteger.TEN, verifiedTokenZeroBalance to BigInteger.ZERO)))

        val testObserver = TestObserver<State>()
        viewModel.observeTokens(BigInteger.ONE, BigInteger.TEN).subscribe(testObserver)

        testObserver.assertResult(State(0, listOf(
                ERC20TokenWithBalance(ETHER_TOKEN, null),
                ERC20TokenWithBalance(verifiedTokenNoBalance, null),
                ERC20TokenWithBalance(verifiedTokenBalance, BigInteger.TEN)
        )))
        then(tokenRepository).should().loadTokenBalances(BigInteger.TEN, listOf(ETHER_TOKEN, verifiedTokenNoBalance, verifiedTokenBalance, verifiedTokenZeroBalance))
        then(tokenRepository).should().loadTokens()
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun observeTokensBalanceError() {
        val verifiedTokenNoBalance = ERC20Token(BigInteger.valueOf(42), verified = true, decimals = 7)
        val verifiedTokenBalance = ERC20Token(BigInteger.valueOf(43), verified = true, decimals = 9)
        val verifiedTokenZeroBalance = ERC20Token(BigInteger.valueOf(41), verified = true, decimals = 10)
        given(tokenRepository.loadTokens())
                .willReturn(Single.just(listOf(verifiedTokenNoBalance, verifiedTokenBalance, verifiedTokenZeroBalance)))
        given(tokenRepository.loadTokenBalances(MockUtils.any(), MockUtils.any()))
                .willReturn(Observable.error(IllegalStateException()))

        val testObserver = TestObserver<State>()
        viewModel.observeTokens(BigInteger.ONE, BigInteger.TEN).subscribe(testObserver)

        testObserver.assertResult(State(0, listOf(
                ERC20TokenWithBalance(ETHER_TOKEN, null),
                ERC20TokenWithBalance(verifiedTokenNoBalance, null),
                ERC20TokenWithBalance(verifiedTokenBalance, null),
                ERC20TokenWithBalance(verifiedTokenZeroBalance, null)
        )))
        then(tokenRepository).should().loadTokenBalances(BigInteger.TEN, listOf(ETHER_TOKEN, verifiedTokenNoBalance, verifiedTokenBalance, verifiedTokenZeroBalance))
        then(tokenRepository).should().loadTokens()
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun observeTokensNoSafe() {
        val verifiedTokenNoBalance = ERC20Token(BigInteger.valueOf(42), verified = true, decimals = 7)
        val verifiedTokenBalance = ERC20Token(BigInteger.valueOf(43), verified = true, decimals = 9)
        val verifiedTokenZeroBalance = ERC20Token(BigInteger.valueOf(41), verified = true, decimals = 10)
        given(tokenRepository.loadTokens())
                .willReturn(Single.just(listOf(verifiedTokenNoBalance, verifiedTokenBalance, verifiedTokenZeroBalance)))

        val testObserver = TestObserver<State>()
        viewModel.observeTokens(BigInteger.valueOf(41), null).subscribe(testObserver)

        testObserver.assertResult(State(3, listOf(
                ERC20TokenWithBalance(ETHER_TOKEN, null),
                ERC20TokenWithBalance(verifiedTokenNoBalance, null),
                ERC20TokenWithBalance(verifiedTokenBalance, null),
                ERC20TokenWithBalance(verifiedTokenZeroBalance, null)
        )))
        then(tokenRepository).should().loadTokens()
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun observeTokensNoDefault() {
        val verifiedTokenNoBalance = ERC20Token(BigInteger.valueOf(42), verified = true, decimals = 7)
        val verifiedTokenBalance = ERC20Token(BigInteger.valueOf(43), verified = true, decimals = 9)
        val verifiedTokenZeroBalance = ERC20Token(BigInteger.valueOf(41), verified = true, decimals = 10)
        given(tokenRepository.loadTokens())
                .willReturn(Single.just(listOf(verifiedTokenNoBalance, verifiedTokenBalance, verifiedTokenZeroBalance)))

        val testObserver = TestObserver<State>()
        viewModel.observeTokens(null, null).subscribe(testObserver)

        testObserver.assertResult(State(0, listOf(
                ERC20TokenWithBalance(ETHER_TOKEN, null),
                ERC20TokenWithBalance(verifiedTokenNoBalance, null),
                ERC20TokenWithBalance(verifiedTokenBalance, null),
                ERC20TokenWithBalance(verifiedTokenZeroBalance, null)
        )))
        then(tokenRepository).should().loadTokens()
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    @Test
    fun observeTokensError() {
        given(tokenRepository.loadTokens())
                .willReturn(Single.error(IllegalStateException()))
        given(tokenRepository.loadTokenBalances(MockUtils.any(), MockUtils.any()))
                .willReturn(Observable.error(IllegalStateException()))

        val testObserver = TestObserver<State>()
        viewModel.observeTokens(null, BigInteger.TEN).subscribe(testObserver)

        testObserver.assertResult(State(0, listOf(
                ERC20TokenWithBalance(ETHER_TOKEN, null)
        )))
        then(tokenRepository).should().loadTokenBalances(BigInteger.TEN, listOf(ETHER_TOKEN))
        then(tokenRepository).should().loadTokens()
        then(detailsRepository).shouldHaveNoMoreInteractions()
        then(tokenRepository).shouldHaveNoMoreInteractions()
    }

    private fun testInputTransformer(inputStream: PublishSubject<InputEvent>, outputStream: TestObserver<Result<Transaction>>,
                                     input: InputEvent, expectedOutput: Result<Transaction>, testNo: Int) {
        inputStream.onNext(input)
        outputStream.assertNoErrors().assertValueCount(testNo)
                .assertValueAt(testNo - 1, expectedOutput)
    }

    @Test
    fun inputTransformerWithOriginalTransaction() {

        val testPublisher = PublishSubject.create<InputEvent>()
        val testObserver = TestObserver<Result<Transaction>>()
        val originalTransaction = Transaction(BigInteger.TEN, nonce = BigInteger.valueOf(1337))

        testPublisher.compose(viewModel.inputTransformer(originalTransaction))
                .subscribe(testObserver)
        testObserver.assertNoValues()

        var testNo = 1
        // Valid input with change (token)
        val tentenToken = ERC20Token(BigInteger.TEN, decimals = 10)
        val transferTo = Solidity.Address(BigInteger.ZERO)
        val transferAmount = Solidity.UInt256(BigInteger.valueOf(123).multiply(BigInteger.TEN.pow(tentenToken.decimals)))
        val expectedData = StandardToken.Transfer.encode(transferTo, transferAmount)
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0" to true, "123" to false, ERC20TokenWithBalance(tentenToken, null) to false),
                DataResult(Transaction(BigInteger.TEN, value = null,
                        data = expectedData, nonce = BigInteger.valueOf(1337))),
                testNo++
        )
        // Valid input with change (ether)
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0" to true, "123" to false, ERC20TokenWithBalance(ERC20Token.ETHER_TOKEN, null) to false),
                DataResult(Transaction(BigInteger.ZERO, value = Wei(BigInteger.valueOf(123).multiply(BigInteger.TEN.pow(ERC20Token.ETHER_TOKEN.decimals))),
                        data = null, nonce = BigInteger.valueOf(1337))),
                testNo++
        )
        // Invalid input with change
        // Third changed (compared to last value -> valid input)
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0" to false, "123" to true, null to false),
                ErrorResult(TransactionInputException(
                        mockContext, TOKEN_FIELD, true
                )),
                testNo++
        )
        // Second changed
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0" to false, "123y" to false, null to true),
                ErrorResult(TransactionInputException(
                        mockContext, AMOUNT_FIELD or TOKEN_FIELD, true
                )),
                testNo++
        )
        // First changed
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0t" to false, "123y" to false, null to true),
                ErrorResult(TransactionInputException(
                        mockContext, TO_FIELD or AMOUNT_FIELD or TOKEN_FIELD, true
                )),
                testNo++
        )
        // No change
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0t" to false, "123y" to false, null to false),
                ErrorResult(TransactionInputException(
                        mockContext, TO_FIELD or AMOUNT_FIELD or TOKEN_FIELD, false
                )),
                testNo++
        )
    }

    @Test
    fun inputTransformerNoOriginalTransaction() {

        val testPublisher = PublishSubject.create<InputEvent>()
        val testObserver = TestObserver<Result<Transaction>>()

        testPublisher.compose(viewModel.inputTransformer(null))
                .subscribe(testObserver)
        testObserver.assertNoValues()

        var testNo = 1
        // Valid input with change (token)
        val tentenToken = ERC20Token(BigInteger.TEN, decimals = 10)
        val transferTo = Solidity.Address(BigInteger.ZERO)
        val transferAmount = Solidity.UInt256(BigInteger.valueOf(123).multiply(BigInteger.TEN.pow(tentenToken.decimals)))
        val expectedData = StandardToken.Transfer.encode(transferTo, transferAmount)
        testPublisher.onNext(InputEvent("0x0" to true, "123" to false, ERC20TokenWithBalance(tentenToken, null) to false))
        testObserver.assertNoErrors().assertValueCount(testNo)
                .assertValueAt(testNo - 1, {
                    it is DataResult
                            && it.data.address == BigInteger.TEN
                            && it.data.value == null
                            && it.data.data == expectedData
                            && it.data.nonce != null
                            && it.data.nonce!! <= BigInteger.valueOf(System.currentTimeMillis())
                            && it.data.nonce!! >= BigInteger.valueOf(System.currentTimeMillis() - NONCE_WINDOW)
                })
        testNo++
        // Valid input with change (ether)
        testPublisher.onNext(InputEvent("0x0" to true, "123" to false, ERC20TokenWithBalance(ERC20Token.ETHER_TOKEN, null) to false))
        testObserver.assertNoErrors().assertValueCount(testNo)
                .assertValueAt(testNo - 1, {
                    it is DataResult
                            && it.data.address == BigInteger.ZERO
                            && it.data.value == Wei(BigInteger.valueOf(123).multiply(BigInteger.TEN.pow(ERC20Token.ETHER_TOKEN.decimals)))
                            && it.data.data == null
                            && it.data.nonce != null
                            && it.data.nonce!! <= BigInteger.valueOf(System.currentTimeMillis())
                            && it.data.nonce!! >= BigInteger.valueOf(System.currentTimeMillis() - NONCE_WINDOW)
                })
        testNo++
        // Invalid input with change
        // Third changed (compared to last value -> valid input)
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0" to false, "123" to true, null to false),
                ErrorResult(TransactionInputException(
                        mockContext, TOKEN_FIELD, true
                )),
                testNo++
        )
        // Second changed
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0" to false, "123y" to false, null to true),
                ErrorResult(TransactionInputException(
                        mockContext, AMOUNT_FIELD or TOKEN_FIELD, true
                )),
                testNo++
        )
        // First changed
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0t" to false, "123y" to false, null to true),
                ErrorResult(TransactionInputException(
                        mockContext, TO_FIELD or AMOUNT_FIELD or TOKEN_FIELD, true
                )),
                testNo++
        )
        // No change
        testInputTransformer(testPublisher, testObserver,
                InputEvent("0x0t" to false, "123y" to false, null to false),
                ErrorResult(TransactionInputException(
                        mockContext, TO_FIELD or AMOUNT_FIELD or TOKEN_FIELD, false
                )),
                testNo++
        )
    }

    private fun testTransactionTransformer(inputStream: PublishSubject<Optional<Transaction>>, outputStream: TestObserver<Result<Transaction>>,
                                           input: Transaction?, expectedOutput: Result<Transaction>, testNo: Int) {
        inputStream.onNext(input.toOptional())
        outputStream.assertNoErrors().assertValueCount(testNo)
                .assertValueAt(testNo - 1, expectedOutput)
    }

    @Test
    fun transactionTransformer() {

        val testPublisher = PublishSubject.create<Optional<Transaction>>()
        val testObserver = TestObserver<Result<Transaction>>()

        testPublisher.compose(viewModel.transactionTransformer())
                .subscribe(testObserver)
        testObserver.assertNoValues()

        var testNo = 1

        // No transaction passed
        testPublisher.onNext(None)
        testObserver.assertNoErrors().assertValueCount(testNo)
                .assertValueAt(testNo - 1, { it is ErrorResult && it.error is IllegalStateException })
        testNo++
        then(detailsRepository).shouldHaveNoMoreInteractions()
        reset(detailsRepository)

        // No asset transaction passed
        var transaction = Transaction(BigInteger.valueOf(42))
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.GENERIC, null, transaction)))
        testPublisher.onNext(transaction.toOptional())
        testObserver.assertNoErrors().assertValueCount(testNo)
                .assertValueAt(testNo - 1, { it is ErrorResult && it.error is IllegalStateException })
        testNo++
        then(detailsRepository).should().loadTransactionDetails(transaction)
        reset(detailsRepository)

        // Ether transaction action passed with no value
        transaction = Transaction(BigInteger.valueOf(42))
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.ETHER_TRANSFER, null, transaction)))
        testTransactionTransformer(testPublisher, testObserver,
                transaction, ErrorResult(TransactionInputException(mockContext, AMOUNT_FIELD, true)), testNo++)
        then(detailsRepository).should().loadTransactionDetails(transaction)
        reset(detailsRepository)

        // Ether transaction action passed with zero value
        transaction = Transaction(BigInteger.valueOf(42), value = Wei.ZERO)
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.ETHER_TRANSFER, null, transaction)))
        testTransactionTransformer(testPublisher, testObserver,
                transaction, ErrorResult(TransactionInputException(mockContext, AMOUNT_FIELD, true)), testNo++)
        then(detailsRepository).should().loadTransactionDetails(transaction)
        reset(detailsRepository)

        // Ether transaction action passed
        transaction = Transaction(BigInteger.valueOf(42), value = Wei(BigInteger.TEN))
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.ETHER_TRANSFER, null, transaction)))
        testTransactionTransformer(testPublisher, testObserver,
                transaction, DataResult(transaction), testNo++)
        then(detailsRepository).should().loadTransactionDetails(transaction)
        reset(detailsRepository)

        // Token transaction action passed no data
        transaction = Transaction(BigInteger.valueOf(23))
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.TOKEN_TRANSFER, null, transaction)))
        testTransactionTransformer(testPublisher, testObserver,
                transaction, ErrorResult(TransactionInputException(mockContext, AMOUNT_FIELD, true)), testNo++)
        then(detailsRepository).should().loadTransactionDetails(transaction)
        reset(detailsRepository)

        // Token transaction action passed zero tokens
        transaction = Transaction(BigInteger.valueOf(23))
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.TOKEN_TRANSFER, TokenTransferData(BigInteger.valueOf(42), BigInteger.ZERO), transaction)))
        testTransactionTransformer(testPublisher, testObserver,
                transaction, ErrorResult(TransactionInputException(mockContext, AMOUNT_FIELD, true)), testNo++)
        then(detailsRepository).should().loadTransactionDetails(transaction)
        reset(detailsRepository)

        // Ether transaction action passed
        transaction = Transaction(BigInteger.valueOf(42))
        given(detailsRepository.loadTransactionDetails(MockUtils.any()))
                .willReturn(Single.just(TransactionDetails(null, TransactionType.TOKEN_TRANSFER, TokenTransferData(BigInteger.valueOf(42), BigInteger.TEN), transaction)))
        testTransactionTransformer(testPublisher, testObserver,
                transaction, DataResult(transaction), testNo++)
        then(detailsRepository).should().loadTransactionDetails(transaction)
        reset(detailsRepository)
    }

    companion object {
        private const val NONCE_WINDOW = 10000L
    }

}