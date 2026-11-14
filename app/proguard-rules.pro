# WorkManager restores scheduled workers by their class names after app restarts.
# Keep the concrete Worker classes and the constructor used by WorkManager reflection.
-keep class ru.tusur.schedulewidget.RefreshWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class ru.tusur.schedulewidget.WidgetTickWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class ru.tusur.schedulewidget.WidgetDateResetWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
