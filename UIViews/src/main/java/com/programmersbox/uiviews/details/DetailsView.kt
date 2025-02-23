package com.programmersbox.uiviews.details

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import com.programmersbox.favoritesdatabase.ChapterWatched
import com.programmersbox.favoritesdatabase.NotificationItem
import com.programmersbox.models.ChapterModel
import com.programmersbox.models.InfoModel
import com.programmersbox.uiviews.R
import com.programmersbox.uiviews.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.onebone.toolbar.CollapsingToolbarScaffold
import me.onebone.toolbar.ScrollStrategy
import me.onebone.toolbar.rememberCollapsingToolbarScaffoldState
import my.nanihadesuka.compose.LazyColumnScrollbar
import kotlin.math.ln

@ExperimentalComposeUiApi
@ExperimentalMaterial3Api
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun DetailsView(
    info: InfoModel,
    isSaved: Boolean,
    shareChapter: Boolean,
    chapters: List<ChapterWatched>,
    isFavorite: Boolean,
    onFavoriteClick: (Boolean) -> Unit,
    markAs: (ChapterModel, Boolean) -> Unit,
    logo: NotificationLogo,
    description: String,
    onTranslateDescription: (MutableState<Boolean>) -> Unit
) {
    val dao = LocalItemDao.current
    val swatchInfo = LocalSwatchInfo.current.colors
    val genericInfo = LocalGenericInfo.current
    val navController = LocalNavController.current
    var reverseChapters by remember { mutableStateOf(false) }

    val hostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberDrawerState(DrawerValue.Closed)

    val context = LocalContext.current

    BackHandler(scaffoldState.isOpen) {
        scope.launch {
            try {
                scaffoldState.close()
            } catch (e: Exception) {
                navController.popBackStack()
            }
        }
    }

    val topBarColor by animateColorAsState(swatchInfo?.bodyColor?.toComposeColor() ?: MaterialTheme.colorScheme.onSurface)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    ModalNavigationDrawer(
        drawerState = scaffoldState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(
                    topStart = 0.0.dp,
                    topEnd = 8.0.dp,
                    bottomEnd = 8.0.dp,
                    bottomStart = 0.0.dp
                ),
                windowInsets = WindowInsets(0.dp)
            ) {
                MarkAsScreen(
                    topBarColor = topBarColor,
                    drawerState = scaffoldState,
                    info = info,
                    chapters = chapters,
                    markAs = markAs
                )
            }
        }
    ) {
        val b = MaterialTheme.colorScheme.background
        val c by animateColorAsState(swatchInfo?.rgb?.toComposeColor() ?: b)

        OtakuScaffold(
            containerColor = Color.Transparent,
            topBar = {
                InsetSmallTopAppBar(
                    modifier = Modifier.zIndex(2f),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = swatchInfo?.rgb?.toComposeColor()?.animate()?.value
                            ?: MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = swatchInfo?.rgb?.toComposeColor()?.animate()?.value?.let {
                            MaterialTheme.colorScheme.surface.surfaceColorAtElevation(1.dp, it)
                        } ?: MaterialTheme.colorScheme.applyTonalElevation(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            elevation = 1.dp
                        ),
                        titleContentColor = topBarColor
                    ),
                    scrollBehavior = scrollBehavior,
                    title = {
                        Text(
                            info.title,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, null, tint = topBarColor)
                        }
                    },
                    actions = {
                        var showDropDown by remember { mutableStateOf(false) }

                        val dropDownDismiss = { showDropDown = false }

                        DropdownMenu(
                            expanded = showDropDown,
                            onDismissRequest = dropDownDismiss,
                        ) {

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    scope.launch { scaffoldState.open() }
                                },
                                text = { Text(stringResource(id = R.string.markAs)) },
                                leadingIcon = { Icon(Icons.Default.Check, null) }
                            )

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    navController.navigateChromeCustomTabs(info.url)
                                },
                                text = { Text(stringResource(id = R.string.fallback_menu_item_open_in_browser)) },
                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) }
                            )

                            if (!isSaved) {
                                DropdownMenuItem(
                                    onClick = {
                                        dropDownDismiss()
                                        scope.launch(Dispatchers.IO) {
                                            dao.insertNotification(
                                                NotificationItem(
                                                    id = info.hashCode(),
                                                    url = info.url,
                                                    summaryText = context
                                                        .getString(
                                                            R.string.hadAnUpdate,
                                                            info.title,
                                                            info.chapters.firstOrNull()?.name.orEmpty()
                                                        ),
                                                    notiTitle = info.title,
                                                    imageUrl = info.imageUrl,
                                                    source = info.source.serviceName,
                                                    contentTitle = info.title
                                                )
                                            )
                                        }
                                    },
                                    text = { Text(stringResource(id = R.string.save_for_later)) },
                                    leadingIcon = { Icon(Icons.Default.Save, null) }
                                )
                            } else {
                                DropdownMenuItem(
                                    onClick = {
                                        dropDownDismiss()
                                        scope.launch(Dispatchers.IO) {
                                            dao.getNotificationItemFlow(info.url)
                                                .firstOrNull()
                                                ?.let { dao.deleteNotification(it) }
                                        }
                                    },
                                    text = { Text(stringResource(R.string.removeNotification)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                            }

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    Screen.GlobalSearchScreen.navigate(navController, info.title)
                                },
                                text = { Text(stringResource(id = R.string.global_search_by_name)) },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    reverseChapters = !reverseChapters
                                },
                                text = { Text(stringResource(id = R.string.reverseOrder)) },
                                leadingIcon = { Icon(Icons.Default.Sort, null) }
                            )
                        }

                        IconButton(
                            onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, info.url)
                                    putExtra(Intent.EXTRA_TITLE, info.title)
                                }, context.getString(R.string.share_item, info.title)))
                            }
                        ) { Icon(Icons.Default.Share, null, tint = topBarColor) }

                        genericInfo.DetailActions(infoModel = info, tint = topBarColor)

                        IconButton(onClick = { showDropDown = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = topBarColor)
                        }
                    }
                )
            },
            snackbarHost = {
                SnackbarHost(hostState) { data ->
                    val background = swatchInfo?.rgb?.toComposeColor() ?: SnackbarDefaults.color
                    val font = swatchInfo?.titleColor?.toComposeColor() ?: MaterialTheme.colorScheme.surface
                    Snackbar(
                        containerColor = Color(ColorUtils.blendARGB(background.toArgb(), MaterialTheme.colorScheme.onSurface.toArgb(), .25f)),
                        contentColor = font,
                        snackbarData = data
                    )
                }
            },
            modifier = Modifier
                .drawBehind { drawRect(Brush.verticalGradient(listOf(c, b))) }
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { p ->

            val header: @Composable () -> Unit = {
                DetailsHeader(
                    model = info,
                    logo = painterResource(id = logo.notificationId),
                    isFavorite = isFavorite,
                    favoriteClick = onFavoriteClick
                )
            }

            val state = rememberCollapsingToolbarScaffoldState()

            CollapsingToolbarScaffold(
                modifier = Modifier.padding(p),
                state = state,
                scrollStrategy = ScrollStrategy.EnterAlwaysCollapsed,
                toolbar = { header() }
            ) {
                val listState = rememberLazyListState()

                LazyColumnScrollbar(
                    enabled = true,
                    thickness = 8.dp,
                    padding = 2.dp,
                    listState = listState,
                    thumbColor = swatchInfo?.bodyColor?.toComposeColor() ?: MaterialTheme.colorScheme.primary,
                    thumbSelectedColor = (swatchInfo?.bodyColor?.toComposeColor() ?: MaterialTheme.colorScheme.primary).copy(alpha = .6f),
                ) {
                    var descriptionVisibility by remember { mutableStateOf(false) }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        state = listState
                    ) {

                        if (info.description.isNotEmpty()) {
                            item {
                                Box {
                                    val progress = remember { mutableStateOf(false) }

                                    Text(
                                        description,
                                        modifier = Modifier
                                            .combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = rememberRipple(),
                                                onClick = { descriptionVisibility = !descriptionVisibility },
                                                onLongClick = { onTranslateDescription(progress) }
                                            )
                                            .padding(horizontal = 4.dp)
                                            .fillMaxWidth()
                                            .animateContentSize(),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = if (descriptionVisibility) Int.MAX_VALUE else 3,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (progress.value) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }

                        items(info.chapters.let { if (reverseChapters) it.reversed() else it }) { c ->
                            ChapterItem(
                                infoModel = info,
                                c = c,
                                read = chapters,
                                chapters = info.chapters,
                                shareChapter = shareChapter,
                                markAs = markAs
                            )
                        }
                    }
                }
            }
        }
    }
}

@ExperimentalComposeUiApi
@ExperimentalMaterial3Api
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun DetailsViewLandscape(
    info: InfoModel,
    isSaved: Boolean,
    shareChapter: Boolean,
    chapters: List<ChapterWatched>,
    isFavorite: Boolean,
    onFavoriteClick: (Boolean) -> Unit,
    markAs: (ChapterModel, Boolean) -> Unit,
    logo: NotificationLogo,
    description: String,
    onTranslateDescription: (MutableState<Boolean>) -> Unit
) {
    val dao = LocalItemDao.current
    val swatchInfo = LocalSwatchInfo.current.colors
    val genericInfo = LocalGenericInfo.current
    val navController = LocalNavController.current
    val context = LocalContext.current

    var reverseChapters by remember { mutableStateOf(false) }

    val hostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberDrawerState(DrawerValue.Closed)

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    BackHandler(scaffoldState.isOpen) {
        scope.launch {
            try {
                scaffoldState.close()
            } catch (e: Exception) {
                navController.popBackStack()
            }
        }
    }

    val topBarColor = swatchInfo?.bodyColor?.toComposeColor()?.animate()?.value ?: MaterialTheme.colorScheme.onSurface

    ModalNavigationDrawer(
        drawerState = scaffoldState,
        drawerContent = {
            ModalDrawerSheet {
                MarkAsScreen(
                    topBarColor = topBarColor,
                    drawerState = scaffoldState,
                    info = info,
                    chapters = chapters,
                    markAs = markAs
                )
            }
        }
    ) {
        OtakuScaffold(
            containerColor = Color.Transparent,
            topBar = {
                InsetSmallTopAppBar(
                    modifier = Modifier.zIndex(2f),
                    colors = TopAppBarDefaults.topAppBarColors(
                        titleContentColor = topBarColor,
                        containerColor = swatchInfo?.rgb?.toComposeColor()?.animate()?.value ?: MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = swatchInfo?.rgb?.toComposeColor()?.animate()?.value?.let {
                            MaterialTheme.colorScheme.surface.surfaceColorAtElevation(1.dp, it)
                        } ?: MaterialTheme.colorScheme.applyTonalElevation(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            elevation = 1.dp
                        )
                    ),
                    scrollBehavior = scrollBehavior,
                    title = { Text(info.title) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, null, tint = topBarColor)
                        }
                    },
                    actions = {
                        var showDropDown by remember { mutableStateOf(false) }

                        val dropDownDismiss = { showDropDown = false }

                        DropdownMenu(
                            expanded = showDropDown,
                            onDismissRequest = dropDownDismiss,
                        ) {

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    scope.launch { scaffoldState.open() }
                                },
                                text = { Text(stringResource(id = R.string.markAs)) },
                                leadingIcon = { Icon(Icons.Default.Check, null) }
                            )

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    navController.navigateChromeCustomTabs(info.url)
                                },
                                text = { Text(stringResource(id = R.string.fallback_menu_item_open_in_browser)) },
                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) }
                            )

                            if (!isSaved) {
                                DropdownMenuItem(
                                    onClick = {
                                        dropDownDismiss()
                                        scope.launch(Dispatchers.IO) {
                                            dao.insertNotification(
                                                NotificationItem(
                                                    id = info.hashCode(),
                                                    url = info.url,
                                                    summaryText = context
                                                        .getString(
                                                            R.string.hadAnUpdate,
                                                            info.title,
                                                            info.chapters.firstOrNull()?.name.orEmpty()
                                                        ),
                                                    notiTitle = info.title,
                                                    imageUrl = info.imageUrl,
                                                    source = info.source.serviceName,
                                                    contentTitle = info.title
                                                )
                                            )
                                        }
                                    },
                                    text = { Text(stringResource(id = R.string.save_for_later)) },
                                    leadingIcon = { Icon(Icons.Default.Save, null) }
                                )
                            } else {
                                DropdownMenuItem(
                                    onClick = {
                                        dropDownDismiss()
                                        scope.launch(Dispatchers.IO) {
                                            dao.getNotificationItemFlow(info.url)
                                                .firstOrNull()
                                                ?.let { dao.deleteNotification(it) }
                                        }
                                    },
                                    text = { Text(stringResource(R.string.removeNotification)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                                )
                            }

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    Screen.GlobalSearchScreen.navigate(navController, info.title)
                                },
                                text = { Text(stringResource(id = R.string.global_search_by_name)) },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )

                            DropdownMenuItem(
                                onClick = {
                                    dropDownDismiss()
                                    reverseChapters = !reverseChapters
                                },
                                text = { Text(stringResource(id = R.string.reverseOrder)) },
                                leadingIcon = { Icon(Icons.Default.Sort, null) }
                            )
                        }

                        IconButton(
                            onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, info.url)
                                    putExtra(Intent.EXTRA_TITLE, info.title)
                                }, context.getString(R.string.share_item, info.title)))
                            }
                        ) { Icon(Icons.Default.Share, null, tint = topBarColor) }

                        genericInfo.DetailActions(infoModel = info, tint = topBarColor)

                        IconButton(onClick = { showDropDown = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = topBarColor)
                        }
                    }
                )
            },
            snackbarHost = {
                SnackbarHost(hostState) { data ->
                    val background = swatchInfo?.rgb?.toComposeColor() ?: SnackbarDefaults.color
                    val font = swatchInfo?.titleColor?.toComposeColor() ?: MaterialTheme.colorScheme.surface
                    Snackbar(
                        containerColor = Color(ColorUtils.blendARGB(background.toArgb(), MaterialTheme.colorScheme.onSurface.toArgb(), .25f)),
                        contentColor = font,
                        snackbarData = data
                    )
                }
            },
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            swatchInfo?.rgb
                                ?.toComposeColor()
                                ?.animate()?.value ?: MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { p ->
            DetailsLandscapeContent(
                p = p,
                info = info,
                shareChapter = shareChapter,
                logo = logo,
                reverseChapters = reverseChapters,
                description = description,
                onTranslateDescription = onTranslateDescription,
                chapters = chapters,
                markAs = markAs,
                isFavorite = isFavorite,
                onFavoriteClick = onFavoriteClick
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DetailsLandscapeContent(
    p: PaddingValues,
    info: InfoModel,
    shareChapter: Boolean,
    isFavorite: Boolean,
    onFavoriteClick: (Boolean) -> Unit,
    markAs: (ChapterModel, Boolean) -> Unit,
    description: String,
    onTranslateDescription: (MutableState<Boolean>) -> Unit,
    chapters: List<ChapterWatched>,
    logo: NotificationLogo,
    reverseChapters: Boolean
) {
    Row(
        modifier = Modifier.padding(p)
    ) {
        DetailsHeader(
            modifier = Modifier.weight(1f),
            model = info,
            logo = painterResource(id = logo.notificationId),
            isFavorite = isFavorite,
            favoriteClick = onFavoriteClick
        )

        val listState = rememberLazyListState()

        var descriptionVisibility by remember { mutableStateOf(false) }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .padding(vertical = 4.dp),
            state = listState
        ) {

            if (info.description.isNotEmpty()) {
                item {
                    Box {
                        val progress = remember { mutableStateOf(false) }

                        Text(
                            description,
                            modifier = Modifier
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(),
                                    onClick = { descriptionVisibility = !descriptionVisibility },
                                    onLongClick = { onTranslateDescription(progress) }
                                )
                                .padding(horizontal = 4.dp)
                                .fillMaxWidth()
                                .animateContentSize(),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = if (descriptionVisibility) Int.MAX_VALUE else 3,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (progress.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            items(info.chapters.let { if (reverseChapters) it.reversed() else it }) { c ->
                ChapterItem(
                    infoModel = info,
                    c = c,
                    read = chapters,
                    chapters = info.chapters,
                    shareChapter = shareChapter,
                    markAs = markAs
                )
            }
        }
    }
}

/**
 * Returns the new background [Color] to use, representing the original background [color] with an
 * overlay corresponding to [elevation] applied. The overlay will only be applied to
 * [ColorScheme.surface].
 */
internal fun ColorScheme.applyTonalElevation(backgroundColor: Color, elevation: Dp): Color {
    return if (backgroundColor == surface) {
        surfaceColorAtElevation(elevation)
    } else {
        backgroundColor
    }
}

/**
 * Returns the [ColorScheme.surface] color with an alpha of the [ColorScheme.primary] color overlaid
 * on top of it.
 * Computes the surface tonal color at different elevation levels e.g. surface1 through surface5.
 *
 * @param elevation Elevation value used to compute alpha of the color overlay layer.
 */
internal fun ColorScheme.surfaceColorAtElevation(
    elevation: Dp,
): Color {
    if (elevation == 0.dp) return surface
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return primary.copy(alpha = alpha).compositeOver(surface)
}

internal fun Color.surfaceColorAtElevation(
    elevation: Dp,
    surface: Color
): Color {
    if (elevation == 0.dp) return surface
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return copy(alpha = alpha).compositeOver(surface)
}