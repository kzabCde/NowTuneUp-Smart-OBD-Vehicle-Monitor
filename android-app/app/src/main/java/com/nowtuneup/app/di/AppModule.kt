package com.nowtuneup.app.di

import android.content.Context
import androidx.room.Room
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.database.NtuDatabase
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.data.transport.ObdTransportManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NtuDatabase =
        Room.databaseBuilder(context, NtuDatabase::class.java, "ntu.db").build()

    @Provides
    fun dao(database: NtuDatabase): NtuDao = database.dao()

    @Provides
    @Singleton
    fun transport(manager: ObdTransportManager): ObdTransport = manager
}
