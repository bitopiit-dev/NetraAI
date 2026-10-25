package com.factory.netra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DeviceEntity::class, CameraEntity::class, AlertEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NetraDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun cameraDao(): CameraDao
    abstract fun alertDao(): AlertDao

    companion object {
        fun build(context: Context): NetraDatabase =
            Room.databaseBuilder(context, NetraDatabase::class.java, "netra.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
