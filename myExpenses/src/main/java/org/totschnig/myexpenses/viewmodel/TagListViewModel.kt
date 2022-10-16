package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.compose.toggle
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COUNT
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_LABEL
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.appendBooleanQueryParameter
import org.totschnig.myexpenses.provider.getIntIfExists
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.viewmodel.data.Tag

class TagListViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    TagBaseViewModel(application, savedStateHandle) {

    fun toggleSelectedTagId(tagId: Long) {
        savedStateHandle[KEY_SELECTED_IDS] = getSelectedTagIds().toMutableSet().apply {
            toggle(tagId)
        }
    }

    fun getSelectedTagIds(): HashSet<Long> {
        return savedStateHandle.get<HashSet<Long>>(KEY_SELECTED_IDS) ?: HashSet()
    }

    fun loadTags(selected: List<Tag>?) {
        viewModelScope.launch(context = coroutineContext()) {
        if (tagsInternal.value == null) {
            val tagsUri = TransactionProvider.TAGS_URI.buildUpon()
                .appendBooleanQueryParameter(TransactionProvider.QUERY_PARAMETER_WITH_COUNT).build()
            contentResolver.observeQuery(
                uri = tagsUri,
                sortOrder = "$KEY_LABEL COLLATE LOCALIZED",
                notifyForDescendants = true
            ).mapToList { cursor ->
                    val id = cursor.getLong(KEY_ROWID)
                    val label = cursor.getString(KEY_LABEL)
                    val count = cursor.getIntIfExists(KEY_COUNT) ?: -1
                    Tag(id, label, count)
                }.collect(tagsInternal::postValue)
        }
    }
    }

    fun removeTagAndPersist(tag: Tag) {
        viewModelScope.launch(context = coroutineContext()) {
            if (contentResolver.delete(
                    ContentUris.withAppendedId(
                        TransactionProvider.TAGS_URI,
                        tag.id
                    ), null, null
                ) == 1
            ) {
                addDeletedTagId(tag.id)
            }
        }
    }

    fun addTagAndPersist(label: String): LiveData<Boolean> =
        liveData(context = viewModelScope.coroutineContext + Dispatchers.IO) {
            val result = contentResolver.insert(TransactionProvider.TAGS_URI,
                ContentValues().apply { put(KEY_LABEL, label) })
            emit(result != null)
        }

    fun updateTag(tag: Tag, newLabel: String) =
        liveData(context = viewModelScope.coroutineContext + Dispatchers.IO) {
            val result = try {
                contentResolver.update(ContentUris.withAppendedId(
                    TransactionProvider.TAGS_URI,
                    tag.id
                ),
                    ContentValues().apply { put(KEY_LABEL, newLabel) }, null, null
                )
            } catch (e: SQLiteConstraintException) {
                0
            }
            val success = result == 1
            if (success) {
                tagsInternal.postValue(tagsInternal.value?.map {
                    if (it == tag) Tag(
                        tag.id,
                        newLabel,
                        tag.count
                    ) else it
                })
            }
            emit(success)
        }

    companion object {
        private const val KEY_SELECTED_IDS = "selectedIds"
    }
}