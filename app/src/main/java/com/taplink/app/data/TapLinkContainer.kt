package com.taplink.app.data

import android.content.Context
import androidx.room.Room
import com.taplink.app.domain.TapLinkRepository

object TapLinkContainer {
    @Volatile private var instance: TapLinkRepository? = null

    fun repository(context: Context): TapLinkRepository = instance ?: synchronized(this) {
        instance ?: RoomTapLinkRepository(
            Room.databaseBuilder(context.applicationContext, TapLinkDatabase::class.java, "taplink.db")
                .build()
        ).also { instance = it }
    }
}
