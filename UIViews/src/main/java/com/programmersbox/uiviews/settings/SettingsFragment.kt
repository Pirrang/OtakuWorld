package com.programmersbox.uiviews.settings

import android.Manifest
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.programmersbox.favoritesdatabase.HistoryDatabase
import com.programmersbox.favoritesdatabase.ItemDao
import com.programmersbox.helpfulutils.notificationManager
import com.programmersbox.helpfulutils.requestPermissions
import com.programmersbox.models.sourceFlow
import com.programmersbox.sharedutils.AppUpdate
import com.programmersbox.sharedutils.MainLogo
import com.programmersbox.sharedutils.updateAppCheck
import com.programmersbox.uiviews.*
import com.programmersbox.uiviews.R
import com.programmersbox.uiviews.utils.*
import com.programmersbox.uiviews.utils.components.ListBottomScreen
import com.programmersbox.uiviews.utils.components.ListBottomSheetItemModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*

class ComposeSettingsDsl {
    internal var generalSettings: (@Composable () -> Unit)? = null
    internal var viewSettings: (@Composable () -> Unit)? = null
    internal var playerSettings: (@Composable () -> Unit)? = null

    fun generalSettings(block: @Composable () -> Unit) {
        generalSettings = block
    }

    fun viewSettings(block: @Composable () -> Unit) {
        viewSettings = block
    }

    fun playerSettings(block: @Composable () -> Unit) {
        playerSettings = block
    }
}

@ExperimentalComposeUiApi
@ExperimentalMaterial3Api
@Composable
fun SettingScreen(
    navController: NavController,
    logo: MainLogo,
    genericInfo: GenericInfo,
    activity: ComponentActivity,
    usedLibraryClick: () -> Unit = {},
    debugMenuClick: () -> Unit = {},
    notificationClick: () -> Unit = {},
    favoritesClick: () -> Unit = {},
    historyClick: () -> Unit = {},
    globalSearchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val customPreferences = remember { ComposeSettingsDsl().apply(genericInfo.composeCustomPreferences(navController)) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    OtakuScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            InsetSmallTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
    ) { p ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(p)
        ) {

            if (BuildConfig.FLAVOR != "noFirebase") {
                /*Account*/
                AccountSettings(
                    context = context,
                    activity = activity,
                    logo = logo
                )

                Divider()
            }

            /*About*/
            AboutSettings(
                context = context,
                scope = scope,
                logo = logo
            )

            Divider(modifier = Modifier.padding(top = 4.dp))

            /*Notifications*/
            NotificationSettings(
                context = context,
                scope = scope,
                notificationClick = notificationClick
            )

            /*View*/
            ViewSettings(
                customSettings = customPreferences.viewSettings,
                debugMenuClick = debugMenuClick,
                favoritesClick = favoritesClick,
                historyClick = historyClick,
                globalSearchClick = globalSearchClick
            )

            Divider()

            /*General*/
            GeneralSettings(
                context = context,
                scope = scope,
                customSettings = customPreferences.generalSettings,
            )

            Divider()

            /*Player*/
            PlaySettings(
                scope = scope,
                customSettings = customPreferences.playerSettings
            )

            Divider(modifier = Modifier.padding(top = 4.dp))

            /*More Info*/
            InfoSettings(
                logo = logo,
                scope = scope,
                activity = activity,
                genericInfo = genericInfo,
                usedLibraryClick = usedLibraryClick
            )
        }
    }

}

@Composable
private fun AccountSettings(
    context: Context,
    activity: ComponentActivity,
    logo: MainLogo,
    viewModel: AccountViewModel = viewModel()
) {
    CategorySetting { Text(stringResource(R.string.account_category_title)) }

    val accountInfo = viewModel.accountInfo

    PreferenceSetting(
        settingTitle = { Text(accountInfo?.displayName ?: "User") },
        settingIcon = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(accountInfo?.photoUrl)
                    .error(logo.logoId)
                    .placeholder(logo.logoId)
                    .crossfade(true)
                    .lifecycle(LocalLifecycleOwner.current)
                    .transformations(CircleCropTransformation())
                    .build(),
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() }
        ) { viewModel.signInOrOut(context, activity) }
    )
}

@Composable
private fun AboutSettings(
    context: Context,
    scope: CoroutineScope,
    logo: MainLogo,
    aboutViewModel: AboutViewModel = viewModel { AboutViewModel(context) }
) {
    val navController = LocalNavController.current

    CategorySetting(
        settingTitle = { Text(stringResource(R.string.about)) },
        settingIcon = {
            Image(
                bitmap = AppCompatResources.getDrawable(context, logo.logoId)!!.toBitmap().asImageBitmap(),
                null,
                modifier = Modifier.fillMaxSize()
            )
        }
    )

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.last_update_check_time)) },
        summaryValue = { Text(aboutViewModel.time) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() }
        ) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "oneTimeUpdate",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<UpdateFlowWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .setRequiresBatteryNotLow(false)
                                .setRequiresCharging(false)
                                .setRequiresDeviceIdle(false)
                                .setRequiresStorageNotLow(false)
                                .build()
                        )
                        .build()
                )
        }
    )

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.are_you_sure_stop_checking)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            context.updatePref(SHOULD_CHECK, false)
                            OtakuApp.updateSetupNow(context, false)
                        }
                        showDialog = false
                    }
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.no)) } }
        )
    }

    SwitchSetting(
        settingTitle = { Text(stringResource(R.string.check_for_periodic_updates)) },
        value = aboutViewModel.canCheck,
        updateValue = {
            if (!it) {
                showDialog = true
            } else {
                scope.launch {
                    context.updatePref(SHOULD_CHECK, it)
                    OtakuApp.updateSetupNow(context, it)
                }
            }
        }
    )

    ShowWhen(aboutViewModel.canCheck) {
        PreferenceSetting(
            settingTitle = { Text(stringResource(R.string.clear_update_queue)) },
            summaryValue = { Text(stringResource(R.string.clear_update_queue_summary)) },
            modifier = Modifier
                .alpha(if (aboutViewModel.canCheck) 1f else .38f)
                .clickable(
                    enabled = aboutViewModel.canCheck,
                    indication = rememberRipple(),
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    val work = WorkManager.getInstance(context)
                    work.cancelUniqueWork("updateFlowChecks")
                    work.pruneWork()
                    OtakuApp.updateSetup(context)
                    Toast
                        .makeText(context, R.string.cleared, Toast.LENGTH_SHORT)
                        .show()
                }
        )
    }

    val source by sourceFlow.collectAsState(initial = null)

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.currentSource, source?.serviceName.orEmpty())) },
        settingIcon = { Icon(Icons.Default.Source, null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() }
        ) { navController.navigate(Screen.SourceChooserScreen.route) }
    )
}

@Composable
fun SourceChooserScreen() {
    val source by sourceFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val genericInfo = LocalGenericInfo.current

    ListBottomScreen(
        includeInsetPadding = true,
        title = stringResource(R.string.chooseASource),
        list = genericInfo.sourceList(),
        onClick = { service ->
            navController.popBackStack()
            scope.launch {
                service.let {
                    sourceFlow.emit(it)
                    context.currentService = it.serviceName
                }
            }
        }
    ) {
        ListBottomSheetItemModel(
            primaryText = it.serviceName,
            icon = if (it == source) Icons.Default.Check else null
        )
    }
}

@Composable
private fun NotificationSettings(
    context: Context,
    scope: CoroutineScope,
    notificationClick: () -> Unit,
    dao: ItemDao = LocalItemDao.current,
    notiViewModel: NotificationViewModel = viewModel { NotificationViewModel(dao = dao) }
) {
    ShowWhen(notiViewModel.savedNotifications > 0) {
        CategorySetting { Text(stringResource(R.string.notifications_category_title)) }

        PreferenceSetting(
            settingTitle = { Text(stringResource(R.string.view_notifications_title)) },
            settingIcon = { Icon(Icons.Default.Notifications, null, modifier = Modifier.fillMaxSize()) },
            summaryValue = { Text(stringResource(R.string.pending_saved_notifications, notiViewModel.savedNotifications)) },
            modifier = Modifier.clickable(
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() },
                onClick = notificationClick
            )
        )

        var showDialog by remember { mutableStateOf(false) }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.are_you_sure_delete_notifications)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val number = dao.deleteAllNotifications()
                                launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.deleted_notifications, number),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    context.notificationManager.cancel(42)
                                }
                            }
                            showDialog = false
                        }
                    ) { Text(stringResource(R.string.yes)) }
                },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.no)) } }
            )
        }

        PreferenceSetting(
            settingTitle = { Text(stringResource(R.string.delete_saved_notifications_title)) },
            summaryValue = { Text(stringResource(R.string.delete_notifications_summary)) },
            modifier = Modifier
                .clickable(
                    indication = rememberRipple(),
                    interactionSource = remember { MutableInteractionSource() }
                ) { showDialog = true }
                .padding(bottom = 16.dp, top = 8.dp)
        )

        Divider()
    }
}

@Composable
private fun ViewSettings(
    customSettings: (@Composable () -> Unit)?,
    debugMenuClick: () -> Unit,
    favoritesClick: () -> Unit,
    historyClick: () -> Unit,
    globalSearchClick: () -> Unit
) {
    CategorySetting { Text(stringResource(R.string.view_menu_category_title)) }

    if (BuildConfig.DEBUG) {
        PreferenceSetting(
            settingTitle = { Text("Debug Menu") },
            settingIcon = { Icon(Icons.Default.Android, null, modifier = Modifier.fillMaxSize()) },
            modifier = Modifier.clickable(
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() },
                onClick = debugMenuClick
            )
        )
    }

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.viewFavoritesMenu)) },
        settingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() },
            onClick = favoritesClick
        )
    )

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.global_search)) },
        settingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() },
            onClick = globalSearchClick
        )
    )

    val context = LocalContext.current
    val dao = remember { HistoryDatabase.getInstance(context).historyDao() }
    val historyCount by dao.getAllRecentHistoryCount().collectAsState(initial = 0)

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.history)) },
        summaryValue = { Text(historyCount.toString()) },
        settingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() },
            onClick = historyClick
        )
    )

    customSettings?.invoke()
}

@ExperimentalMaterial3Api
@ExperimentalComposeUiApi
@Composable
private fun GeneralSettings(
    context: Context,
    scope: CoroutineScope,
    customSettings: (@Composable () -> Unit)?,
) {
    CategorySetting { Text(stringResource(R.string.general_menu_title)) }

    val source by sourceFlow.collectAsState(initial = null)

    val navController = LocalNavController.current

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.view_source_in_browser)) },
        settingIcon = { Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier
            .alpha(animateFloatAsState(targetValue = if (source != null) 1f else 0f).value)
            .clickable(
                enabled = source != null,
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() }
            ) {
                source?.baseUrl?.let {
                    navController.navigateChromeCustomTabs(
                        it,
                        {
                            anim {
                                enter = R.anim.slide_in_right
                                popEnter = R.anim.slide_in_right
                                exit = R.anim.slide_out_left
                                popExit = R.anim.slide_out_left
                            }
                        }
                    )
                }
            }
    )

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.viewTranslationModels)) },
        settingIcon = { Icon(Icons.Default.Language, null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() },
            onClick = { navController.navigate(Screen.TranslationScreen.route) }
        )
    )

    val handling = LocalSettingsHandling.current

    val themeSetting by handling.systemThemeMode.collectAsState(initial = SystemThemeMode.FollowSystem)

    val themeText by remember {
        derivedStateOf {
            when (themeSetting) {
                SystemThemeMode.FollowSystem -> "System"
                SystemThemeMode.Day -> "Light"
                SystemThemeMode.Night -> "Dark"
                else -> "None"
            }
        }
    }

    ListSetting(
        settingTitle = { Text(stringResource(R.string.theme_choice_title)) },
        dialogIcon = { Icon(Icons.Default.SettingsBrightness, null) },
        settingIcon = { Icon(Icons.Default.SettingsBrightness, null, modifier = Modifier.fillMaxSize()) },
        dialogTitle = { Text(stringResource(R.string.choose_a_theme)) },
        summaryValue = { Text(themeText) },
        confirmText = { TextButton(onClick = { it.value = false }) { Text(stringResource(R.string.cancel)) } },
        value = themeSetting,
        options = listOf(SystemThemeMode.FollowSystem, SystemThemeMode.Day, SystemThemeMode.Night),
        updateValue = { it, d ->
            d.value = false
            scope.launch { handling.setSystemThemeMode(it) }
            when (it) {
                SystemThemeMode.FollowSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                SystemThemeMode.Day -> AppCompatDelegate.MODE_NIGHT_NO
                SystemThemeMode.Night -> AppCompatDelegate.MODE_NIGHT_YES
                else -> null
            }?.let(AppCompatDelegate::setDefaultNightMode)
        }
    )

    val shareChapter by handling.shareChapter.collectAsState(initial = true)

    SwitchSetting(
        settingTitle = { Text(stringResource(R.string.share_chapters)) },
        settingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.fillMaxSize()) },
        value = shareChapter,
        updateValue = { scope.launch { handling.setShareChapter(it) } }
    )

    val showAllScreen by handling.showAll.collectAsState(initial = true)

    SwitchSetting(
        settingTitle = { Text(stringResource(R.string.show_all_screen)) },
        settingIcon = { Icon(Icons.Default.Menu, null, modifier = Modifier.fillMaxSize()) },
        value = showAllScreen,
        updateValue = { scope.launch { handling.setShowAll(it) } }
    )

    var sliderValue by remember { mutableStateOf(runBlocking { context.historySave.first().toFloat() }) }

    SliderSetting(
        sliderValue = sliderValue,
        settingTitle = { Text(stringResource(R.string.history_save_title)) },
        settingSummary = { Text(stringResource(R.string.history_save_summary)) },
        settingIcon = { Icon(Icons.Default.ChangeHistory, null) },
        range = -1f..100f,
        updateValue = {
            sliderValue = it
            scope.launch { context.updatePref(HISTORY_SAVE, sliderValue.toInt()) }
        }
    )

    customSettings?.invoke()
}

@Composable
private fun PlaySettings(scope: CoroutineScope, customSettings: (@Composable () -> Unit)?) {
    CategorySetting { Text(stringResource(R.string.playSettings)) }

    val settingsHandling = LocalSettingsHandling.current
    val slider by settingsHandling.batteryPercentage.collectAsState(runBlocking { settingsHandling.batteryPercentage.first() })
    var sliderValue by remember(slider) { mutableStateOf(slider.toFloat()) }

    SliderSetting(
        sliderValue = sliderValue,
        settingTitle = { Text(stringResource(R.string.battery_alert_percentage)) },
        settingSummary = { Text(stringResource(R.string.battery_default)) },
        settingIcon = { Icon(Icons.Default.BatteryAlert, null) },
        range = 1f..100f,
        updateValue = { sliderValue = it },
        onValueChangedFinished = { scope.launch { settingsHandling.setBatteryPercentage(sliderValue.toInt()) } }
    )

    customSettings?.invoke()
}

@Composable
private fun InfoSettings(
    infoViewModel: MoreInfoViewModel = viewModel(),
    logo: MainLogo,
    scope: CoroutineScope,
    activity: ComponentActivity,
    genericInfo: GenericInfo,
    usedLibraryClick: () -> Unit,
) {
    val navController = LocalNavController.current
    val context = LocalContext.current

    CategorySetting(settingTitle = { Text(stringResource(R.string.more_info_category)) })

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.view_libraries_used)) },
        settingIcon = { Icon(Icons.Default.LibraryBooks, null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() },
            onClick = usedLibraryClick
        )
    )

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.view_on_github)) },
        settingIcon = { Icon(painterResource(R.drawable.github_icon), null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() }
        ) { navController.navigateChromeCustomTabs("https://github.com/jakepurple13/OtakuWorld/releases/latest") }
    )

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.join_discord)) },
        settingIcon = { Icon(painterResource(R.drawable.ic_baseline_discord_24), null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() }
        ) { navController.navigateChromeCustomTabs("https://discord.gg/MhhHMWqryg") }
    )

    PreferenceSetting(
        settingTitle = { Text(stringResource(R.string.support)) },
        summaryValue = { Text(stringResource(R.string.support_summary)) },
        settingIcon = { Icon(Icons.Default.AttachMoney, null, modifier = Modifier.fillMaxSize()) },
        modifier = Modifier.clickable(
            indication = rememberRipple(),
            interactionSource = remember { MutableInteractionSource() }
        ) { navController.navigateChromeCustomTabs("https://ko-fi.com/V7V3D3JI") }
    )

    val appUpdate by updateAppCheck.collectAsState(null)

    PreferenceSetting(
        settingIcon = {
            Image(
                bitmap = AppCompatResources.getDrawable(context, logo.logoId)!!.toBitmap().asImageBitmap(),
                null,
                modifier = Modifier.fillMaxSize()
            )
        },
        settingTitle = { Text(stringResource(R.string.currentVersion, appVersion())) },
        modifier = Modifier.clickable { scope.launch(Dispatchers.IO) { infoViewModel.updateChecker(context) } }
    )

    ShowWhen(
        visibility = AppUpdate.checkForUpdate(appVersion(), appUpdate?.update_real_version.orEmpty())
    ) {
        var showDialog by remember { mutableStateOf(false) }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.updateTo, appUpdate?.update_real_version.orEmpty())) },
                text = { Text(stringResource(R.string.please_update_for_latest_features)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            activity.requestPermissions(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ) {
                                if (it.isGranted) {
                                    updateAppCheck.value
                                        ?.let { a ->
                                            val isApkAlreadyThere = File(
                                                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.absolutePath + "/",
                                                a.let(genericInfo.apkString).toString()
                                            )
                                            if (isApkAlreadyThere.exists()) isApkAlreadyThere.delete()
                                            DownloadUpdate(context, context.packageName).downloadUpdate(a)
                                        }
                                }
                            }
                            showDialog = false
                        }
                    ) { Text(stringResource(R.string.update)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.notNow)) }
                    TextButton(
                        onClick = {
                            navController.navigateChromeCustomTabs("https://github.com/jakepurple13/OtakuWorld/releases/latest")
                            showDialog = false
                        }
                    ) { Text(stringResource(R.string.gotoBrowser)) }
                }
            )
        }

        PreferenceSetting(
            settingTitle = { Text(stringResource(R.string.update_available)) },
            summaryValue = { Text(stringResource(R.string.updateTo, appUpdate?.update_real_version.orEmpty())) },
            modifier = Modifier.clickable(
                indication = rememberRipple(),
                interactionSource = remember { MutableInteractionSource() }
            ) { showDialog = true },
            settingIcon = {
                Icon(
                    Icons.Default.SystemUpdateAlt,
                    null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.fillMaxSize()
                )
            }
        )
    }
}

@Composable
fun TranslationScreen(vm: TranslationViewModel = viewModel()) {
    val scope = rememberCoroutineScope()

    LifecycleHandle(onResume = { vm.loadModels() })

    ListBottomScreen(
        title = stringResource(id = R.string.chooseModelToDelete),
        list = vm.translationModels.toList(),
        onClick = { item -> scope.launch { vm.deleteModel(item) } },
    ) {
        ListBottomSheetItemModel(
            primaryText = it.language,
            overlineText = try {
                Locale.forLanguageTag(it.language).displayLanguage
            } catch (e: Exception) {
                null
            },
            icon = Icons.Default.Delete
        )
    }
}