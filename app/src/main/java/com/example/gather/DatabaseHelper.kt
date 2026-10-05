package com.example.gather

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "hangouts.db", null, 3) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE hangouts (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                city TEXT NOT NULL,
                when_text TEXT NOT NULL,
                notes TEXT NOT NULL
            )
            """.trimIndent()
        )

        createAdditionalTables(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 3) {
            createAdditionalTables(db)
        }
    }

    private fun createAdditionalTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stops (
                id INTEGER PRIMARY KEY,
                hangout_id INTEGER NOT NULL,
                place_id TEXT NOT NULL,
                name TEXT NOT NULL,
                address TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                position INTEGER NOT NULL,
                planned_time TEXT NOT NULL DEFAULT '',
                FOREIGN KEY (hangout_id)
                    REFERENCES hangouts(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS stops_hangout_position
            ON stops(hangout_id, position)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS save_batches (
                token TEXT PRIMARY KEY,
                hangout_id INTEGER NOT NULL,
                FOREIGN KEY (hangout_id)
                    REFERENCES hangouts(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun valuesFor(hangout: Hangout): ContentValues {
        return ContentValues().apply {
            put("title", hangout.title)
            put("city", hangout.city)
            put("when_text", hangout.whenText)
            put("notes", hangout.notes)
        }
    }

    private fun <T> transaction(
        action: (SQLiteDatabase) -> T
    ): T {
        val db = writableDatabase
        db.beginTransaction()

        try {
            val result = action(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    fun saveHangoutWithPlaces(
        hangout: Hangout,
        places: List<ApiPlace>,
        saveToken: String
    ): Long {
        return transaction { db ->
            val previousId = db.rawQuery(
                "SELECT hangout_id FROM save_batches WHERE token = ?",
                arrayOf(saveToken)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }

            if (previousId != null) {
                return@transaction previousId
            }

            val hangoutId = if (hangout.id == 0L) {
                db.insertOrThrow("hangouts", null, valuesFor(hangout))
            } else {
                check(
                    db.update(
                        "hangouts",
                        valuesFor(hangout),
                        "id = ?",
                        arrayOf(hangout.id.toString())
                    ) == 1
                ) {
                    "The hangout no longer exists"
                }

                hangout.id
            }

            places.forEach { place ->
                insertStopRow(db, hangoutId, place)
            }

            val batch = ContentValues().apply {
                put("token", saveToken)
                put("hangout_id", hangoutId)
            }

            db.insertOrThrow("save_batches", null, batch)

            hangoutId
        }
    }

    fun insertHangout(hangout: Hangout): Long {
        return writableDatabase.insertOrThrow(
            "hangouts",
            null,
            valuesFor(hangout)
        )
    }

    fun getAllHangouts(): List<Hangout> {
        val hangouts = mutableListOf<Hangout>()

        readableDatabase.rawQuery(
            "SELECT id, title, city, when_text, notes FROM hangouts ORDER BY id DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                hangouts.add(
                    Hangout(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        city = cursor.getString(2),
                        whenText = cursor.getString(3),
                        notes = cursor.getString(4)
                    )
                )
            }
        }

        return hangouts
    }

    fun updateHangout(hangout: Hangout): Int {
        return writableDatabase.update(
            "hangouts",
            valuesFor(hangout),
            "id = ?",
            arrayOf(hangout.id.toString())
        )
    }

    fun deleteHangout(id: Long): Int {
        return writableDatabase.delete(
            "hangouts",
            "id = ?",
            arrayOf(id.toString())
        )
    }

    private fun insertStopRow(
        db: SQLiteDatabase,
        hangoutId: Long,
        place: ApiPlace
    ): Long {
        val position = db.rawQuery(
            """
            SELECT COALESCE(MAX(position), -1) + 1
            FROM stops WHERE hangout_id = ?
            """.trimIndent(),
            arrayOf(hangoutId.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

        val values = ContentValues().apply {
            put("hangout_id", hangoutId)
            put("place_id", place.placeId)
            put("name", place.name)
            put("address", place.address)
            put("latitude", place.latitude)
            put("longitude", place.longitude)
            put("position", position)
            put("planned_time", "")
        }

        return db.insertOrThrow("stops", null, values)
    }

    fun insertStop(hangoutId: Long, place: ApiPlace): Long {
        return transaction { db ->
            insertStopRow(db, hangoutId, place)
        }
    }

    fun getStops(hangoutId: Long): List<Stop> {
        val stops = mutableListOf<Stop>()

        readableDatabase.rawQuery(
            """
            SELECT id, hangout_id, place_id, name, address,
                   latitude, longitude, position, planned_time
            FROM stops
            WHERE hangout_id = ?
            ORDER BY position ASC, id ASC
            """.trimIndent(),
            arrayOf(hangoutId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                stops.add(
                    Stop(
                        id = cursor.getLong(0),
                        hangoutId = cursor.getLong(1),
                        placeId = cursor.getString(2),
                        name = cursor.getString(3),
                        address = cursor.getString(4),
                        latitude = cursor.getDouble(5),
                        longitude = cursor.getDouble(6),
                        position = cursor.getInt(7),
                        plannedTime = cursor.getString(8)
                    )
                )
            }
        }

        return stops
    }

    fun updateStopTime(
        hangoutId: Long,
        stopId: Long,
        time: String
    ): Int {
        val values = ContentValues().apply {
            put("planned_time", time)
        }

        return writableDatabase.update(
            "stops",
            values,
            "id = ? AND hangout_id = ?",
            arrayOf(stopId.toString(), hangoutId.toString())
        )
    }

    fun moveStop(
        hangoutId: Long,
        stopId: Long,
        direction: Int
    ) {
        require(direction == -1 || direction == 1)

        transaction { db ->
            val stops = getStops(hangoutId)
            val currentIndex = stops.indexOfFirst { it.id == stopId }

            check(currentIndex >= 0) {
                "The stop no longer exists"
            }

            val otherIndex = currentIndex + direction

            if (otherIndex in stops.indices) {
                val current = stops[currentIndex]
                val other = stops[otherIndex]

                val currentValues = ContentValues().apply {
                    put("position", other.position)
                }

                val otherValues = ContentValues().apply {
                    put("position", current.position)
                }

                check(
                    db.update(
                        "stops",
                        currentValues,
                        "id = ? AND hangout_id = ?",
                        arrayOf(current.id.toString(), hangoutId.toString())
                    ) == 1
                )

                check(
                    db.update(
                        "stops",
                        otherValues,
                        "id = ? AND hangout_id = ?",
                        arrayOf(other.id.toString(), hangoutId.toString())
                    ) == 1
                )
            }
        }
    }

    fun deleteStop(hangoutId: Long, stopId: Long): Int {
        return writableDatabase.delete(
            "stops",
            "id = ? AND hangout_id = ?",
            arrayOf(stopId.toString(), hangoutId.toString())
        )
    }
}