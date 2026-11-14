package ru.tusur.schedulewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        enqueueRefresh(context, false)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val workManager = WorkManager.getInstance(context.applicationContext)
        appWidgetIds.forEach { widgetId ->
            workManager.cancelUniqueWork(dateResetWorkName(widgetId))
            ScheduleStore.resetSelectedDate(context, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        update(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        when (intent.action) {
            ACTION_REFRESH -> enqueueRefresh(context, true)
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                updateAll(context)
                enqueueRefresh(context, false)
            }
            ACTION_DAY_PREVIOUS, ACTION_DAY_NEXT -> if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Store the date and timeout token together before replacing the delayed work.
                val resetAtMillis = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(DATE_RESET_DELAY_HOURS)
                ScheduleStore.moveSelectedDate(
                    context,
                    widgetId,
                    if (intent.action == ACTION_DAY_NEXT) 1 else -1,
                    resetAtMillis
                )
                // Restart the one-hour timeout after every manual day change.
                scheduleDateReset(context, widgetId, resetAtMillis)
                update(context, manager, widgetId)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "ru.tusur.schedulewidget.REFRESH"
        const val ACTION_DAY_PREVIOUS = "ru.tusur.schedulewidget.DAY_PREVIOUS"
        const val ACTION_DAY_NEXT = "ru.tusur.schedulewidget.DAY_NEXT"

        private const val TAG = "TusurWidget"
        private const val MAX_LEGACY_ROWS = 6
        private const val COMPACT_WIDTH_DP = 190
        // Subject titles always remain complete; overflow is softened with a color-matched fade.
        private const val DATE_RESET_DELAY_HOURS = 1L
        private const val WIDE_WIDTH_DP = 260
        private const val WIDGET_ROW_HORIZONTAL_PADDING_DP = 9
        private const val WIDGET_ROW_COMPACT_HORIZONTAL_PADDING_DP = 6
        private const val WIDGET_ROW_VERTICAL_PADDING_DP = 8
        private const val WIDGET_TIME_SIZE_SP = 13f
        private const val WIDGET_TITLE_SIZE_SP = 13f
        private const val WIDGET_ROOM_SIZE_SP = 11f

        private val zone = ZoneId.of("Asia/Tomsk")
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

        private val rowIds = listOf(
            RowIds(R.id.widget_row_1, R.id.widget_row_content_1, R.id.widget_row_bg_1, R.id.widget_row_border_1, R.id.widget_row_time_start_1, R.id.widget_row_time_end_1, R.id.widget_row_title_1, R.id.widget_row_title_fade_1, R.id.widget_row_room_1),
            RowIds(R.id.widget_row_2, R.id.widget_row_content_2, R.id.widget_row_bg_2, R.id.widget_row_border_2, R.id.widget_row_time_start_2, R.id.widget_row_time_end_2, R.id.widget_row_title_2, R.id.widget_row_title_fade_2, R.id.widget_row_room_2),
            RowIds(R.id.widget_row_3, R.id.widget_row_content_3, R.id.widget_row_bg_3, R.id.widget_row_border_3, R.id.widget_row_time_start_3, R.id.widget_row_time_end_3, R.id.widget_row_title_3, R.id.widget_row_title_fade_3, R.id.widget_row_room_3),
            RowIds(R.id.widget_row_4, R.id.widget_row_content_4, R.id.widget_row_bg_4, R.id.widget_row_border_4, R.id.widget_row_time_start_4, R.id.widget_row_time_end_4, R.id.widget_row_title_4, R.id.widget_row_title_fade_4, R.id.widget_row_room_4),
            RowIds(R.id.widget_row_5, R.id.widget_row_content_5, R.id.widget_row_bg_5, R.id.widget_row_border_5, R.id.widget_row_time_start_5, R.id.widget_row_time_end_5, R.id.widget_row_title_5, R.id.widget_row_title_fade_5, R.id.widget_row_room_5),
            RowIds(R.id.widget_row_6, R.id.widget_row_content_6, R.id.widget_row_bg_6, R.id.widget_row_border_6, R.id.widget_row_time_start_6, R.id.widget_row_time_end_6, R.id.widget_row_title_6, R.id.widget_row_title_fade_6, R.id.widget_row_room_6)
        )

        private data class RowIds(
            val root: Int,
            val content: Int,
            val background: Int,
            val border: Int,
            val timeStart: Int,
            val timeEnd: Int,
            val title: Int,
            val titleFade: Int,
            val room: Int
        )

        fun enqueueRefresh(context: Context, now: Boolean) {
            val connected = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(connected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.enqueueUniqueWork(
                "tusur-refresh",
                if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
            val periodic = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(connected)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "tusur-periodic",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
            )
            val tick = PeriodicWorkRequestBuilder<WidgetTickWorker>(15, TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork(
                "tusur-widget-tick",
                ExistingPeriodicWorkPolicy.KEEP,
                tick
            )
        }

        private fun dateResetWorkName(widgetId: Int): String = "widget-date-reset-$widgetId"

        private fun scheduleDateReset(context: Context, widgetId: Int, resetAtMillis: Long) {
            val request = OneTimeWorkRequestBuilder<WidgetDateResetWorker>()
                .setInitialDelay(DATE_RESET_DELAY_HOURS, TimeUnit.HOURS)
                .setInputData(
                    androidx.work.workDataOf(
                        AppWidgetManager.EXTRA_APPWIDGET_ID to widgetId,
                        WidgetDateResetWorker.KEY_RESET_AT_MILLIS to resetAtMillis
                    )
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                dateResetWorkName(widgetId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, ScheduleWidgetProvider::class.java)
            )
            ids.forEach { update(appContext, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val appContext = context.applicationContext

            try {
                val group = ScheduleStore.selectedGroup(appContext)
                val day = ScheduleStore.selectedDate(appContext, widgetId)
                val allLessons = ScheduleStore.load(appContext)
                val dayLessons = allLessons.filter {
                    Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == day
                }.sortedBy { it.start }
                val options = manager.getAppWidgetOptions(widgetId)

                // Android 12+ provides the launcher's exact supported widget sizes.
                // Build one RemoteViews layout per size instead of estimating from min/max bounds.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    @Suppress("DEPRECATION")
                    val exactSizes = options.getParcelableArrayList<SizeF>(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES
                    )
                    if (!exactSizes.isNullOrEmpty()) {
                        val layouts = LinkedHashMap<SizeF, RemoteViews>()
                        exactSizes.distinct().forEach { size ->
                            layouts[size] = createWidgetViews(
                                context = appContext,
                                widgetId = widgetId,
                                group = group,
                                day = day,
                                allLessons = allLessons,
                                dayLessons = dayLessons,
                                widgetWidthDp = size.width.toInt().coerceAtLeast(1),
                                widgetHeightDp = size.height.toInt().coerceAtLeast(1)
                            )
                        }
                        manager.updateAppWidget(widgetId, RemoteViews(layouts))
                        return
                    }
                }

                // Older launchers may not provide exact sizes. Their min/max dimensions
                // are only estimates, so use the upper bounds as a conservative fallback.
                val widgetWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    .takeIf { it > 0 }
                    ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180).coerceAtLeast(1)
                val widgetHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    .takeIf { it > 0 }
                    ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160).coerceAtLeast(1)

                manager.updateAppWidget(
                    widgetId,
                    createWidgetViews(
                        context = appContext,
                        widgetId = widgetId,
                        group = group,
                        day = day,
                        allLessons = allLessons,
                        dayLessons = dayLessons,
                        widgetWidthDp = widgetWidthDp,
                        widgetHeightDp = widgetHeightDp
                    )
                )
            } catch (error: Exception) {
                Log.e(TAG, "Failed to update widget id=$widgetId", error)
                runCatching {
                    val fallback = RemoteViews(appContext.packageName, R.layout.widget_schedule).apply {
                        setTextViewText(R.id.widget_group, ScheduleStore.selectedGroup(appContext).name)
                        setTextViewText(R.id.widget_day, RussianDateText.widgetDate(LocalDate.now(zone)))
                        setTextViewText(
                            R.id.widget_empty,
                            "Не удалось обновить виджет. Открой приложение."
                        )
                        setViewVisibility(R.id.widget_lessons_container, View.GONE)
                        setViewVisibility(R.id.widget_lessons_list, View.GONE)
                        setViewVisibility(R.id.widget_empty, View.VISIBLE)
                        setViewVisibility(R.id.widget_updated, View.INVISIBLE)
                        setViewVisibility(R.id.widget_footer_divider, View.GONE)
                        setOnClickPendingIntent(
                            R.id.widget_root,
                            activityIntent(appContext, widgetId)
                        )
                    }
                    manager.updateAppWidget(widgetId, fallback)
                }.onFailure { Log.e(TAG, "Fallback widget update failed", it) }
            }
        }

        private fun createWidgetViews(
            context: Context,
            widgetId: Int,
            group: GroupOption,
            day: LocalDate,
            allLessons: List<Lesson>,
            dayLessons: List<Lesson>,
            widgetWidthDp: Int,
            widgetHeightDp: Int
        ): RemoteViews {
            val compactWidth = widgetWidthDp < COMPACT_WIDTH_DP
            val showDate = widgetWidthDp >= COMPACT_WIDTH_DP
            val showUpdateInfo = widgetWidthDp >= WIDE_WIDTH_DP
            val background = WidgetAppearance.background(context)
            val foreground = WidgetAppearance.contrastColor(background)
            val muted = WidgetAppearance.mutedTextColor(background)

            return RemoteViews(context.packageName, R.layout.widget_schedule).apply {
                setImageViewResource(
                    R.id.widget_background,
                    WidgetAppearance.outerBackgroundDrawable(context)
                )
                setInt(R.id.widget_background, "setColorFilter", background)

                setTextViewText(
                    R.id.widget_group,
                    if (compactWidth) group.name else "Группа: ${group.name}"
                )
                setInt(
                    R.id.widget_group,
                    "setGravity",
                    if (compactWidth) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
                )
                setTextViewText(R.id.widget_day, RussianDateText.widgetDate(day))
                setViewVisibility(R.id.widget_day, if (showDate) View.VISIBLE else View.GONE)

                // In compact mode, move the selected schedule date into the footer.
                // At normal widths, keep the last-update label in its existing place.
                setTextViewText(
                    R.id.widget_updated,
                    if (compactWidth) {
                        compactWidgetDate(day)
                    } else {
                        "Последнее обновление: ${updatedLabel(context)}"
                    }
                )
                setContentDescription(
                    R.id.widget_updated,
                    if (compactWidth) {
                        RussianDateText.widgetDate(day)
                    } else {
                        "Последнее обновление расписания: ${updatedAccessibleLabel(context)}"
                    }
                )
                // Keep the footer's centre slot reserved; show the date there in compact mode.
                setViewVisibility(
                    R.id.widget_updated,
                    if (compactWidth || showUpdateInfo) View.VISIBLE else View.INVISIBLE
                )
                setViewVisibility(
                    R.id.widget_footer_divider,
                    if (showUpdateInfo) View.VISIBLE else View.GONE
                )
                setViewVisibility(R.id.widget_more, View.GONE)

                setTextColor(R.id.widget_group, foreground)
                setTextColor(R.id.widget_day, muted)
                setTextColor(R.id.widget_updated, muted)
                setTextColor(R.id.widget_previous, foreground)
                setTextColor(R.id.widget_next, foreground)
                setTextColor(R.id.widget_empty, muted)
                setInt(R.id.widget_empty, "setGravity", Gravity.CENTER)

                // The top, bottom and footer insets are fixed at every widget size.
                setViewPadding(
                    R.id.widget_content,
                    dp(context, if (compactWidth) 7 else 10),
                    dp(context, 9),
                    dp(context, if (compactWidth) 7 else 10),
                    dp(context, 3)
                )
                setViewPadding(R.id.widget_updated, 0, dp(context, 3), 0, 0)

                setOnClickPendingIntent(R.id.widget_root, activityIntent(context, widgetId))
                setOnClickPendingIntent(
                    R.id.widget_previous,
                    broadcastIntent(context, widgetId, ACTION_DAY_PREVIOUS, 1)
                )
                setOnClickPendingIntent(
                    R.id.widget_next,
                    broadcastIntent(context, widgetId, ACTION_DAY_NEXT, 2)
                )

                if (allLessons.isEmpty()) {
                    setTextViewText(
                        R.id.widget_empty,
                        ScheduleStore.error(context) ?: "Открой приложение и обнови расписание"
                    )
                } else if (compactWidth) {
                    setTextViewText(R.id.widget_empty, "В этот день нет\nзанятий 🎉")
                } else {
                    setTextViewText(R.id.widget_empty, "В этот день нет занятий 🎉")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Local static collection: the ListView scrolls without RemoteViewsService.
                    setViewVisibility(R.id.widget_lessons_container, View.GONE)
                    setViewVisibility(
                        R.id.widget_lessons_list,
                        if (dayLessons.isEmpty()) View.GONE else View.VISIBLE
                    )
                    if (dayLessons.isNotEmpty()) {
                        setPendingIntentTemplate(
                            R.id.widget_lessons_list,
                            collectionActivityIntent(context, widgetId)
                        )
                    }
                    val collection = RemoteViews.RemoteCollectionItems.Builder()
                        .setHasStableIds(true)
                        // Normal and compact cards use two distinct XML layout resources.
                        .setViewTypeCount(2)

                    dayLessons.forEachIndexed { index, lesson ->
                        collection.addItem(
                            index.toLong(),
                            createLessonItem(
                                context = context,
                                lesson = lesson,
                                compactWidth = compactWidth
                            )
                        )
                    }
                    setRemoteAdapter(R.id.widget_lessons_list, collection.build())
                } else {
                    // Android 11 and older retain the static six-row fallback.
                    setViewVisibility(R.id.widget_lessons_list, View.GONE)
                    setViewVisibility(
                        R.id.widget_lessons_container,
                        if (dayLessons.isEmpty()) View.GONE else View.VISIBLE
                    )
                    configureLegacyRows(
                        views = this,
                        context = context,
                        lessons = dayLessons,
                        compactWidth = compactWidth
                    )
                }

                setViewVisibility(
                    R.id.widget_empty,
                    if (dayLessons.isEmpty()) View.VISIBLE else View.GONE
                )
            }
        }

        private fun createLessonItem(
            context: Context,
            lesson: Lesson,
            compactWidth: Boolean
        ): RemoteViews {
            val now = System.currentTimeMillis()
            val fill = if (now >= lesson.start && now < lesson.end) {
                WidgetAppearance.current(context)
            } else {
                WidgetAppearance.lesson(context)
            }
            val details = "${lesson.note} ${lesson.teacher}"
            val borderColor = LessonTypeStyle.color(lesson.type, details)
            val dashed = LessonTypeStyle.isDashed(lesson.type, details)
            val start = timeFormatter.format(Instant.ofEpochMilli(lesson.start).atZone(zone))
            val end = timeFormatter.format(Instant.ofEpochMilli(lesson.end).atZone(zone))
            val title = displayTitle(lesson.title)
            val room = if (compactWidth) LessonText.compactRoom(lesson.room) else LessonText.room(lesson.room)

            // Use separate fixed-size XML layouts instead of RemoteViews sizing APIs
            // introduced in Android 12. This keeps the collection compatible with minSdk 23.
            val rowLayout = if (compactWidth) {
                R.layout.widget_schedule_row_compact
            } else {
                R.layout.widget_schedule_row
            }

            return RemoteViews(context.packageName, rowLayout).apply {
                // Collection rows use the ListView's PendingIntent template.
                setOnClickFillInIntent(R.id.widget_lesson_item_root, Intent())
                setViewPadding(
                    R.id.widget_lesson_item_content,
                    dp(context, if (compactWidth) WIDGET_ROW_COMPACT_HORIZONTAL_PADDING_DP else WIDGET_ROW_HORIZONTAL_PADDING_DP),
                    dp(context, WIDGET_ROW_VERTICAL_PADDING_DP),
                    dp(context, if (compactWidth) WIDGET_ROW_COMPACT_HORIZONTAL_PADDING_DP else WIDGET_ROW_HORIZONTAL_PADDING_DP),
                    dp(context, WIDGET_ROW_VERTICAL_PADDING_DP)
                )

                setImageViewResource(R.id.widget_lesson_item_bg, R.drawable.widget_lesson_shape)
                setInt(R.id.widget_lesson_item_bg, "setColorFilter", fill)
                setImageViewResource(
                    R.id.widget_lesson_item_border,
                    if (dashed) R.drawable.widget_lesson_border_dashed else R.drawable.widget_lesson_border
                )
                setInt(R.id.widget_lesson_item_border, "setColorFilter", borderColor)

                setTextViewText(R.id.widget_lesson_item_time_start, start)
                setTextViewText(R.id.widget_lesson_item_time_end, end)
                setTextViewText(R.id.widget_lesson_item_title, title)
                setImageViewBitmap(R.id.widget_lesson_item_title_fade, titleFadeBitmap(context, fill))
                setTextViewText(R.id.widget_lesson_item_room, room)
                setTextColor(R.id.widget_lesson_item_time_start, WidgetAppearance.mutedTextColor(fill))
                setTextColor(R.id.widget_lesson_item_time_end, WidgetAppearance.mutedTextColor(fill))
                setTextColor(R.id.widget_lesson_item_title, WidgetAppearance.contrastColor(fill))
                setTextColor(R.id.widget_lesson_item_room, WidgetAppearance.mutedTextColor(fill))

                // Keep the full subject name on one line. A soft bitmap fade is overlaid on
                // its right edge so the clipping remains visually quiet on narrow widgets.
                setTextViewTextSize(R.id.widget_lesson_item_time_start, TypedValue.COMPLEX_UNIT_SP, WIDGET_TIME_SIZE_SP)
                setTextViewTextSize(R.id.widget_lesson_item_time_end, TypedValue.COMPLEX_UNIT_SP, WIDGET_TIME_SIZE_SP)
                setTextViewTextSize(R.id.widget_lesson_item_title, TypedValue.COMPLEX_UNIT_SP, WIDGET_TITLE_SIZE_SP)
                setTextViewTextSize(R.id.widget_lesson_item_room, TypedValue.COMPLEX_UNIT_SP, WIDGET_ROOM_SIZE_SP)
                setInt(R.id.widget_lesson_item_title, "setMaxLines", 1)
                setContentDescription(R.id.widget_lesson_item_title, lesson.title.trim())
            }
        }

        private fun configureLegacyRows(
            views: RemoteViews,
            context: Context,
            lessons: List<Lesson>,
            compactWidth: Boolean
        ) {
            rowIds.forEachIndexed { index, ids ->
                val lesson = lessons.getOrNull(index)
                val show = lesson != null && index < MAX_LEGACY_ROWS
                views.setViewVisibility(ids.root, if (show) View.VISIBLE else View.GONE)
                if (!show || lesson == null) return@forEachIndexed

                val now = System.currentTimeMillis()
                val fill = if (now >= lesson.start && now < lesson.end) {
                    WidgetAppearance.current(context)
                } else {
                    WidgetAppearance.lesson(context)
                }
                val details = "${lesson.note} ${lesson.teacher}"
                val borderColor = LessonTypeStyle.color(lesson.type, details)
                val dashed = LessonTypeStyle.isDashed(lesson.type, details)
                val start = timeFormatter.format(Instant.ofEpochMilli(lesson.start).atZone(zone))
                val end = timeFormatter.format(Instant.ofEpochMilli(lesson.end).atZone(zone))
                val title = displayTitle(lesson.title)
                val room = if (compactWidth) LessonText.compactRoom(lesson.room) else LessonText.room(lesson.room)

                views.setViewPadding(
                    ids.content,
                    dp(context, if (compactWidth) WIDGET_ROW_COMPACT_HORIZONTAL_PADDING_DP else WIDGET_ROW_HORIZONTAL_PADDING_DP),
                    dp(context, WIDGET_ROW_VERTICAL_PADDING_DP),
                    dp(context, if (compactWidth) WIDGET_ROW_COMPACT_HORIZONTAL_PADDING_DP else WIDGET_ROW_HORIZONTAL_PADDING_DP),
                    dp(context, WIDGET_ROW_VERTICAL_PADDING_DP)
                )
                views.setImageViewResource(ids.background, R.drawable.widget_lesson_shape)
                views.setInt(ids.background, "setColorFilter", fill)
                views.setImageViewResource(
                    ids.border,
                    if (dashed) R.drawable.widget_lesson_border_dashed else R.drawable.widget_lesson_border
                )
                views.setInt(ids.border, "setColorFilter", borderColor)
                views.setTextViewText(ids.timeStart, start)
                views.setTextViewText(ids.timeEnd, end)
                views.setTextViewText(ids.title, title)
                views.setImageViewBitmap(ids.titleFade, titleFadeBitmap(context, fill))
                views.setTextViewText(ids.room, room)
                views.setViewVisibility(ids.room, View.VISIBLE)
                views.setTextColor(ids.timeStart, WidgetAppearance.mutedTextColor(fill))
                views.setTextColor(ids.timeEnd, WidgetAppearance.mutedTextColor(fill))
                views.setTextColor(ids.title, WidgetAppearance.contrastColor(fill))
                views.setTextColor(ids.room, WidgetAppearance.mutedTextColor(fill))
                views.setTextViewTextSize(ids.timeStart, TypedValue.COMPLEX_UNIT_SP, WIDGET_TIME_SIZE_SP)
                views.setTextViewTextSize(ids.timeEnd, TypedValue.COMPLEX_UNIT_SP, WIDGET_TIME_SIZE_SP)
                views.setTextViewTextSize(ids.title, TypedValue.COMPLEX_UNIT_SP, WIDGET_TITLE_SIZE_SP)
                views.setTextViewTextSize(ids.room, TypedValue.COMPLEX_UNIT_SP, WIDGET_ROOM_SIZE_SP)
                views.setInt(ids.title, "setMaxLines", 1)
                views.setContentDescription(ids.title, lesson.title.trim())
            }
        }

        /**
         * Short titles (up to three words) are never abbreviated.
         * On narrow layouts, longer names are reduced to initials; otherwise
         * the full title is retained and the TextView marquee handles overflow.
         */
        private fun compactWidgetDate(day: LocalDate): String {
            val weekday = day.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale("ru"))
                .replaceFirstChar { it.titlecase(Locale("ru")) }
                .removeSuffix(".")

            val month = when (day.monthValue) {
                1 -> "янв"
                2 -> "фев"
                3 -> "марта"
                4 -> "апр"
                5 -> "мая"
                6 -> "июня"
                7 -> "июля"
                8 -> "авг"
                9 -> "сен"
                10 -> "окт"
                11 -> "ноя"
                12 -> "дек"
                else -> return RussianDateText.widgetDate(day)
            }

            return "$weekday · ${day.dayOfMonth} $month"
        }

        private fun displayTitle(title: String): String =
            title.trim().replace(Regex("\\s+"), " ")

        private val titleFadeBitmaps = mutableMapOf<Int, Bitmap>()

        @Synchronized
        private fun titleFadeBitmap(context: Context, backgroundColor: Int): Bitmap {
            titleFadeBitmaps[backgroundColor]?.let { return it }
            val bitmapWidth = dp(context, 26).coerceAtLeast(1)
            val bitmapHeight = dp(context, 24).coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, bitmapWidth.toFloat(), 0f,
                    intArrayOf(Color.TRANSPARENT, backgroundColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            Canvas(bitmap).drawRect(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat(), paint)
            titleFadeBitmaps[backgroundColor] = bitmap
            return bitmap
        }

        private fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        private fun collectionActivityIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("timetable://widget/$widgetId/collection")
            }
            // RemoteViews collection items use fill-in intents, which require a mutable template.
            return PendingIntent.getActivity(
                context,
                widgetId * 10 + 4,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        private fun activityIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("timetable://widget/$widgetId/open")
            }
            return PendingIntent.getActivity(
                context,
                widgetId * 10 + 3,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun broadcastIntent(
            context: Context,
            widgetId: Int,
            action: String,
            suffix: Int
        ): PendingIntent {
            val intent = Intent(context, ScheduleWidgetProvider::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("timetable://widget/$widgetId/$suffix")
            }
            return PendingIntent.getBroadcast(
                context,
                widgetId * 10 + suffix,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun updatedLabel(context: Context): String {
            val updated = ScheduleStore.updatedAt(context)
            if (updated <= 0L) return "не выполнялось"
            return DateTimeFormatter.ofPattern("dd.MM · HH:mm", Locale.ROOT)
                .format(Instant.ofEpochMilli(updated).atZone(zone))
        }

        private fun updatedAccessibleLabel(context: Context): String {
            val updated = ScheduleStore.updatedAt(context)
            if (updated <= 0L) return "не выполнялось"
            return RussianDateText.accessibleDateTime(updated, zone)
        }
    }
}
