package com.nekosu.di

import coil.ImageLoader
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import com.nekosu.BuildConfig
import com.nekosu.data.AppSettingsRepository
import com.nekosu.data.application.ApplicationControlRepository
import com.nekosu.data.application.DynamicManagerRepository
import com.nekosu.data.count.CountRepository
import com.nekosu.data.download.DownloadRepository
import com.nekosu.data.file.ModuleFileRepository
import com.nekosu.data.flash.FlashRepository
import com.nekosu.data.kernel.KernelRepository
import com.nekosu.data.kernel.UmountRepository
import com.nekosu.data.logging.BugreportRepository
import com.nekosu.data.logging.SulogRepository
import com.nekosu.data.module.ModuleActionRepository
import com.nekosu.data.module.ModuleCatalogRepository
import com.nekosu.data.module.ModuleIconRepository
import com.nekosu.data.module.ModulePreferencesRepository
import com.nekosu.data.module.ModuleReleaseRepository
import com.nekosu.data.module.ModuleRepository
import com.nekosu.data.module.RepositorySourceStore
import com.nekosu.data.module.registry.KernelSuRegistryRepository
import com.nekosu.data.network.NetworkRequestRepository
import com.nekosu.data.network.NetworkStatusRepository
import com.nekosu.data.network.WebResourceRepository
import com.nekosu.data.packageinfo.AppIconDataSource
import com.nekosu.data.packageinfo.InstalledPackageCache
import com.nekosu.data.packageinfo.InstalledPackageRepository
import com.nekosu.data.packageinfo.RootServiceRepository
import com.nekosu.data.packageinfo.SuperUserRepository
import com.nekosu.data.profile.ProfileRepository
import com.nekosu.data.profile.ProfileTemplateRepository
import com.nekosu.data.script.ScriptRepository
import com.nekosu.data.settings.LocaleHelper
import com.nekosu.data.settings.LocaleRepository
import com.nekosu.data.settings.SettingsPlatformRepository
import com.nekosu.data.shell.KsuCliRepository
import com.nekosu.data.shell.ShortcutRepository
import com.nekosu.data.startup.ApplicationInitializationRepository
import com.nekosu.data.startup.StartupRepository
import com.nekosu.data.susfs.SuSFSConfigHelper
import com.nekosu.data.susfs.SuSFSRepository
import com.nekosu.data.system.HomeRuntimeRepository
import com.nekosu.data.system.HomeStateRepository
import com.nekosu.data.text.HanziToPinyin
import com.nekosu.data.theme.MonetCompatColorSource
import com.nekosu.data.theme.ThemeRepository
import com.nekosu.data.update.ManagerUpdateRepository
import com.nekosu.data.webui.WebUiRepository
import com.nekosu.domain.text.TextTransliterator
import com.nekosu.domain.usecase.AddModuleRepositoryUseCase
import com.nekosu.domain.usecase.AddUmountPathUseCase
import com.nekosu.domain.usecase.ApplyLanguageUseCase
import com.nekosu.domain.usecase.BackupAllowlistUseCase
import com.nekosu.domain.usecase.CalculateInstalledModuleSizeUseCase
import com.nekosu.domain.usecase.CheckFlashModuleMountUseCase
import com.nekosu.domain.usecase.CheckManagerUpdateUseCase
import com.nekosu.domain.usecase.CleanSulogUseCase
import com.nekosu.domain.usecase.ClearDynamicManagerUseCase
import com.nekosu.domain.usecase.ConfigureSuLogUseCase
import com.nekosu.domain.usecase.ControlAppUseCase
import com.nekosu.domain.usecase.DeleteProfileTemplateUseCase
import com.nekosu.domain.usecase.EnableSulogUseCase
import com.nekosu.domain.usecase.EnqueueDownloadUseCase
import com.nekosu.domain.usecase.EnqueueManagerUpdateUseCase
import com.nekosu.domain.usecase.EnsureManagerInstalledUseCase
import com.nekosu.domain.usecase.ExecuteFlashOperationUseCase
import com.nekosu.domain.usecase.ExecuteModuleActionUseCase
import com.nekosu.domain.usecase.ExportProfileTemplatesUseCase
import com.nekosu.domain.usecase.ExtractModuleIdUseCase
import com.nekosu.domain.usecase.ExtractModuleNameUseCase
import com.nekosu.domain.usecase.FetchRemoteTextUseCase
import com.nekosu.domain.usecase.GenerateBugreportUseCase
import com.nekosu.domain.usecase.GetAppProfileUseCase
import com.nekosu.domain.usecase.GetAppSepolicyUseCase
import com.nekosu.domain.usecase.GetBooleanPreferenceUseCase
import com.nekosu.domain.usecase.GetCatalogModuleUseCase
import com.nekosu.domain.usecase.GetDefaultUmountModulesUseCase
import com.nekosu.domain.usecase.GetHomeBasicInfoUseCase
import com.nekosu.domain.usecase.GetKernelFeatureSettingsUseCase
import com.nekosu.domain.usecase.GetKernelStatusUseCase
import com.nekosu.domain.usecase.GetLastScriptRunUseCase
import com.nekosu.domain.usecase.GetManagerRuntimeInfoUseCase
import com.nekosu.domain.usecase.GetModuleReadmeUseCase
import com.nekosu.domain.usecase.GetModuleReleaseDetailsUseCase
import com.nekosu.domain.usecase.GetModuleUpdateChangelogUseCase
import com.nekosu.domain.usecase.GetPlatformFeatureStatusUseCase
import com.nekosu.domain.usecase.GetProfileTemplateUseCase
import com.nekosu.domain.usecase.GetStringPreferenceUseCase
import com.nekosu.domain.usecase.GetStringSetPreferenceUseCase
import com.nekosu.domain.usecase.GetSuSFSStatusUseCase
import com.nekosu.domain.usecase.GetSuperUserAppGroupUseCase
import com.nekosu.domain.usecase.ImportAllowlistUseCase
import com.nekosu.domain.usecase.ImportProfileTemplatesUseCase
import com.nekosu.domain.usecase.InitializeApplicationUseCase
import com.nekosu.domain.usecase.IsCatalogModuleCurrentUseCase
import com.nekosu.domain.usecase.IsLateLoadModeUseCase
import com.nekosu.domain.usecase.IsModuleUriAccessibleUseCase
import com.nekosu.domain.usecase.IsNetworkAvailableUseCase
import com.nekosu.domain.usecase.IsSoftRebootPreferredUseCase
import com.nekosu.domain.usecase.IsSystemLanguageSettingsUseCase
import com.nekosu.domain.usecase.LaunchSystemLanguageSettingsUseCase
import com.nekosu.domain.usecase.LoadModuleIconUseCase
import com.nekosu.domain.usecase.LoadSettingsPlatformUseCase
import com.nekosu.domain.usecase.ObserveAssetDownloadUseCase
import com.nekosu.domain.usecase.ObserveCatalogModulesUseCase
import com.nekosu.domain.usecase.ObserveDownloadUseCase
import com.nekosu.domain.usecase.ObserveDynamicManagerStateUseCase
import com.nekosu.domain.usecase.ObserveInstalledModulesUseCase
import com.nekosu.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import com.nekosu.domain.usecase.ObserveProfileTemplateOfflineUseCase
import com.nekosu.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import com.nekosu.domain.usecase.ObserveProfileTemplatesUseCase
import com.nekosu.domain.usecase.ObserveRepositorySourcesUseCase
import com.nekosu.domain.usecase.ObserveStartupStateUseCase
import com.nekosu.domain.usecase.ObserveSulogStateUseCase
import com.nekosu.domain.usecase.ObserveSuperUserStateUseCase
import com.nekosu.domain.usecase.ObserveUmountStateUseCase
import com.nekosu.domain.usecase.PeekOneshotScriptUseCase
import com.nekosu.domain.usecase.PrepareScriptUseCase
import com.nekosu.domain.usecase.RebootUseCase
import com.nekosu.domain.usecase.RefreshDynamicManagerUseCase
import com.nekosu.domain.usecase.RefreshInstalledModulesUseCase
import com.nekosu.domain.usecase.RefreshModuleCatalogUseCase
import com.nekosu.domain.usecase.RefreshProfileTemplatesUseCase
import com.nekosu.domain.usecase.RefreshSulogUseCase
import com.nekosu.domain.usecase.RefreshSuperUsersUseCase
import com.nekosu.domain.usecase.RefreshUmountPathsUseCase
import com.nekosu.domain.usecase.ReleaseScriptUseCase
import com.nekosu.domain.usecase.RemoveModuleRepositoryUseCase
import com.nekosu.domain.usecase.RemovePreferenceUseCase
import com.nekosu.domain.usecase.RemoveUmountPathUseCase
import com.nekosu.domain.usecase.RenameModuleRepositoryUseCase
import com.nekosu.domain.usecase.ResolveModuleUpdateUseCase
import com.nekosu.domain.usecase.RunScriptUseCase
import com.nekosu.domain.usecase.SaveModuleActionLogUseCase
import com.nekosu.domain.usecase.SaveProfileTemplateUseCase
import com.nekosu.domain.usecase.SelectDynamicManagerUseCase
import com.nekosu.domain.usecase.SetAppProfileUseCase
import com.nekosu.domain.usecase.SetAppSepolicyUseCase
import com.nekosu.domain.usecase.SetBooleanPreferenceUseCase
import com.nekosu.domain.usecase.SetDefaultUmountModulesUseCase
import com.nekosu.domain.usecase.SetKernelUmountEnabledUseCase
import com.nekosu.domain.usecase.SetManualDynamicManagerUseCase
import com.nekosu.domain.usecase.SetModuleEnabledUseCase
import com.nekosu.domain.usecase.SetModuleRemovedUseCase
import com.nekosu.domain.usecase.SetSelinuxHideEnabledUseCase
import com.nekosu.domain.usecase.SetShellSuAdbOnlyEnabledUseCase
import com.nekosu.domain.usecase.SetStringPreferenceUseCase
import com.nekosu.domain.usecase.SetStringSetPreferenceUseCase
import com.nekosu.domain.usecase.SetSuEnabledUseCase
import com.nekosu.domain.usecase.SuSFSConfigUseCase
import com.nekosu.domain.usecase.TakeModuleUriPermissionUseCase
import com.nekosu.domain.usecase.TransliterateTextUseCase
import com.nekosu.domain.usecase.UpdateAppearanceUseCase
import com.nekosu.domain.usecase.UpdateCachedModuleEnabledUseCase
import com.nekosu.domain.usecase.UpdatePlatformSettingUseCase
import com.nekosu.domain.usecase.ValidateSepolicyUseCase
import com.nekosu.ui.activity.util.ThemeUtils
import com.nekosu.ui.component.ZipFileDetector
import com.nekosu.ui.theme.BackgroundManager
import com.nekosu.ui.theme.CardConfig
import com.nekosu.ui.theme.ThemeConfig
import com.nekosu.ui.util.Shortcut
import com.nekosu.ui.viewmodel.AppProfileViewModel
import com.nekosu.ui.viewmodel.DynamicManagerViewModel
import com.nekosu.ui.viewmodel.ExecuteModuleActionViewModel
import com.nekosu.ui.viewmodel.FlashViewModel
import com.nekosu.ui.viewmodel.HomeViewModel
import com.nekosu.ui.viewmodel.MainIntentViewModel
import com.nekosu.ui.viewmodel.ModuleDetailViewModel
import com.nekosu.ui.viewmodel.ModuleRepoViewModel
import com.nekosu.ui.viewmodel.ModuleViewModel
import com.nekosu.ui.viewmodel.RepositorySourcesViewModel
import com.nekosu.ui.viewmodel.ScriptRunViewModel
import com.nekosu.ui.viewmodel.SettingsViewModel
import com.nekosu.ui.viewmodel.SuSFSViewModel
import com.nekosu.ui.viewmodel.SulogViewModel
import com.nekosu.ui.viewmodel.SuperUserViewModel
import com.nekosu.ui.viewmodel.TemplateEditorViewModel
import com.nekosu.ui.viewmodel.TemplateViewModel
import com.nekosu.ui.viewmodel.UmountManagerScreenViewModel
import com.nekosu.ui.webui.MonetColorsProvider
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "NekoSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build(),
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    singleOf(::CountRepository)
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            ksuCliRepository = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(), get()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::ModuleCatalogRepository)
    single { ModuleReleaseRepository(get(), get(applicationScopeQualifier)) }
    singleOf(::ModuleIconRepository)
    single { KernelSuRegistryRepository(get(), get(), get(), get()) }
    singleOf(::RepositorySourceStore)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    single { ScriptRepository(androidApplication(), get(), get(applicationScopeQualifier)) }
    singleOf(::WebResourceRepository)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::IsSoftRebootPreferredUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::ObserveAssetDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::SetShellSuAdbOnlyEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::IsCatalogModuleCurrentUseCase)
    factoryOf(::GetModuleReadmeUseCase)
    factoryOf(::GetModuleReleaseDetailsUseCase)
    factoryOf(::LoadModuleIconUseCase)
    factoryOf(::ResolveModuleUpdateUseCase)
    factoryOf(::GetModuleUpdateChangelogUseCase)
    factoryOf(::ObserveRepositorySourcesUseCase)
    factoryOf(::AddModuleRepositoryUseCase)
    factoryOf(::RenameModuleRepositoryUseCase)
    factoryOf(::RemoveModuleRepositoryUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::PeekOneshotScriptUseCase)
    factoryOf(::PrepareScriptUseCase)
    factoryOf(::ReleaseScriptUseCase)
    factoryOf(::RunScriptUseCase)
    factoryOf(::GetLastScriptRunUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters -> ScriptRunViewModel(parameters[0], get(), get(), get(), get(), get()) }
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModel { parameters -> ModuleRepoViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), parameters.getOrNull<String>()) }
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], parameters[1], get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::RepositorySourcesViewModel)
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)
