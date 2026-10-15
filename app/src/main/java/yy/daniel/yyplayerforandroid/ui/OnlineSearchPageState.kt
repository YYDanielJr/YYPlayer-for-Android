package yy.daniel.yyplayerforandroid.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import yy.daniel.yyplayerforandroid.online.OnlineSong

/** Retain result data across configuration changes without putting large lists in a Bundle. */
internal class OnlineSearchPageState : ViewModel() {
    val songs = mutableStateOf(emptyList<OnlineSong>())
    val searched = mutableStateOf(false)
    val page = mutableIntStateOf(1)
    val lastQuery = mutableStateOf("")
    val more = mutableStateOf(false)
    var sourceKey: List<Any?>? = null
}
