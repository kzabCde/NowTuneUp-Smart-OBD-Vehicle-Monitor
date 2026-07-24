package com.nowtuneup.app.di
import android.content.Context
import androidx.room.Room
import com.nowtuneup.app.BuildConfig
import com.nowtuneup.app.data.local.dao.NtuDao
import com.nowtuneup.app.data.local.database.NtuDatabase
import com.nowtuneup.app.data.transport.ObdTransport
import com.nowtuneup.app.data.transport.mock.MockObdTransport
import com.nowtuneup.app.data.transport.usb.UsbObdTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
 @Provides @Singleton fun database(@ApplicationContext c:Context)=Room.databaseBuilder(c,NtuDatabase::class.java,"ntu.db").build()
 @Provides fun dao(db:NtuDatabase):NtuDao=db.dao()
 @Provides @Singleton fun transport(mock:MockObdTransport,usb:UsbObdTransport):ObdTransport=if(BuildConfig.MOCK_OBD_DEFAULT)mock else usb
}
