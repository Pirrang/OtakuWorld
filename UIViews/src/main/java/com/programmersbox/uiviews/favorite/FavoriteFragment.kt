package com.programmersbox.uiviews.favorite

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.programmersbox.favoritesdatabase.DbModel
import com.programmersbox.favoritesdatabase.ItemDao
import com.programmersbox.favoritesdatabase.toItemModel
import com.programmersbox.models.ApiService
import com.programmersbox.sharedutils.MainLogo
import com.programmersbox.uiviews.BaseMainActivity
import com.programmersbox.uiviews.GenericInfo
import com.programmersbox.uiviews.R
import com.programmersbox.uiviews.utils.*
import com.programmersbox.uiviews.utils.components.GroupButton
import com.programmersbox.uiviews.utils.components.GroupButtonModel
import com.programmersbox.uiviews.utils.components.ListBottomScreen
import com.programmersbox.uiviews.utils.components.ListBottomSheetItemModel
import androidx.compose.material3.MaterialTheme as M3MaterialTheme

@ExperimentalMaterial3Api
@ExperimentalMaterialApi
@ExperimentalFoundationApi
@Composable
fun FavoriteUi(
    logo: MainLogo,
    genericInfo: GenericInfo = LocalGenericInfo.current,
    dao: ItemDao = LocalItemDao.current,
    viewModel: FavoriteViewModel = viewModel { FavoriteViewModel(dao, genericInfo) }
) {

    val navController = LocalNavController.current
    val activity = LocalActivity.current
    val context = LocalContext.current

    val favoriteItems: List<DbModel> = viewModel.favoriteList
    val allSources: List<ApiService> = genericInfo.sourceList()

    val focusManager = LocalFocusManager.current

    var searchText by rememberSaveable { mutableStateOf("") }

    val showing = remember(favoriteItems, searchText, viewModel.selectedSources) {
        favoriteItems.filter { it.title.contains(searchText, true) && it.source in viewModel.selectedSources }
    }

    val showingList = remember(viewModel.sortedBy, viewModel.reverse, showing) {
        showing
            .groupBy(DbModel::title)
            .entries
            .let {
                when (val s = viewModel.sortedBy) {
                    is SortFavoritesBy.TITLE -> it.sortedBy(s.sort)
                    is SortFavoritesBy.COUNT -> it.sortedByDescending(s.sort)
                    is SortFavoritesBy.CHAPTERS -> it.sortedByDescending(s.sort)
                }
            }
            .let { if (viewModel.reverse) it.reversed() else it }
            .toTypedArray()
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    var showBanner by remember { mutableStateOf(false) }

    M3OtakuBannerBox(
        showBanner = showBanner,
        placeholder = logo.logoId,
        modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues())
    ) { itemInfo ->
        OtakuScaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                Surface {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        InsetSmallTopAppBar(
                            scrollBehavior = scrollBehavior,
                            navigationIcon = { BackButton() },
                            title = { Text(stringResource(R.string.viewFavoritesMenu)) },
                            actions = {

                                val rotateIcon: @Composable (SortFavoritesBy<*>) -> Float = {
                                    animateFloatAsState(if (it == viewModel.sortedBy && viewModel.reverse) 180f else 0f).value
                                }

                                GroupButton(
                                    selected = viewModel.sortedBy,
                                    options = listOf(
                                        GroupButtonModel(SortFavoritesBy.TITLE) {
                                            Icon(
                                                Icons.Default.SortByAlpha,
                                                null,
                                                modifier = Modifier.rotate(rotateIcon(SortFavoritesBy.TITLE))
                                            )
                                        },
                                        GroupButtonModel(SortFavoritesBy.COUNT) {
                                            Icon(
                                                Icons.Default.Sort,
                                                null,
                                                modifier = Modifier.rotate(rotateIcon(SortFavoritesBy.COUNT))
                                            )
                                        },
                                        GroupButtonModel(SortFavoritesBy.CHAPTERS) {
                                            Icon(
                                                Icons.Default.ReadMore,
                                                null,
                                                modifier = Modifier.rotate(rotateIcon(SortFavoritesBy.CHAPTERS))
                                            )
                                        }
                                    )
                                ) { if (viewModel.sortedBy != it) viewModel.sortedBy = it else viewModel.reverse = !viewModel.reverse }
                            }
                        )

                        var active by rememberSaveable { mutableStateOf(false) }

                        fun closeSearchBar() {
                            focusManager.clearFocus()
                            active = false
                        }
                        SearchBar(
                            modifier = Modifier.fillMaxWidth(),
                            windowInsets = WindowInsets(0.dp),
                            query = searchText,
                            onQueryChange = { searchText = it },
                            onSearch = { closeSearchBar() },
                            active = active,
                            onActiveChange = {
                                active = it
                                if (!active) focusManager.clearFocus()
                            },
                            placeholder = {
                                Text(
                                    context.resources.getQuantityString(
                                        R.plurals.numFavorites,
                                        showing.size,
                                        showing.size
                                    )
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(Icons.Default.Cancel, null)
                                }
                            },
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                showing.take(4).forEachIndexed { index, dbModel ->
                                    ListItem(
                                        headlineContent = { Text(dbModel.title) },
                                        supportingContent = { Text(dbModel.source) },
                                        leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                                        modifier = Modifier.clickable {
                                            searchText = dbModel.title
                                            closeSearchBar()
                                        }
                                    )
                                    if (index != 3) {
                                        Divider()
                                    }
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = true,
                                    modifier = Modifier.combinedClickable(
                                        onClick = { viewModel.resetSources() },
                                        onLongClick = { viewModel.selectedSources.clear() }
                                    ),
                                    label = { Text("ALL") },
                                    onClick = { viewModel.allClick() }
                                )
                            }

                            items(
                                (allSources.fastMap(ApiService::serviceName) + showing.fastMap(DbModel::source))
                                    .groupBy { it }
                                    .toList()
                                    .sortedBy { it.first }
                            ) {
                                FilterChip(
                                    selected = it.first in viewModel.selectedSources,
                                    onClick = { viewModel.newSource(it.first) },
                                    label = { Text(it.first) },
                                    leadingIcon = { Text("${it.second.size - 1}") },
                                    modifier = Modifier.combinedClickable(
                                        onClick = { viewModel.newSource(it.first) },
                                        onLongClick = { viewModel.singleSource(it.first) }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { p ->
            if (showing.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(p)
                ) {

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        tonalElevation = 4.dp,
                        shape = RoundedCornerShape(4.dp)
                    ) {

                        Column(modifier = Modifier) {

                            Text(
                                text = stringResource(id = R.string.get_started),
                                style = M3MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Text(
                                text = stringResource(R.string.get_started_info),
                                style = M3MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Button(
                                onClick = { (activity as? BaseMainActivity)?.goToScreen(BaseMainActivity.Screen.RECENT) },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(vertical = 4.dp)
                            ) { Text(text = stringResource(R.string.add_a_favorite)) }

                        }

                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = adaptiveGridCell(),
                    state = rememberLazyGridState(),
                    contentPadding = p,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        showingList,
                        key = { it.key }
                    ) { info ->
                        M3CoverCard(
                            onLongPress = { c ->
                                itemInfo.value = if (c == ComponentState.Pressed) {
                                    info.value.randomOrNull()
                                        ?.let { genericInfo.toSource(it.source)?.let { it1 -> it.toItemModel(it1) } }
                                } else null
                                showBanner = c == ComponentState.Pressed
                            },
                            imageUrl = info.value.randomOrNull()?.imageUrl.orEmpty(),
                            name = info.key,
                            placeHolder = logo.logoId,
                            favoriteIcon = {
                                if (info.value.size > 1) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = M3MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                        Text(
                                            info.value.size.toString(),
                                            color = M3MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        ) {
                            if (info.value.size == 1) {
                                info.value
                                    .firstOrNull()
                                    ?.let { genericInfo.toSource(it.source)?.let { it1 -> it.toItemModel(it1) } }
                                    ?.let { navController.navigateToDetails(it) }
                            } else {
                                Screen.FavoriteChoiceScreen.navigate(navController, info.value)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteChoiceScreen(vm: FavoriteChoiceViewModel = viewModel { FavoriteChoiceViewModel(createSavedStateHandle()) }) {
    val genericInfo = LocalGenericInfo.current
    val navController = LocalNavController.current
    ListBottomScreen(
        includeInsetPadding = false,
        title = stringResource(R.string.chooseASource),
        list = vm.items,
        onClick = { item ->
            item
                .let { genericInfo.toSource(it.source)?.let { it1 -> it.toItemModel(it1) } }
                ?.let { navController.navigateToDetails(it) }
        }
    ) {
        ListBottomSheetItemModel(
            primaryText = it.title,
            overlineText = it.source
        )
    }
}