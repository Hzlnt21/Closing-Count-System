package com.closingcount.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration6To7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `online_closings` (
            `scope` TEXT NOT NULL, `closingId` TEXT NOT NULL, `generation` INTEGER NOT NULL,
            `revision` INTEGER NOT NULL, `state` TEXT NOT NULL, `snapshotJson` TEXT NOT NULL,
            PRIMARY KEY(`scope`, `closingId`))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `online_entries` (
            `scope` TEXT NOT NULL, `closingId` TEXT NOT NULL, `menuId` TEXT NOT NULL,
            `generation` INTEGER NOT NULL, `serverQuantity` INTEGER NOT NULL,
            `serverRevision` INTEGER NOT NULL, `localQuantity` INTEGER NOT NULL, `status` TEXT NOT NULL,
            PRIMARY KEY(`scope`, `closingId`, `menuId`))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `online_operations` (
            `sequence` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operationId` TEXT NOT NULL,
            `scope` TEXT NOT NULL, `closingId` TEXT NOT NULL, `menuId` TEXT NOT NULL,
            `generation` INTEGER NOT NULL, `expectedRevision` INTEGER, `quantity` INTEGER NOT NULL,
            `status` TEXT NOT NULL, `errorCode` TEXT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_online_operations_operationId` ON `online_operations` (`operationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_online_operations_scope_closingId` ON `online_operations` (`scope`, `closingId`)")
    }
}

val Migration7To8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `online_lifecycle_operations` (
            `scope` TEXT NOT NULL, `closingId` TEXT NOT NULL, `operationId` TEXT NOT NULL,
            `action` TEXT NOT NULL, `generation` INTEGER NOT NULL, `expectedRevision` INTEGER NOT NULL,
            `reason` TEXT NOT NULL, `status` TEXT NOT NULL, `wasSent` INTEGER NOT NULL, `errorCode` TEXT,
            PRIMARY KEY(`scope`, `closingId`))""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_online_lifecycle_operations_operationId` ON `online_lifecycle_operations` (`operationId`)")
    }
}
