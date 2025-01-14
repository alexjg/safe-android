package pm.gnosis.heimdall.di.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pm.gnosis.heimdall.di.ViewModelFactory
import pm.gnosis.heimdall.di.ViewModelKey
import pm.gnosis.heimdall.ui.addressbook.AddressBookContract
import pm.gnosis.heimdall.ui.addressbook.AddressBookViewModel
import pm.gnosis.heimdall.ui.authenticator.ConnectAuthenticatorContract
import pm.gnosis.heimdall.ui.authenticator.ConnectAuthenticatorViewModel
import pm.gnosis.heimdall.ui.debugsettings.DebugSettingsContract
import pm.gnosis.heimdall.ui.debugsettings.DebugSettingsViewModel
import pm.gnosis.heimdall.ui.dialogs.ens.EnsInputContract
import pm.gnosis.heimdall.ui.dialogs.ens.EnsInputViewModel
import pm.gnosis.heimdall.ui.keycard.*
import pm.gnosis.heimdall.ui.messagesigning.ConfirmMessageContract
import pm.gnosis.heimdall.ui.messagesigning.ConfirmMessageViewModel
import pm.gnosis.heimdall.ui.onboarding.fingerprint.FingerprintSetupContract
import pm.gnosis.heimdall.ui.onboarding.fingerprint.FingerprintSetupViewModel
import pm.gnosis.heimdall.ui.onboarding.password.PasswordSetupContract
import pm.gnosis.heimdall.ui.onboarding.password.PasswordSetupViewModel
import pm.gnosis.heimdall.ui.recoveryphrase.SetupRecoveryPhraseContract
import pm.gnosis.heimdall.ui.recoveryphrase.SetupRecoveryPhraseViewModel
import pm.gnosis.heimdall.ui.safe.create.CreateSafeConfirmRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.create.CreateSafeConfirmRecoveryPhraseViewModel
import pm.gnosis.heimdall.ui.safe.create.CreateSafePaymentTokenContract
import pm.gnosis.heimdall.ui.safe.create.CreateSafePaymentTokenViewModel
import pm.gnosis.heimdall.ui.safe.details.SafeDetailsContract
import pm.gnosis.heimdall.ui.safe.details.SafeDetailsViewModel
import pm.gnosis.heimdall.ui.safe.details.transactions.SafeTransactionsContract
import pm.gnosis.heimdall.ui.safe.details.transactions.SafeTransactionsViewModel
import pm.gnosis.heimdall.ui.safe.main.SafeMainContract
import pm.gnosis.heimdall.ui.safe.main.SafeMainViewModel
import pm.gnosis.heimdall.ui.safe.pairing.PairingContract
import pm.gnosis.heimdall.ui.safe.pairing.PairingViewModel
import pm.gnosis.heimdall.ui.safe.pending.DeploySafeProgressContract
import pm.gnosis.heimdall.ui.safe.pending.DeploySafeProgressViewModel
import pm.gnosis.heimdall.ui.safe.pending.SafeCreationFundContract
import pm.gnosis.heimdall.ui.safe.pending.SafeCreationFundViewModel
import pm.gnosis.heimdall.ui.safe.recover.extension.ReplaceAuthenticatorRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.recover.extension.ReplaceAuthenticatorRecoveryPhraseViewModel
import pm.gnosis.heimdall.ui.safe.recover.extension.ReplaceAuthenticatorSubmitContract
import pm.gnosis.heimdall.ui.safe.recover.extension.ReplaceAuthenticatorSubmitViewModel
import pm.gnosis.heimdall.ui.safe.recover.recoveryphrase.*
import pm.gnosis.heimdall.ui.safe.recover.safe.CheckSafeContract
import pm.gnosis.heimdall.ui.safe.recover.safe.CheckSafeViewModel
import pm.gnosis.heimdall.ui.safe.recover.safe.RecoverSafeRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.recover.safe.RecoverSafeRecoveryPhraseViewModel
import pm.gnosis.heimdall.ui.safe.recover.safe.submit.RecoveringSafeContract
import pm.gnosis.heimdall.ui.safe.recover.safe.submit.RecoveringSafeViewModel
import pm.gnosis.heimdall.ui.safe.upgrade.UpgradeMasterCopyContract
import pm.gnosis.heimdall.ui.safe.upgrade.UpgradeMasterCopyViewModel
import pm.gnosis.heimdall.ui.security.unlock.UnlockContract
import pm.gnosis.heimdall.ui.security.unlock.UnlockViewModel
import pm.gnosis.heimdall.ui.settings.general.GeneralSettingsContract
import pm.gnosis.heimdall.ui.settings.general.GeneralSettingsViewModel
import pm.gnosis.heimdall.ui.settings.general.changepassword.ChangePasswordContract
import pm.gnosis.heimdall.ui.settings.general.changepassword.ChangePasswordViewModel
import pm.gnosis.heimdall.ui.splash.SplashContract
import pm.gnosis.heimdall.ui.splash.SplashViewModel
import pm.gnosis.heimdall.ui.tokens.balances.TokenBalancesContract
import pm.gnosis.heimdall.ui.tokens.balances.TokenBalancesViewModel
import pm.gnosis.heimdall.ui.tokens.manage.ManageTokensContract
import pm.gnosis.heimdall.ui.tokens.manage.ManageTokensViewModel
import pm.gnosis.heimdall.ui.tokens.payment.PaymentTokensContract
import pm.gnosis.heimdall.ui.tokens.payment.PaymentTokensViewModel
import pm.gnosis.heimdall.ui.tokens.receive.ReceiveTokenContract
import pm.gnosis.heimdall.ui.tokens.receive.ReceiveTokenViewModel
import pm.gnosis.heimdall.ui.transactions.create.CreateAssetTransferContract
import pm.gnosis.heimdall.ui.transactions.create.CreateAssetTransferViewModel
import pm.gnosis.heimdall.ui.transactions.view.confirm.ConfirmTransactionContract
import pm.gnosis.heimdall.ui.transactions.view.confirm.ConfirmTransactionViewModel
import pm.gnosis.heimdall.ui.transactions.view.review.ReviewTransactionContract
import pm.gnosis.heimdall.ui.transactions.view.review.ReviewTransactionViewModel
import pm.gnosis.heimdall.ui.transactions.view.status.TransactionStatusContract
import pm.gnosis.heimdall.ui.transactions.view.status.TransactionStatusViewModel
import pm.gnosis.heimdall.ui.walletconnect.intro.WalletConnectIntroContract
import pm.gnosis.heimdall.ui.walletconnect.intro.WalletConnectIntroViewModel
import pm.gnosis.heimdall.ui.walletconnect.link.WalletConnectLinkContract
import pm.gnosis.heimdall.ui.walletconnect.link.WalletConnectLinkViewModel
import pm.gnosis.heimdall.ui.walletconnect.sessions.WalletConnectSessionsContract
import pm.gnosis.heimdall.ui.walletconnect.sessions.WalletConnectSessionsViewModel
import javax.inject.Singleton

@Module
abstract class ViewModelFactoryModule {

    @Binds
    @IntoMap
    @ViewModelKey(AddressBookContract::class)
    abstract fun bindsAddressBookContract(viewModel: AddressBookViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ChangePasswordContract::class)
    abstract fun bindsChangePasswordContract(viewModel: ChangePasswordViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CheckSafeContract::class)
    abstract fun bindsCheckSafeContract(viewModel: CheckSafeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConfirmMessageContract::class)
    abstract fun bindsConfirmMessageContract(viewModel: ConfirmMessageViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConfirmNewRecoveryPhraseContract::class)
    abstract fun bindsConfirmNewRecoveryPhraseContract(viewModel: ConfirmNewRecoveryPhraseViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ConfirmTransactionContract::class)
    abstract fun bindsConfirmTransactionContract(viewModel: ConfirmTransactionViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(ConnectAuthenticatorContract::class)
    abstract fun bindsConnectAuthenticatorContract(viewModel: ConnectAuthenticatorViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CreateAssetTransferContract::class)
    abstract fun bindsCreateAssetTransferContract(viewModel: CreateAssetTransferViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(CreateSafeConfirmRecoveryPhraseContract::class)
    abstract fun bindsCreateSafeConfirmRecoveryPhraseContract(viewModel: CreateSafeConfirmRecoveryPhraseViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(CreateSafePaymentTokenContract::class)
    abstract fun bindsCreateSafePaymentTokenContract(viewModel: CreateSafePaymentTokenViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(DebugSettingsContract::class)
    abstract fun bindsDebugSettingsContract(viewModel: DebugSettingsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(DeploySafeProgressContract::class)
    abstract fun bindsDeploySafeProgressContract(viewModel: DeploySafeProgressViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(EnsInputContract::class)
    abstract fun bindsEnsInputContract(viewModel: EnsInputViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(FingerprintSetupContract::class)
    abstract fun bindsFingerprintSetupContract(viewModel: FingerprintSetupViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(GeneralSettingsContract::class)
    abstract fun bindsGeneralSettingsContract(viewModel: GeneralSettingsViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(KeycardCredentialsContract::class)
    abstract fun bindsKeycardCredentialsContract(viewModel: KeycardCredentialsViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(KeycardInitializeContract::class)
    abstract fun bindsKeycardInitializeContract(viewModel: KeycardInitializeViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(KeycardPairingContract::class)
    abstract fun bindsKeycardPairingContract(viewModel: KeycardPairingViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(KeycardSigningContract::class)
    abstract fun bindsKeycardSigningContract(viewModel: KeycardSigningViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ManageTokensContract::class)
    abstract fun bindsManageTokensContract(viewModel: ManageTokensViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PairingContract::class)
    abstract fun bindsPairingContract(viewModel: PairingViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PasswordSetupContract::class)
    abstract fun bindsPasswordSetupContract(viewModel: PasswordSetupViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(PaymentTokensContract::class)
    abstract fun bindsPaymentTokensContract(viewModel: PaymentTokensViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ReceiveTokenContract::class)
    abstract fun bindsReceiveTokenContract(viewModel: ReceiveTokenViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(RecoveringSafeContract::class)
    abstract fun bindsRecoveringSafeContract(viewModel: RecoveringSafeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(RecoverSafeRecoveryPhraseContract::class)
    abstract fun bindsRecoverInputRecoveryPhraseContract(viewModel: RecoverSafeRecoveryPhraseViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ReplaceAuthenticatorSubmitContract::class)
    abstract fun bindsReplaceBrowserExtensionContract(viewModel: ReplaceAuthenticatorSubmitViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ReplaceAuthenticatorRecoveryPhraseContract::class)
    abstract fun bindsReplaceBrowserExtensionRecoveryPhraseContract(viewModel: ReplaceAuthenticatorRecoveryPhraseViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ReviewTransactionContract::class)
    abstract fun bindsReviewTransactionContract(viewModel: ReviewTransactionViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SafeCreationFundContract::class)
    abstract fun bindsSafeCreationFundContract(viewModel: SafeCreationFundViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SafeDetailsContract::class)
    abstract fun bindsSafeDetailsContract(viewModel: SafeDetailsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SafeMainContract::class)
    abstract fun bindsSafeMainContract(viewModel: SafeMainViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SafeTransactionsContract::class)
    abstract fun bindsSafeTransactionsContract(viewModel: SafeTransactionsViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ScanExtensionAddressContract::class)
    abstract fun bindsScanExtensionAddressContract(viewModel: ScanExtensionAddressViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SetupRecoveryPhraseContract::class)
    abstract fun bindsSetupRecoveryPhraseContract(viewModel: SetupRecoveryPhraseViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(SetupNewRecoveryPhraseIntroContract::class)
    abstract fun bindsSetupNewRecoveryPhraseIntroContract(viewModel: SetupNewRecoveryPhraseIntroViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(SplashContract::class)
    abstract fun bindsSplashContract(viewModel: SplashViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TokenBalancesContract::class)
    abstract fun bindsTokensContract(viewModel: TokenBalancesViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TransactionStatusContract::class)
    abstract fun bindsTransactionStatusContract(viewModel: TransactionStatusViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(UnlockContract::class)
    abstract fun bindsUnlockContract(viewModel: UnlockViewModel): ViewModel

    @ExperimentalCoroutinesApi
    @Binds
    @IntoMap
    @ViewModelKey(UpgradeMasterCopyContract::class)
    abstract fun bindsUpgradeMasterCopyContract(viewModel: UpgradeMasterCopyViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(WalletConnectIntroContract::class)
    abstract fun bindsWalletConnectIntroContract(viewModel: WalletConnectIntroViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(WalletConnectLinkContract::class)
    abstract fun bindsWalletConnectLinkContract(viewModel: WalletConnectLinkViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(WalletConnectSessionsContract::class)
    abstract fun bindsWalletConnectSessionsContract(viewModel: WalletConnectSessionsViewModel): ViewModel

    @Binds
    @Singleton
    abstract fun bindsViewModelFactory(viewModel: ViewModelFactory): ViewModelProvider.Factory
}
