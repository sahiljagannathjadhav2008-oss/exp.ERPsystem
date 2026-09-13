package com.builtdifferent.erp.data.local.migration

import androidx.room.migration.Migration

/**
 * Central registry of every Room migration this app has ever shipped.
 * Section 68 of the spec is explicit: never destroy-and-recreate the
 * database on a schema change. Concretely that means:
 *
 *  - DatabaseBuilder is configured (see ErpApplication) WITHOUT
 *    fallbackToDestructiveMigration().
 *  - Every future schema change ships a Migration(oldVersion, newVersion)
 *    added to [ALL] below, with a corresponding migration test in
 *    androidTest asserting the upgraded schema and that existing rows
 *    survive.
 *  - AppDatabase.version is bumped in the same commit as the Migration is
 *    added.
 *
 * There are no migrations yet because the schema is still at version 1.
 */
object AppDatabaseMigrations {
    val ALL: Array<Migration> = arrayOf()
}
