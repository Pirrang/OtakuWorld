package com.programmersbox.uiviews.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.programmersbox.favoritesdatabase.ItemDao
import com.programmersbox.sharedutils.*
import com.programmersbox.uiviews.R
import com.programmersbox.uiviews.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AccountViewModel : ViewModel() {

    var accountInfo by mutableStateOf<CustomFirebaseUser?>(null)

    init {
        FirebaseAuthentication.addAuthStateListener { p0 -> accountInfo = p0 }
    }

    fun signInOrOut(context: Context, activity: ComponentActivity) {
        FirebaseAuthentication.signInOrOut(context, activity, R.string.logOut, R.string.areYouSureLogOut, R.string.yes, R.string.no)
    }

    override fun onCleared() {
        super.onCleared()
        FirebaseAuthentication.clear()
    }
}

class AboutViewModel(context: Context) : ViewModel() {

    var canCheck by mutableStateOf(false)

    var time by mutableStateOf("")

    init {
        viewModelScope.launch { context.shouldCheckFlow.collect { canCheck = it } }

        combine(
            context.updateCheckingStart.map { "Start: ${context.getSystemDateTimeFormat().format(it)}" },
            context.updateCheckingEnd.map { "End: ${context.getSystemDateTimeFormat().format(it)}" }
        ) { s, e -> s to e }
            .map { "${it.first}\n${it.second}" }
            .onEach { time = it }
            .launchIn(viewModelScope)
    }

    private val checker = AtomicBoolean(false)

    suspend fun updateChecker(context: Context) {
        try {
            if (!checker.get()) {
                checker.set(true)
                AppUpdate.getUpdate()?.let(updateAppCheck::tryEmit)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            checker.set(false)
            withContext(Dispatchers.Main) { context.let { c -> Toast.makeText(c, "Done Checking", Toast.LENGTH_SHORT).show() } }
        }
    }
}

class MoreInfoViewModel : ViewModel() {

    private val checker = AtomicBoolean(false)

    suspend fun updateChecker(context: Context) {
        try {
            if (!checker.get()) {
                checker.set(true)
                AppUpdate.getUpdate()?.let(updateAppCheck::tryEmit)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            checker.set(false)
            withContext(Dispatchers.Main) { context.let { c -> Toast.makeText(c, "Done Checking", Toast.LENGTH_SHORT).show() } }
        }
    }
}

class NotificationViewModel(dao: ItemDao) : ViewModel() {

    var savedNotifications by mutableStateOf(0)
        private set

    init {
        dao.getAllNotificationCount()
            .dispatchIo()
            .onEach { savedNotifications = it }
            .launchIn(viewModelScope)
    }

}

class TranslationViewModel : ViewModel() {

    var translationModels: List<CustomRemoteModel> by mutableStateOf(emptyList())
        private set

    fun loadModels() {
        TranslatorUtils.getModels { translationModels = it }
    }

    suspend fun deleteModel(model: CustomRemoteModel) {
        TranslatorUtils.deleteModel(model)
        TranslatorUtils.getModels { translationModels = it }
    }

}