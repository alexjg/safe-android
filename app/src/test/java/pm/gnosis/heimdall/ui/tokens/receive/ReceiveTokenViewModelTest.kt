package pm.gnosis.heimdall.ui.tokens.receive

import android.content.Context
import android.graphics.Bitmap
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.*
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.AddressBookRepository
import pm.gnosis.models.AddressBookEntry
import pm.gnosis.svalinn.common.utils.QrCodeGenerator
import pm.gnosis.tests.utils.ImmediateSchedulersRule
import pm.gnosis.tests.utils.MockUtils
import pm.gnosis.tests.utils.TestSingleFactory
import pm.gnosis.tests.utils.mockGetString
import pm.gnosis.utils.asEthereumAddress

@RunWith(MockitoJUnitRunner::class)
class ReceiveTokenViewModelTest {
    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    private lateinit var contextMock: Context

    @Mock
    private lateinit var addressBookRepository: AddressBookRepository

    @Mock
    private lateinit var qrCodeGeneratorMock: QrCodeGenerator

    private lateinit var viewModel: ReceiveTokenViewModel

    @Before
    fun setUp() {
        viewModel = ReceiveTokenViewModel(contextMock, addressBookRepository, qrCodeGeneratorMock)
    }

    @Test
    fun observeSafeInfo() {
        val safeSingleFactory = TestSingleFactory<AddressBookEntry>()
        given(addressBookRepository.loadAddressBookEntry(MockUtils.any())).willReturn(safeSingleFactory.get())

        val qrSingleFactory = TestSingleFactory<Bitmap>()
        given(qrCodeGeneratorMock.generateQrCode(MockUtils.any(), anyInt(), anyInt(), anyInt())).willReturn(qrSingleFactory.get())

        val testObserver = TestObserver<ReceiveTokenContract.ViewUpdate>()
        viewModel.observeSafeInfo(TEST_SAFE).subscribe(testObserver)

        then(addressBookRepository).should().loadAddressBookEntry(TEST_SAFE)
        then(qrCodeGeneratorMock).should().generateQrCode("0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC")

        val updates = mutableListOf<ReceiveTokenContract.ViewUpdate>(
            ReceiveTokenContract.ViewUpdate.Address("0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC")
        )
        testObserver.assertValues(*updates.toTypedArray())

        val bitmapMock = mock(Bitmap::class.java)
        qrSingleFactory.success(bitmapMock)
        updates += ReceiveTokenContract.ViewUpdate.QrCode(bitmapMock)
        testObserver.assertValues(*updates.toTypedArray())

        safeSingleFactory.success(AddressBookEntry(TEST_SAFE, "Some Name", ""))
        updates += ReceiveTokenContract.ViewUpdate.Info("Some Name")
        testObserver.assertValues(*updates.toTypedArray())

        then(addressBookRepository).shouldHaveNoMoreInteractions()
        then(qrCodeGeneratorMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun observeSafeInfoErrors() {
        contextMock.mockGetString()
        val safeSingleFactory = TestSingleFactory<AddressBookEntry>()
        given(addressBookRepository.loadAddressBookEntry(MockUtils.any())).willReturn(safeSingleFactory.get())

        val qrSingleFactory = TestSingleFactory<Bitmap>()
        given(qrCodeGeneratorMock.generateQrCode(MockUtils.any(), anyInt(), anyInt(), anyInt())).willReturn(qrSingleFactory.get())

        val testObserver = TestObserver<ReceiveTokenContract.ViewUpdate>()
        viewModel.observeSafeInfo(TEST_SAFE).subscribe(testObserver)

        then(addressBookRepository).should().loadAddressBookEntry(TEST_SAFE)
        then(qrCodeGeneratorMock).should().generateQrCode("0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC")

        val updates = mutableListOf<ReceiveTokenContract.ViewUpdate>(
            ReceiveTokenContract.ViewUpdate.Address("0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC")
        )
        testObserver.assertValues(*updates.toTypedArray())

        qrSingleFactory.error(OutOfMemoryError())
        testObserver.assertValues(*updates.toTypedArray())

        safeSingleFactory.error(NoSuchElementException())
        updates += ReceiveTokenContract.ViewUpdate.Info(R.string.default_safe_name.toString())
        testObserver.assertValues(*updates.toTypedArray())

        then(addressBookRepository).shouldHaveNoMoreInteractions()
        then(qrCodeGeneratorMock).shouldHaveNoMoreInteractions()
    }

    @Test
    fun observeSafeInfoEmptyName() {
        contextMock.mockGetString()
        val safeSingleFactory = TestSingleFactory<AddressBookEntry>()
        given(addressBookRepository.loadAddressBookEntry(MockUtils.any())).willReturn(safeSingleFactory.get())

        given(qrCodeGeneratorMock.generateQrCode(MockUtils.any(), anyInt(), anyInt(), anyInt())).willReturn(Single.error(OutOfMemoryError()))

        val testObserver = TestObserver<ReceiveTokenContract.ViewUpdate>()
        viewModel.observeSafeInfo(TEST_SAFE).subscribe(testObserver)

        then(addressBookRepository).should().loadAddressBookEntry(TEST_SAFE)
        then(qrCodeGeneratorMock).should().generateQrCode("0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC")

        val updates = mutableListOf<ReceiveTokenContract.ViewUpdate>(
            ReceiveTokenContract.ViewUpdate.Address("0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC")
        )
        testObserver.assertValues(*updates.toTypedArray())

        safeSingleFactory.success(AddressBookEntry(TEST_SAFE, "      ", ""))
        updates += ReceiveTokenContract.ViewUpdate.Info("      ")
        testObserver.assertValues(*updates.toTypedArray())

        then(addressBookRepository).shouldHaveNoMoreInteractions()
        then(qrCodeGeneratorMock).shouldHaveNoMoreInteractions()
    }

    companion object {
        private val TEST_SAFE = "0xA7e15e2e76Ab469F8681b576cFF168F37Aa246EC".asEthereumAddress()!!
    }
}
