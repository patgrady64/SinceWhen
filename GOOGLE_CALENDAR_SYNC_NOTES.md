# SinceWhen Google Calendar Sync

This version adds **Settings > Add to Google Calendar**.

## What it does

1. Requests Android Calendar read/write permission.
2. Finds writable Google calendars already configured on the phone.
3. Lets the user choose a calendar when more than one is available.
4. Includes moments dated today or later.
5. Shows the future moments in a checkbox list. Every item starts unchecked, so the user must explicitly choose what to add.
6. Labels each item as either **Not currently in calendar** or **Already in calendar**.
7. Checks each selected moment for an existing calendar event using:
   - the previously stored Calendar Provider event ID;
   - a stable SinceWhen UUID marker; or
   - the same title and all-day date as a fallback.
8. Adds only the checked events.
9. When selected duplicates exist, asks once whether to replace them or keep them unchanged.
10. Creates all-day events marked as free, so they do not block the entire day as busy.

## Files changed

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/patgrady64/sincewhen/Moment.kt`
- `app/src/main/java/com/patgrady64/sincewhen/SettingsActivity.kt`
- `app/src/main/res/layout/activity_settings.xml`

## File added

- `app/src/main/java/com/patgrady64/sincewhen/GoogleCalendarSyncManager.kt`

## First-device test

1. Open the project in Android Studio and let Gradle sync.
2. Install the debug build on a physical Android phone containing a synced Google account.
3. Create two future SinceWhen moments.
4. Open Settings and tap **Add to Google Calendar**.
5. Grant both Calendar permissions.
6. Select the desired Google calendar.
7. Confirm that a checkbox list appears and that all moments begin unchecked.
8. Select only one moment and press **Continue**.
9. Confirm only that selected event appears as an all-day event in Google Calendar.
10. Open the sync screen again, select the same moment, and choose **Replace existing** when prompted.
11. Confirm the existing calendar event was updated rather than duplicated.

## Notes

- This uses Android's built-in Calendar Provider. It does not need a Google Cloud project, OAuth client ID, API key, or an additional Gradle dependency.
- The Google account must already exist on the phone and Calendar sync must be enabled.
- Existing event reminders, location, attendees, and other fields are not explicitly cleared when an event is updated.

## Polished selection interface

The event picker now uses a Material bottom sheet instead of the default multi-choice dialog. It includes:

- one card per future moment;
- full readable dates;
- **Ready to add** and **Already in calendar** status badges;
- a live selected-event count;
- Select all / Clear all controls;
- a disabled Add button until at least one event is selected;
- colors that follow the user's selected SinceWhen theme.

## Calendar UI polish update

- The Google-calendar chooser now uses the same themed Material bottom-sheet design as the moment-selection screen.
- Calendars are shown as rounded cards with separate calendar-name and Google-account labels.
- The primary calendar receives a visible Primary badge.
- The Cancel and Add Selected actions now use explicit 16sp bold labels on 56dp-tall buttons.
- The chooser height adapts to the number of available calendars while remaining scrollable.
