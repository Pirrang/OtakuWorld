package com.programmersbox.uiviews.notifications

import android.app.NotificationManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.android.material.datepicker.DateValidatorPointForward
import com.programmersbox.favoritesdatabase.ItemDao
import com.programmersbox.favoritesdatabase.NotificationItem
import com.programmersbox.favoritesdatabase.toDbModel
import com.programmersbox.favoritesdatabase.toItemModel
import com.programmersbox.gsonutils.toJson
import com.programmersbox.helpfulutils.notificationManager
import com.programmersbox.sharedutils.MainLogo
import com.programmersbox.uiviews.*
import com.programmersbox.uiviews.R
import com.programmersbox.uiviews.utils.*
import com.programmersbox.uiviews.utils.components.AnimatedLazyColumn
import com.programmersbox.uiviews.utils.components.AnimatedLazyListItem
import com.programmersbox.uiviews.utils.components.BottomSheetDeleteScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.compose.material3.MaterialTheme as M3MaterialTheme

private fun NotificationManager.cancelNotification(item: NotificationItem) {
    cancel(item.id)
    val g = activeNotifications.map { it.notification }.filter { it.group == "otakuGroup" }
    if (g.size == 1) cancel(42)
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun NotificationsScreen(
    notificationManager: NotificationManager,
    logo: MainLogo,
    notificationLogo: NotificationLogo,
    navController: NavController = LocalNavController.current,
    genericInfo: GenericInfo = LocalGenericInfo.current,
    db: ItemDao = LocalItemDao.current,
    settingsHandling: SettingsHandling = LocalSettingsHandling.current,
    vm: NotificationScreenViewModel = viewModel { NotificationScreenViewModel(db, settingsHandling, genericInfo) }
) {
    val items = vm.items

    LaunchedEffect(Unit) {
        db.getAllNotificationCount()
            .filter { it == 0 }
            .collect {
                notificationManager.cancel(42)
                navController.popBackStack()
            }
    }

    val state = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val logoDrawable = remember { AppCompatResources.getDrawable(context, logo.logoId) }

    BackHandler(state.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch { state.bottomSheetState.partialExpand() }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    BottomSheetDeleteScaffold(
        listOfItems = items,
        state = state,
        multipleTitle = stringResource(R.string.areYouSureRemoveNoti),
        bottomScrollBehavior = scrollBehavior,
        topBar = {
            var showPopup by remember { mutableStateOf(false) }

            if (showPopup) {

                val onDismiss = { showPopup = false }

                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text(stringResource(R.string.are_you_sure_delete_notifications)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val number = db.deleteAllNotifications()
                                    launch(Dispatchers.Main) {
                                        onDismiss()
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.deleted_notifications, number),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        context.notificationManager.cancel(42)
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.yes)) }
                    },
                    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } }
                )

            }

            InsetSmallTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(id = R.string.current_notification_count, items.size)) },
                actions = {
                    IconButton(onClick = { showPopup = true }) { Icon(Icons.Default.ClearAll, null) }
                    IconToggleButton(
                        checked = vm.sortedBy == NotificationSortBy.Grouped,
                        onCheckedChange = { vm.toggleSort() }
                    ) { Icon(Icons.Default.Sort, null) }
                },
                navigationIcon = { BackButton() }
            )
        },
        onRemove = { item ->
            vm.deleteNotification(db, item)
            notificationManager.cancelNotification(item)
        },
        onMultipleRemove = { d ->
            scope.launch {
                withContext(Dispatchers.Default) {
                    d.forEach {
                        notificationManager.cancelNotification(it)
                        db.deleteNotification(it)
                    }
                }
                d.clear()
            }
        },
        deleteTitle = { stringResource(R.string.removeNoti, it.notiTitle) },
        itemUi = { item ->
            ListItem(
                modifier = Modifier.padding(4.dp),
                leadingContent = {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.imageUrl)
                            .lifecycle(LocalLifecycleOwner.current)
                            .size(ComposableUtils.IMAGE_WIDTH_PX, ComposableUtils.IMAGE_HEIGHT_PX)
                            .crossfade(true)
                            .build(),
                        placeholder = rememberDrawablePainter(logoDrawable),
                        error = rememberDrawablePainter(logoDrawable),
                        contentScale = ContentScale.FillBounds,
                        contentDescription = item.notiTitle,
                        modifier = Modifier.size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                    )
                },
                overlineContent = { Text(item.source) },
                headlineContent = { Text(item.notiTitle) },
                supportingContent = { Text(item.summaryText) },
                trailingContent = {
                    var showDropDown by remember { mutableStateOf(false) }

                    DropdownMenu(
                        expanded = showDropDown,
                        onDismissRequest = { showDropDown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.remove_same_name)) },
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.Default) {
                                        items
                                            .filter { it.notiTitle == item.notiTitle }
                                            .forEach {
                                                notificationManager.cancelNotification(it)
                                                db.deleteNotification(it)
                                            }
                                    }
                                    showDropDown = false
                                    Toast.makeText(context, R.string.done, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    IconButton(onClick = { showDropDown = true }) { Icon(Icons.Default.MoreVert, null) }
                }
            )
        }
    ) { p, itemList ->
        Crossfade(targetState = vm.sortedBy) { target ->
            when (target) {
                NotificationSortBy.Date -> {
                    AnimatedLazyColumn(
                        contentPadding = p,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                        items = itemList.fastMap {
                            AnimatedLazyListItem(key = it.url, value = it) {
                                NotificationItem(
                                    item = it,
                                    navController = navController,
                                    vm = vm,
                                    notificationManager = notificationManager,
                                    db = db,
                                    genericInfo = genericInfo,
                                    logo = logo,
                                    notificationLogo = notificationLogo
                                )
                            }
                        }
                    )
                }
                NotificationSortBy.Grouped -> {
                    LazyColumn(
                        contentPadding = p,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {

                        vm.groupedList.toList().forEach { item ->
                            val expanded = vm.groupedListState[item.first]?.value == true

                            stickyHeader {
                                Surface(
                                    shape = M3MaterialTheme.shapes.medium,
                                    tonalElevation = 4.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { vm.toggleGroupedState(item.first) }
                                ) {
                                    ListItem(
                                        modifier = Modifier.padding(4.dp),
                                        headlineContent = { Text(item.first) },
                                        leadingContent = { Text(item.second.size.toString()) },
                                        trailingContent = {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                null,
                                                modifier = Modifier.rotate(animateFloatAsState(if (expanded) 180f else 0f).value)
                                            )
                                        }
                                    )
                                }
                            }

                            item {
                                Column(
                                    modifier = Modifier.animateContentSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (expanded) {
                                        item.second.forEach {
                                            NotificationItem(
                                                item = it,
                                                navController = navController,
                                                vm = vm,
                                                notificationManager = notificationManager,
                                                db = db,
                                                genericInfo = genericInfo,
                                                logo = logo,
                                                notificationLogo = notificationLogo
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                NotificationSortBy.UNRECOGNIZED -> {}
            }
        }

        /*AnimatedLazyColumn(
            contentPadding = p,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 4.dp),
            items = itemList.fastMap {
                AnimatedLazyListItem(key = it.url, value = it) {
                    NotificationItem(
                        item = it,
                        navController = navController,
                        vm = vm,
                        notificationManager = notificationManager,
                        db = db,
                        parentFragmentManager = fragmentManager,
                        genericInfo = genericInfo,
                        logo = logo,
                        notificationLogo = notificationLogo
                    )
                }
            }
        )*/

        /*LazyColumn(
            contentPadding = p,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) { items(itemList) { NotificationItem(item = it!!, navController = findNavController()) } }*/
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationItem(
    item: NotificationItem,
    navController: NavController,
    vm: NotificationScreenViewModel,
    notificationManager: NotificationManager,
    db: ItemDao,
    genericInfo: GenericInfo,
    logo: MainLogo,
    notificationLogo: NotificationLogo,
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPopup by remember { mutableStateOf(false) }

    if (showPopup) {
        val onDismiss = { showPopup = false }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.removeNoti, item.notiTitle)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteNotification(db, item, onDismiss)
                        notificationManager.cancelNotification(item)
                    }
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.no)) } }
        )
    }

    var showLoadingDialog by remember { mutableStateOf(false) }

    LoadingDialog(
        showLoadingDialog = showLoadingDialog,
        onDismissRequest = { showLoadingDialog = false }
    )

    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToEnd || it == DismissValue.DismissedToStart) {
                showPopup = true
            }
            false
        }
    )

    SwipeToDismiss(
        state = dismissState,
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    DismissValue.Default -> Color.Transparent
                    DismissValue.DismissedToEnd -> Color.Red
                    DismissValue.DismissedToStart -> Color.Red
                }
            )
            val alignment = when (direction) {
                DismissDirection.StartToEnd -> Alignment.CenterStart
                DismissDirection.EndToStart -> Alignment.CenterEnd
            }
            val icon = when (direction) {
                DismissDirection.StartToEnd -> Icons.Default.Delete
                DismissDirection.EndToStart -> Icons.Default.Delete
            }
            val scale by animateFloatAsState(if (dismissState.targetValue == DismissValue.Default) 0.75f else 1f)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.scale(scale),
                    tint = M3MaterialTheme.colorScheme.onSurface.copy(alpha = LocalContentAlpha.current)
                )
            }
        },
        dismissContent = {
            ElevatedCard(
                onClick = {
                    genericInfo
                        .toSource(item.source)
                        ?.let { source ->
                            Cached.cache[item.url]?.let {
                                flow {
                                    emit(
                                        it
                                            .toDbModel()
                                            .toItemModel(source)
                                    )
                                }
                            } ?: source.getSourceByUrlFlow(item.url)
                        }
                        ?.dispatchIo()
                        ?.onStart { showLoadingDialog = true }
                        ?.onEach {
                            showLoadingDialog = false
                            navController.navigateToDetails(it)
                        }
                        ?.launchIn(scope)
                },
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Row {
                    val logoDrawable = remember { AppCompatResources.getDrawable(context, logo.logoId) }
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.imageUrl)
                            .lifecycle(LocalLifecycleOwner.current)
                            .crossfade(true)
                            .build(),
                        placeholder = rememberDrawablePainter(logoDrawable),
                        error = rememberDrawablePainter(logoDrawable),
                        contentScale = ContentScale.Crop,
                        contentDescription = item.notiTitle,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(ComposableUtils.IMAGE_WIDTH, ComposableUtils.IMAGE_HEIGHT)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp, top = 4.dp)
                    ) {
                        Text(item.source, style = M3MaterialTheme.typography.labelMedium)
                        Text(item.notiTitle, style = M3MaterialTheme.typography.titleSmall)
                        Text(item.summaryText, style = M3MaterialTheme.typography.bodyMedium)
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(horizontal = 2.dp)
                    ) {

                        var showDropDown by remember { mutableStateOf(false) }

                        val dropDownDismiss = { showDropDown = false }

                        var showDatePicker by remember { mutableStateOf(false) }
                        var showTimePicker by remember { mutableStateOf(false) }

                        val dateState = rememberDatePickerState(
                            initialSelectedDateMillis = System.currentTimeMillis()
                        )
                        val calendar = remember { Calendar.getInstance() }
                        val is24HourFormat by rememberUpdatedState(DateFormat.is24HourFormat(context))
                        val timeState = rememberTimePickerState(
                            initialHour = calendar[Calendar.HOUR_OF_DAY],
                            initialMinute = calendar[Calendar.MINUTE],
                            is24Hour = is24HourFormat
                        )

                        //TODO: make all this code into a function
                        if (showTimePicker) {
                            AlertDialog(
                                onDismissRequest = { showTimePicker = false },
                                title = { Text(stringResource(id = R.string.selectTime)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showTimePicker = false
                                            val c = Calendar.getInstance()
                                            c.timeInMillis = dateState.selectedDateMillis ?: 0L
                                            c[Calendar.HOUR_OF_DAY] = timeState.hour
                                            c[Calendar.MINUTE] = timeState.minute

                                            WorkManager.getInstance(context)
                                                .enqueueUniqueWork(
                                                    item.notiTitle,
                                                    ExistingWorkPolicy.REPLACE,
                                                    OneTimeWorkRequestBuilder<NotifySingleWorker>()
                                                        .setInputData(
                                                            Data.Builder()
                                                                .putString("notiData", item.toJson())
                                                                .build()
                                                        )
                                                        .setInitialDelay(c.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                                                        .build()
                                                )

                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.willNotifyAt,
                                                    context.getSystemDateTimeFormat().format(c.timeInMillis)
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ) { Text(stringResource(R.string.ok)) }
                                },
                                dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
                                text = { TimePicker(state = timeState) }
                            )
                        }

                        if (showDatePicker) {
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDatePicker = false
                                            showTimePicker = true
                                        }
                                    ) { Text(stringResource(R.string.ok)) }
                                }
                            ) {
                                DatePicker(
                                    state = dateState,
                                    dateValidator = { DateValidatorPointForward.now().isValid(it) },
                                    title = { Text(stringResource(R.string.selectDate)) }
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showDropDown,
                            onDismissRequest = dropDownDismiss
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notify)) },
                                onClick = {
                                    dropDownDismiss()
                                    scope.launch(Dispatchers.IO) {
                                        SavedNotifications.viewNotificationFromDb(context, item, notificationLogo, genericInfo)
                                    }
                                }
                            )

                            Divider()

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notifyAtTime)) },
                                onClick = {
                                    dropDownDismiss()
                                    showDatePicker = true
                                }
                            )

                            Divider()

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    showPopup = true
                                },
                                text = { Text(stringResource(R.string.remove)) }
                            )
                        }

                        IconButton(onClick = { showDropDown = true }) { Icon(Icons.Default.MoreVert, null) }
                    }
                }
            }
        }
    )
}