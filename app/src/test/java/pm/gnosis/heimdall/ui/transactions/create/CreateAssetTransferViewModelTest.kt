package pm.gnosis.heimdall.ui.transactions.create

import android.content.Context
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
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.data.repositories.models.ERC20TokenWithBalance
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.data.repositories.models.SemVer
import pm.gnosis.model.Solidity
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.common.utils.DataResult
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.tests.utils.ImmediateSchedulersRule
import pm.gnosis.tests.utils.MockUtils
import pm.gnosis.tests.utils.TestObservableFactory
import pm.gnosis.tests.utils.TestSingleFactory
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger
import java.util.concurrent.TimeoutException

@RunWith(MockitoJUnitRunner::class)
class CreateAssetTransferViewModelTest {

    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    private lateinit var contextMock: Context

    @Mock
    private lateinit var tokenRepositoryMock: TokenRepository

    @Mock
    private lateinit var relayRepositoryMock: TransactionExecutionRepository

    private lateinit var viewModel: CreateAssetTransferViewModel

    @Before
    fun setUp() {
        viewModel = CreateAssetTransferViewModel(contextMock, relayRepositoryMock, tokenRepositoryMock)
    }

    @Test
    fun loadPaymentToken() {
        given(tokenRepositoryMock.loadPaymentToken(MockUtils.any())).willReturn(Single.just(ERC20Token.ETHER_TOKEN))
        val testObserver = TestObserver<ERC20Token>()
        viewModel.loadPaymentToken(TEST_SAFE).subscribe(testObserver)
        testObserver.assertValues(ERC20Token.ETHER_TOKEN)
        then(tokenRepositoryMock).should().loadPaymentToken(TEST_SAFE)
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun processInputEtherTransfer() {
        given(tokenRepositoryMock.loadPaymentToken(MockUtils.any())).willReturn(Single.just(ERC20Token.ETHER_TOKEN))
        given(tokenRepositoryMock.loadToken(MockUtils.any())).willReturn(Single.just(ERC20Token.ETHER_TOKEN))
        val balancesSubject = PublishSubject.create<List<Pair<ERC20Token, BigInteger?>>>()
        given(tokenRepositoryMock.loadTokenBalances(MockUtils.any(), MockUtils.any())).willReturn(balancesSubject)
        val estimationSingleFactory = TestSingleFactory<TransactionExecutionRepository.ExecuteInformation>()
        given(relayRepositoryMock.loadExecuteInformation(MockUtils.any(), MockUtils.any(), MockUtils.any(), MockUtils.any())).willReturn(estimationSingleFactory.get())
        val reviewEvents = TestObservableFactory<Unit>()
        val inputSubject = PublishSubject.create<CreateAssetTransferContract.Input>()
        val testObserver = TestObserver<Result<CreateAssetTransferContract.ViewUpdate>>()
        inputSubject
            .compose(viewModel.processInput(TEST_SAFE, TEST_ETHER_TOKEN, reviewEvents.get()))
            .subscribe(testObserver)

        val updates = mutableListOf<Result<CreateAssetTransferContract.ViewUpdate>>(
            DataResult(CreateAssetTransferContract.ViewUpdate.TokenInfo(ERC20TokenWithBalance(ERC20Token.ETHER_TOKEN, null)))
        )
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Valid input for now
        inputSubject.onNext(CreateAssetTransferContract.Input("31.5", TEST_ADDRESS))

        // No token yet -> cannot validate amount
        updates.add(DataResult(CreateAssetTransferContract.ViewUpdate.InvalidInput(true, false)))
        testObserver.assertValuesOnly(*updates.toTypedArray())

        balancesSubject.onNext(listOf(ERC20Token.ETHER_TOKEN to TEST_ETH_AMOUNT))

        // Got token info
        updates.add(DataResult(CreateAssetTransferContract.ViewUpdate.TokenInfo(ERC20TokenWithBalance(ERC20Token.ETHER_TOKEN, TEST_ETH_AMOUNT))))
        // Not enough funds
        updates.add(DataResult(CreateAssetTransferContract.ViewUpdate.InvalidInput(true, false)))
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Valid input
        inputSubject.onNext(CreateAssetTransferContract.Input("22", TEST_ADDRESS))
        // No estimate = no update
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Estimate error
        val error = IllegalStateException()
        estimationSingleFactory.error(error)
        updates.add(DataResult(CreateAssetTransferContract.ViewUpdate.EstimateError(error)))
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Valid input -> retrigger estimate
        inputSubject.onNext(CreateAssetTransferContract.Input("22", TEST_ADDRESS))
        // No estimate = no update
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Estimate, balance too low
        val lowBalanceInfo = TransactionExecutionRepository.ExecuteInformation(
            TEST_TRANSACTION_HASH,
            TEST_TRANSACTION,
            TEST_OWNERS[2],
            TEST_OWNERS.size,
            TEST_OWNERS,
            SemVer(1, 0, 0),
            TEST_ETHER_TOKEN,
            BigInteger.ONE,
            BigInteger.TEN,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO
        )
        estimationSingleFactory.success(lowBalanceInfo)
        updates.add(
            DataResult(
                CreateAssetTransferContract.ViewUpdate.Estimate(
                    ERC20TokenWithBalance(ERC20Token.ETHER_TOKEN, Wei.ether("-22").value - BigInteger.TEN), BigInteger.TEN, null, false
                )
            )
        )
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Valid input -> retrigger estimate
        inputSubject.onNext(CreateAssetTransferContract.Input("22", TEST_ADDRESS))
        // No estimate = no update
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Estimate
        val validInfo = TransactionExecutionRepository.ExecuteInformation(
            TEST_TRANSACTION_HASH, TEST_TRANSACTION, TEST_OWNERS[2], TEST_OWNERS.size, TEST_OWNERS, SemVer(1, 0, 0),
            TEST_ETHER_TOKEN, BigInteger.ONE, BigInteger.TEN, BigInteger.ZERO, BigInteger.ZERO,
            Wei.ether("23").value
        )
        estimationSingleFactory.success(validInfo)
        updates.add(
            DataResult(
                CreateAssetTransferContract.ViewUpdate.Estimate(
                    ERC20TokenWithBalance(ERC20Token.ETHER_TOKEN, Wei.ether("1").value - BigInteger.TEN), BigInteger.TEN, null, true
                )
            )
        )
        testObserver.assertValuesOnly(*updates.toTypedArray())

        reviewEvents.success(Unit)
        // Cannot assert intent without predicate
        testObserver.assertValueCount(8)
        testObserver.assertValueAt(7) { it is DataResult && it.data is CreateAssetTransferContract.ViewUpdate.StartReview }

        then(tokenRepositoryMock).should(times(3)).loadPaymentToken(TEST_SAFE)
        then(tokenRepositoryMock).should().loadToken(TEST_ETHER_TOKEN)
        then(tokenRepositoryMock).should().loadTokenBalances(TEST_SAFE, listOf(ERC20Token.ETHER_TOKEN))
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(relayRepositoryMock).should(times(3)).loadExecuteInformation(MockUtils.any(), MockUtils.any(), MockUtils.any(), MockUtils.any())
        then(relayRepositoryMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun processInputTokenTransfer() {
        given(tokenRepositoryMock.loadPaymentToken(MockUtils.any())).willReturn(Single.just(TEST_GAS_TOKEN))
        val balancesSubject = PublishSubject.create<List<Pair<ERC20Token, BigInteger?>>>()
        given(tokenRepositoryMock.loadTokenBalances(MockUtils.any(), MockUtils.any())).willReturn(balancesSubject)
        val tokenSingleFactory = TestSingleFactory<ERC20Token>()
        given(tokenRepositoryMock.loadToken(MockUtils.any())).willReturn(tokenSingleFactory.get())
        val estimationSingleFactory = TestSingleFactory<TransactionExecutionRepository.ExecuteInformation>()
        given(relayRepositoryMock.loadExecuteInformation(MockUtils.any(), MockUtils.any(), MockUtils.any(), MockUtils.any())).willReturn(estimationSingleFactory.get())
        val reviewEvents = TestObservableFactory<Unit>()
        val inputSubject = PublishSubject.create<CreateAssetTransferContract.Input>()
        val testObserver = TestObserver<Result<CreateAssetTransferContract.ViewUpdate>>()
        inputSubject
            .compose(viewModel.processInput(TEST_SAFE, TEST_TOKEN_ADDRESS, reviewEvents.get()))
            .subscribe(testObserver)

        testObserver.assertEmpty()
        tokenSingleFactory.success(TEST_TOKEN)

        val updates = mutableListOf<Result<CreateAssetTransferContract.ViewUpdate>>(
            DataResult(CreateAssetTransferContract.ViewUpdate.TokenInfo(ERC20TokenWithBalance(TEST_TOKEN, null)))
        )
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Valid input for now
        inputSubject.onNext(CreateAssetTransferContract.Input("31.5", TEST_ADDRESS))

        // No token yet -> cannot validate amount
        updates.add(DataResult(CreateAssetTransferContract.ViewUpdate.InvalidInput(true, false)))
        testObserver.assertValuesOnly(*updates.toTypedArray())

        balancesSubject.onNext(listOf(TEST_TOKEN to TEST_TOKEN_AMOUNT))

        // Got token info
        updates.add(DataResult(CreateAssetTransferContract.ViewUpdate.TokenInfo(ERC20TokenWithBalance(TEST_TOKEN, TEST_TOKEN_AMOUNT))))
        // Not enough funds
        updates.add(DataResult(CreateAssetTransferContract.ViewUpdate.InvalidInput(true, false)))
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Valid input
        inputSubject.onNext(CreateAssetTransferContract.Input("22", TEST_ADDRESS))
        // No estimate = no update
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Estimate error
        val error = IllegalStateException()
        estimationSingleFactory.error(error)
        updates.add(DataResult(CreateAssetTransferContract.ViewUpdate.EstimateError(error)))
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Valid input -> retrigger estimate
        inputSubject.onNext(CreateAssetTransferContract.Input("22", TEST_ADDRESS))
        // No estimate = no update
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Estimate, balance too low
        val lowBalanceInfo = TransactionExecutionRepository.ExecuteInformation(
            TEST_TRANSACTION_HASH,
            TEST_TRANSACTION,
            TEST_OWNERS[2],
            TEST_OWNERS.size,
            TEST_OWNERS,
            SemVer(1, 0, 0),
            TEST_GAS_TOKEN.address,
            BigInteger.ONE,
            BigInteger.TEN,
            BigInteger.ZERO,
            BigInteger.ZERO,
            BigInteger.ZERO
        )
        estimationSingleFactory.success(lowBalanceInfo)
        tokenSingleFactory.success(ERC20Token.ETHER_TOKEN)
        updates.add(
            DataResult(
                CreateAssetTransferContract.ViewUpdate.Estimate(
                    ERC20TokenWithBalance(TEST_GAS_TOKEN, BigInteger.ZERO - BigInteger.TEN), BigInteger.TEN,
                    ERC20TokenWithBalance(TEST_TOKEN, BigInteger("10000000000")), false
                )
            )
        )
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Valid input -> retrigger estimate
        inputSubject.onNext(CreateAssetTransferContract.Input("22", TEST_ADDRESS))
        // No estimate = no update
        testObserver.assertValuesOnly(*updates.toTypedArray())

        // Estimate, enough balance
        val validInfo = TransactionExecutionRepository.ExecuteInformation(
            TEST_TRANSACTION_HASH, TEST_TRANSACTION, TEST_OWNERS[2], TEST_OWNERS.size, TEST_OWNERS, SemVer(1, 0, 0),
            TEST_ETHER_TOKEN, BigInteger.ONE, BigInteger.TEN, BigInteger.ZERO, BigInteger.ZERO,
            Wei.ether("1").value
        )
        estimationSingleFactory.success(validInfo)
        tokenSingleFactory.success(ERC20Token.ETHER_TOKEN)
        updates.add(
            DataResult(
                CreateAssetTransferContract.ViewUpdate.Estimate(
                    ERC20TokenWithBalance(TEST_GAS_TOKEN, Wei.ether("1").value - BigInteger.TEN),
                    BigInteger.TEN,
                    ERC20TokenWithBalance(TEST_TOKEN, BigInteger("10000000000")),
                    true
                )
            )
        )
        testObserver.assertValuesOnly(*updates.toTypedArray())

        reviewEvents.success(Unit)
        // Cannot assert intent without predicate
        testObserver.assertValueCount(8)
        testObserver.assertValueAt(7) { it is DataResult && it.data is CreateAssetTransferContract.ViewUpdate.StartReview }

        then(tokenRepositoryMock).should().loadToken(TEST_TOKEN_ADDRESS)
        then(tokenRepositoryMock).should(times(3)).loadPaymentToken(TEST_SAFE)
        then(tokenRepositoryMock).should().loadTokenBalances(TEST_SAFE, listOf(TEST_TOKEN))
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(relayRepositoryMock).should(times(3)).loadExecuteInformation(MockUtils.any(), MockUtils.any(), MockUtils.any(), MockUtils.any())
        then(relayRepositoryMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun processInputLoadTokenError() {
        given(tokenRepositoryMock.loadToken(MockUtils.any())).willReturn(Single.just(ERC20Token.ETHER_TOKEN))
        given(tokenRepositoryMock.loadTokenBalances(MockUtils.any(), MockUtils.any())).willReturn(Observable.error(TimeoutException()))
        val reviewEvents = TestObservableFactory<Unit>()
        val inputSubject = PublishSubject.create<CreateAssetTransferContract.Input>()
        val testObserver = TestObserver<Result<CreateAssetTransferContract.ViewUpdate>>()
        inputSubject
            .compose(viewModel.processInput(TEST_SAFE, TEST_ETHER_TOKEN, reviewEvents.get()))
            .subscribe(testObserver)

        testObserver.assertValuesOnly(
            DataResult(CreateAssetTransferContract.ViewUpdate.TokenInfo(ERC20TokenWithBalance(ERC20Token.ETHER_TOKEN, null))),
            DataResult(CreateAssetTransferContract.ViewUpdate.TokenInfo(ERC20TokenWithBalance(ERC20Token.ETHER_TOKEN, null)))
        )
        then(tokenRepositoryMock).should().loadToken(TEST_ETHER_TOKEN)
        then(tokenRepositoryMock).should().loadTokenBalances(TEST_SAFE, listOf(ERC20Token.ETHER_TOKEN))
        then(tokenRepositoryMock).shouldHaveNoMoreInteractions()
        then(relayRepositoryMock).shouldHaveZeroInteractions()
    }

    companion object {
        private val TEST_SAFE = "0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC".asEthereumAddress()!!
        private val TEST_ADDRESS = "0xc257274276a4e539741ca11b590b9447b26a8051".asEthereumAddress()!!
        private val TEST_ETHER_TOKEN = Solidity.Address(BigInteger.ZERO)
        private val TEST_ETH_AMOUNT = Wei.ether("23").value
        private val TEST_TOKEN_ADDRESS = "0xa7e15e2e76ab469f8681b576cff168f37aa246ec".asEthereumAddress()!!
        private val TEST_TOKEN = ERC20Token(TEST_TOKEN_ADDRESS, "Test Token", "TT", 10, "")
        private val TEST_GAS_TOKEN = ERC20Token("0xdeafbeeb".asEthereumAddress()!!, "Gas Token", "GT", 18)
        private val TEST_TOKEN_AMOUNT = BigInteger("230000000000")
        private const val TEST_TRANSACTION_HASH = "SomeHash"
        private val TEST_TRANSACTION =
            SafeTransaction(Transaction(Solidity.Address(BigInteger.ZERO), nonce = BigInteger.TEN), TransactionExecutionRepository.Operation.CALL)
        private val TEST_SIGNERS = listOf(BigInteger.valueOf(7), BigInteger.valueOf(13)).map { Solidity.Address(it) }
        private val TEST_OWNERS = TEST_SIGNERS + Solidity.Address(BigInteger.valueOf(5))
    }
}
