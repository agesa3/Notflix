package com.vickikbt.repository.data_source

import androidx.lifecycle.MutableLiveData
import com.vickikbt.cache.AppDatabase
import com.vickikbt.cache.datastore.TimeDatastore
import com.vickikbt.cache.models.MovieEntity
import com.vickikbt.domain.models.Movie
import com.vickikbt.domain.repository.UpcomingMoviesRepository
import com.vickikbt.domain.utils.Constants
import com.vickikbt.domain.utils.Coroutines
import com.vickikbt.network.ApiService
import com.vickikbt.network.utils.SafeApiRequest
import com.vickikbt.repository.mappers.toDomain
import com.vickikbt.repository.mappers.toEntity
import com.vickikbt.repository.utils.TimeUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UpcomingMoviesMoviesRepositoryImpl constructor(
    private val apiService: ApiService,
    private val appDatabase: AppDatabase,
    private val timeDatastore: TimeDatastore
) : SafeApiRequest(), UpcomingMoviesRepository {

    private val moviesDao = appDatabase.moviesDao()
    private val upcomingMoviesLivedata = MutableLiveData<List<MovieEntity>>()

    init {
        upcomingMoviesLivedata.observeForever {
            Coroutines.io { saveUpcomingMovies(it) }
        }
    }

    private suspend fun saveUpcomingMovies(movies: List<MovieEntity>) =
        moviesDao.saveMovies(movieEntities = movies)

    override suspend fun fetchUpcomingMovies(): Flow<List<Movie>> {
        val isCategoryCacheAvailable = moviesDao.isCategoryCacheAvailable("upcoming") > 0

        val lastSyncTime = timeDatastore.getSyncTime()
        val isTimeSurpassed = TimeUtil.isTimeWithinInterval(
            Constants.TimeInterval,
            lastSyncTime,
            System.currentTimeMillis()
        )

        return if (isCategoryCacheAvailable && !isTimeSurpassed) {
            val cacheResponse = moviesDao.getMovies("upcoming")
            cacheResponse.map { it.map { it.toDomain() } }
        } else {
            moviesDao.deleteMovies(category = "upcoming")

            val networkResponse =
                safeApiRequest { apiService.fetchUpcomingMovies() }.movies?.map {
                    it.toEntity(category = "upcoming")
                }

            upcomingMoviesLivedata.value = networkResponse!!

            val cacheResponse = moviesDao.getMovies(category = "upcoming")

            timeDatastore.saveSyncTime(System.currentTimeMillis())

            cacheResponse.map { it.map { it.toDomain() } }
        }
    }


}