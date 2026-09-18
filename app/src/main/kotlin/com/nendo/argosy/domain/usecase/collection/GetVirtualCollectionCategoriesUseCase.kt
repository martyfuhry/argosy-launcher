package com.nendo.argosy.domain.usecase.collection

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

internal const val COLLECTION_COVER_LIMIT = 4

data class CategoryWithCount(
    val name: String,
    val gameCount: Int,
    val coverPaths: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class GetVirtualCollectionCategoriesUseCase @Inject constructor(
    private val collectionDao: CollectionDao
) {
    fun getGenres(): Flow<List<CategoryWithCount>> {
        return getCategoriesByType(CollectionType.GENRE)
    }

    fun getGameModes(): Flow<List<CategoryWithCount>> {
        return getCategoriesByType(CollectionType.GAME_MODE)
    }

    fun getSeries(): Flow<List<CategoryWithCount>> {
        return getCategoriesByType(CollectionType.SERIES)
    }

    private fun getCategoriesByType(type: CollectionType): Flow<List<CategoryWithCount>> {
        return combine(
            collectionDao.observeByType(type),
            collectionDao.observeLocalGameCounts(),
            collectionDao.observeLocalCoverPaths()
        ) { collections, counts, covers ->
            val countById = counts.associate { it.collectionId to it.gameCount }
            val coversById = covers.groupBy { it.collectionId }
            collections.mapNotNull { collection ->
                val count = countById[collection.id] ?: 0
                if (count == 0) return@mapNotNull null
                CategoryWithCount(
                    name = collection.name,
                    gameCount = count,
                    coverPaths = coversById[collection.id]
                        ?.take(COLLECTION_COVER_LIMIT)
                        ?.map { it.coverPath }
                        ?: emptyList()
                )
            }.sortedBy { it.name }
        }
            .distinctUntilChanged()
            .onStart { emit(emptyList()) }
            .flowOn(Dispatchers.IO)
    }
}
