package org.helllabs.android.xmp.api

import org.helllabs.android.xmp.model.ArtistResult
import org.helllabs.android.xmp.model.ModuleResult
import org.helllabs.android.xmp.model.SearchListResult
import javax.inject.Inject

class ApiHelperImpl @Inject constructor(private val apiService: ApiService) : ApiHelper {

    override suspend fun getArtistSearch(
        apiKey: String,
        byArtist: String,
        query: String
    ): ArtistResult {
        return apiService.getArtistSearch(apiKey, byArtist, query)
    }

    override suspend fun getModuleById(
        apiKey: String,
        byModuleId: String,
        query: Int
    ): ModuleResult {
        return apiService.getModuleById(apiKey, byModuleId, query)
    }

    override suspend fun getRandomModule(
        apiKey: String,
        byRandom: String
    ): ModuleResult {
        return apiService.getRandomModule(apiKey, byRandom)
    }

    override suspend fun getArtistById(
        apiKey: String,
        byArtistId: String,
        query: Int
    ): SearchListResult {
        return apiService.getArtistById(apiKey, byArtistId, query)
    }

    override suspend fun getSearchByFileNameOrTitle(
        apiKey: String,
        bySearch: String,
        typeFileOrTitle: String,
        query: String
    ): SearchListResult {
        return apiService.getSearchByFileNameOrTitle(apiKey, bySearch, typeFileOrTitle, query)
    }
}
