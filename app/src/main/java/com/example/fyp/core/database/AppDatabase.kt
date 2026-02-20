package com.example.fyp.core.database

import android.content.Context
import androidx.room.*
import com.example.fyp.core.database.dao.*
import com.example.fyp.core.database.entities.*

@Database(
    entities = [
        UserEntity::class,
        TopicEntity::class,
        InterestEntity::class,
        VideoClipEntity::class,
        FriendEntity::class,
        SubscribedVideoEntity::class,
        PublishedVideoEntity::class,
        TransferLogEntity::class,
        PeerEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // Abstract methods to access DAOs
    abstract fun userDao(): UserDao
    abstract fun topicDao(): TopicDao
    abstract fun interestDao(): InterestDao
    abstract fun videoClipDao(): VideoClipDao
    abstract fun friendDao(): FriendDao
    abstract fun subscribedVideoDao(): SubscribedVideoDao
    abstract fun publishedVideoDao(): PublishedVideoDao
    abstract fun transferLogDao(): TransferLogDao
    abstract fun peerDao(): PeerDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fyp_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}