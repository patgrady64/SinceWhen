package com.patgrady64.sincewhen

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads and writes the device Calendar Provider.
 *
 * Google calendars are stored in this provider and are synced by Android's
 * Google Calendar sync adapter. SinceWhen does not need the Google Calendar
 * REST API or an OAuth client for this device-local sync feature.
 */
class GoogleCalendarSyncManager(private val context: Context) {

    data class GoogleCalendar(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val isPrimary: Boolean
    ) {
        val displayLabel: String
            get() = if (displayName.equals(accountName, ignoreCase = true)) {
                accountName
            } else {
                "$displayName ($accountName)"
            }
    }

    data class ExistingEvent(
        val id: Long,
        val description: String?
    )

    data class SyncCandidate(
        val moment: Moment,
        val existingEvent: ExistingEvent?
    )

    data class SyncResult(
        val added: Int,
        val replaced: Int,
        val kept: Int,
        val failed: Int
    )

    /** Returns writable, syncing Google calendars on the device. */
    fun getWritableGoogleCalendars(): List<GoogleCalendar> {
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.IS_PRIMARY
        )

        val selection = buildString {
            append("${Calendars.ACCOUNT_TYPE} = ?")
            append(" AND ${Calendars.CALENDAR_ACCESS_LEVEL} >= ?")
            append(" AND ${Calendars.SYNC_EVENTS} = 1")
        }

        val selectionArgs = arrayOf(
            GOOGLE_ACCOUNT_TYPE,
            Calendars.CAL_ACCESS_CONTRIBUTOR.toString()
        )

        val calendars = mutableListOf<GoogleCalendar>()

        context.contentResolver.query(
            Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Calendars.IS_PRIMARY} DESC, ${Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Calendars._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
            val accountIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME)
            val primaryIndex = cursor.getColumnIndexOrThrow(Calendars.IS_PRIMARY)

            while (cursor.moveToNext()) {
                calendars += GoogleCalendar(
                    id = cursor.getLong(idIndex),
                    displayName = cursor.getString(nameIndex).orEmpty(),
                    accountName = cursor.getString(accountIndex).orEmpty(),
                    isPrimary = cursor.getInt(primaryIndex) == 1
                )
            }
        }

        return calendars
    }

    /** Finds existing calendar events before any writes are made. */
    fun scan(calendarId: Long, moments: List<Moment>): List<SyncCandidate> {
        return moments.map { moment ->
            SyncCandidate(moment, findExistingEvent(calendarId, moment))
        }
    }

    /**
     * Adds new events and optionally updates matching events in place.
     * Updating in place preserves reminders, location, guests, and other fields
     * that SinceWhen does not own.
     */
    fun applySync(
        calendarId: Long,
        candidates: List<SyncCandidate>,
        replaceExisting: Boolean
    ): SyncResult {
        var added = 0
        var replaced = 0
        var kept = 0
        var failed = 0

        for (candidate in candidates) {
            try {
                val existing = candidate.existingEvent

                if (existing == null) {
                    val eventId = insertEvent(calendarId, candidate.moment)
                    if (eventId != null) {
                        candidate.moment.calendarEventId = eventId
                        candidate.moment.calendarId = calendarId
                        added++
                    } else {
                        failed++
                    }
                } else if (replaceExisting) {
                    val changed = updateEvent(existing, candidate.moment)
                    if (changed) {
                        candidate.moment.calendarEventId = existing.id
                        candidate.moment.calendarId = calendarId
                        replaced++
                    } else {
                        failed++
                    }
                } else {
                    candidate.moment.calendarEventId = existing.id
                    candidate.moment.calendarId = calendarId
                    kept++
                }
            } catch (_: Exception) {
                failed++
            }
        }

        return SyncResult(added, replaced, kept, failed)
    }

    private fun findExistingEvent(calendarId: Long, moment: Moment): ExistingEvent? {
        // Fastest and most exact match: an event ID stored in the Moment itself.
        val storedEventId = moment.calendarEventId
        if (storedEventId != null && moment.calendarId == calendarId) {
            findEventById(calendarId, storedEventId)?.let { return it }
        }

        // Stable SinceWhen marker. This continues to work if the title or date
        // was changed in Google Calendar after the original export.
        findEventByMarker(calendarId, moment.id)?.let { return it }

        // Fallback for an event that was manually created or exported by an
        // older build: same calendar, same all-day date, and same title.
        return findEventByTitleAndDate(calendarId, moment)
    }

    private fun findEventById(calendarId: Long, eventId: Long): ExistingEvent? {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val projection = arrayOf(Events._ID, Events.DESCRIPTION)
        val selection = "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} != 1"
        val args = arrayOf(calendarId.toString())

        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ExistingEvent(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(Events._ID)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(Events.DESCRIPTION))
                )
            }
        }
        return null
    }

    private fun findEventByMarker(calendarId: Long, momentId: String): ExistingEvent? {
        val projection = arrayOf(Events._ID, Events.DESCRIPTION)
        val markerUri = markerUri(momentId)
        val markerText = markerText(momentId)

        val selection = buildString {
            append("${Events.CALENDAR_ID} = ?")
            append(" AND ${Events.DELETED} != 1")
            append(" AND ((")
            append("${Events.CUSTOM_APP_PACKAGE} = ? AND ${Events.CUSTOM_APP_URI} = ?")
            append(") OR ${Events.DESCRIPTION} LIKE ?)")
        }

        val args = arrayOf(
            calendarId.toString(),
            context.packageName,
            markerUri,
            "%$markerText%"
        )

        context.contentResolver.query(
            Events.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ExistingEvent(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(Events._ID)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(Events.DESCRIPTION))
                )
            }
        }
        return null
    }

    private fun findEventByTitleAndDate(calendarId: Long, moment: Moment): ExistingEvent? {
        val projection = arrayOf(Events._ID, Events.DESCRIPTION)
        val startUtc = allDayStartUtc(moment.timestamp)

        val selection = buildString {
            append("${Events.CALENDAR_ID} = ?")
            append(" AND ${Events.DELETED} != 1")
            append(" AND ${Events.TITLE} = ?")
            append(" AND ${Events.DTSTART} = ?")
            append(" AND ${Events.ALL_DAY} = 1")
        }

        val args = arrayOf(
            calendarId.toString(),
            moment.title,
            startUtc.toString()
        )

        context.contentResolver.query(
            Events.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ExistingEvent(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(Events._ID)),
                    description = cursor.getString(cursor.getColumnIndexOrThrow(Events.DESCRIPTION))
                )
            }
        }
        return null
    }

    private fun insertEvent(calendarId: Long, moment: Moment): Long? {
        val values = eventValues(moment, existingDescription = null).apply {
            put(Events.CALENDAR_ID, calendarId)
        }

        val uri = context.contentResolver.insert(Events.CONTENT_URI, values) ?: return null
        return ContentUris.parseId(uri)
    }

    private fun updateEvent(existing: ExistingEvent, moment: Moment): Boolean {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, existing.id)
        val values = eventValues(moment, existing.description)
        return context.contentResolver.update(uri, values, null, null) > 0
    }

    private fun eventValues(moment: Moment, existingDescription: String?): ContentValues {
        val startUtc = allDayStartUtc(moment.timestamp)
        val endUtc = startUtc + ONE_DAY_MILLIS

        return ContentValues().apply {
            put(Events.TITLE, moment.title)
            put(Events.DESCRIPTION, descriptionWithMarker(existingDescription, moment.id))
            put(Events.DTSTART, startUtc)
            put(Events.DTEND, endUtc)
            put(Events.ALL_DAY, 1)
            put(Events.EVENT_TIMEZONE, "UTC")
            put(Events.EVENT_END_TIMEZONE, "UTC")
            put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
            put(Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(Events.CUSTOM_APP_URI, markerUri(moment.id))
        }
    }

    private fun allDayStartUtc(localMidnightTimestamp: Long): Long {
        val localDate = Instant.ofEpochMilli(localMidnightTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        return localDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }

    private fun descriptionWithMarker(existingDescription: String?, momentId: String): String {
        val marker = markerText(momentId)
        val withoutOldMarker = existingDescription
            ?.replace(SINCEWHEN_MARKER_REGEX, "")
            ?.trim()
            .orEmpty()

        return if (withoutOldMarker.isBlank()) {
            "Created by SinceWhen.\n\n$marker"
        } else {
            "$withoutOldMarker\n\n$marker"
        }
    }

    private fun markerText(momentId: String) = "[SinceWhen ID: $momentId]"

    private fun markerUri(momentId: String) = "sincewhen://moment/$momentId"

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
        private val SINCEWHEN_MARKER_REGEX = Regex("\\n*\\[SinceWhen ID: [^]]+]", RegexOption.IGNORE_CASE)
    }
}
