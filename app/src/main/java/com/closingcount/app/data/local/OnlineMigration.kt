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

val Migration8To9 = object : Migration(8,9) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `online_recovery_claims` (
            `scope` TEXT NOT NULL, `originalOperationId` TEXT NOT NULL, `fingerprint` TEXT NOT NULL,
            `closingId` TEXT NOT NULL, `recoveredOperationId` TEXT, `createdAt` TEXT NOT NULL,
            PRIMARY KEY(`scope`,`originalOperationId`))""")
    }
}

val Migration9To10 = object : Migration(9,10) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `online_recovered_admin` (
            `scope` TEXT NOT NULL, `originalOperationId` TEXT NOT NULL, `fingerprint` TEXT NOT NULL,
            `group` TEXT NOT NULL, `kind` TEXT NOT NULL, `operationId` TEXT NOT NULL, `expectedRevision` INTEGER,
            `payloadJson` TEXT NOT NULL, `status` TEXT NOT NULL, `wasSent` INTEGER NOT NULL, `error` TEXT,
            `active` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`scope`,`originalOperationId`))""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_online_recovered_admin_operationId` ON `online_recovered_admin` (`operationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_online_recovered_admin_scope_group_active` ON `online_recovered_admin` (`scope`,`group`,`active`)")
    }
}
