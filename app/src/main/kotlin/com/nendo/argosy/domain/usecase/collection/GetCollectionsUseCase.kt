package com.nendo.argosy.domain.usecase.collection

import android.util.Log
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.entity.CollectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

data class CollectionWithCount(
    val id: Long,
    val name: String,
    val description: String?,
    val gameCount: Int,
    val coverPaths: List<String>,
    val isUserCreated: Boolean,
    val rommId: Long?
)

class GetCollectionsUseCase @Inject constructor(
    private val collectionDao: CollectionDao
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<CollectionWithCount>> {
        return combine(
            collectionDao.observeByTypes(listOf(CollectionType.REGULAR, CollectionType.SMART)),
            collectionDao.observeLocalGameCounts(),
            collectionDao.observeLocalCoverPaths()
        ) { collections, counts, covers ->
            val countById = counts.associate { it.collectionId to it.gameCount }
            val coversById = covers.groupBy { it.collectionId }
            collections
                .filter { it.name.isNotBlank() && it.name.lowercase() != "favorites" }
                .map { collection ->
                    CollectionWithCount(
                        id = collection.id,
                        name = collection.name,
                        description = collection.description,
                        gameCount = countById[collection.id] ?: 0,
                        coverPaths = coversById[collection.id]
                            ?.take(COLLECTION_COVER_LIMIT)
                            ?.map { cover -> cover.coverPath }
                            ?: emptyList(),
                        isUserCreated = collection.isUserCreated,
                        rommId = collection.rommId
                    )
                }
        }
            .distinctUntilChanged()
            .onStart { emit(emptyList()) }
            .flowOn(Dispatchers.IO)
    }
}
