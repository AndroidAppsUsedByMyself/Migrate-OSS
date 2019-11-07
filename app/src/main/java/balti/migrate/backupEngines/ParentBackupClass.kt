package balti.migrate.backupEngines

import android.app.PendingIntent
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.AsyncTask
import android.os.Build
import androidx.core.app.NotificationCompat
import balti.migrate.AppInstance
import balti.migrate.R
import balti.migrate.backupEngines.BackupServiceKotlin.Companion.serviceContext
import balti.migrate.backupEngines.containers.BackupIntentData
import balti.migrate.backupEngines.utils.OnEngineTaskComplete
import balti.migrate.simpleActivities.ProgressShowActivity
import balti.migrate.utilities.CommonToolKotlin
import balti.migrate.utilities.CommonToolKotlin.Companion.ACTION_BACKUP_CANCEL
import balti.migrate.utilities.CommonToolKotlin.Companion.ACTION_BACKUP_PROGRESS
import balti.migrate.utilities.CommonToolKotlin.Companion.EXTRA_ACTUAL_DESTINATION
import balti.migrate.utilities.CommonToolKotlin.Companion.EXTRA_BACKUP_NAME
import balti.migrate.utilities.CommonToolKotlin.Companion.EXTRA_PROGRESS_PERCENTAGE
import balti.migrate.utilities.CommonToolKotlin.Companion.EXTRA_PROGRESS_TYPE
import balti.migrate.utilities.CommonToolKotlin.Companion.EXTRA_SUBTASK
import balti.migrate.utilities.CommonToolKotlin.Companion.EXTRA_TASKLOG
import balti.migrate.utilities.CommonToolKotlin.Companion.EXTRA_TITLE
import balti.migrate.utilities.CommonToolKotlin.Companion.NOTIFICATION_ID_ONGOING
import balti.migrate.utilities.CommonToolKotlin.Companion.PENDING_INTENT_BACKUP_CANCEL_ID
import balti.migrate.utilities.CommonToolKotlin.Companion.PENDING_INTENT_REQUEST_ID
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter

abstract class ParentBackupClass(private val bd: BackupIntentData,
                                 private val intentType: String): AsyncTask<Any, Any, Any>() {

    val engineContext by lazy { serviceContext }
    val sharedPreferences by lazy { AppInstance.sharedPrefs }

    val onEngineTaskComplete by lazy { engineContext as OnEngineTaskComplete }

    val commonTools by lazy { CommonToolKotlin(engineContext) }
    val actualDestination by lazy { formatName("${bd.destination}/${bd.backupName}") }

    private var lastProgress = 0
    private var isIndeterminate = true

    private val actualBroadcast by lazy {
        Intent(ACTION_BACKUP_PROGRESS).apply {
            putExtra(EXTRA_BACKUP_NAME, bd.backupName)
            putExtra(EXTRA_ACTUAL_DESTINATION, actualDestination)
            putExtra(EXTRA_PROGRESS_TYPE, intentType)
        }
    }

    private val onGoingNotification by lazy {
        NotificationCompat.Builder(engineContext, CommonToolKotlin.CHANNEL_BACKUP_RUNNING)
                .setContentTitle(engineContext.getString(R.string.loading))
                .setSmallIcon(R.drawable.ic_notification_icon)
                .addAction(
                        NotificationCompat.Action(0, serviceContext.getString(android.R.string.cancel),
                                PendingIntent.getBroadcast(serviceContext, PENDING_INTENT_BACKUP_CANCEL_ID,
                                        Intent(ACTION_BACKUP_CANCEL), 0))
                )
    }

    private val activityIntent by lazy { Intent(serviceContext, ProgressShowActivity::class.java) }

    fun getTitle(stringRes: Int): String{
        return engineContext.getString(stringRes)
    }

    fun getDataBase(dataBaseFile: File): SQLiteDatabase{
        var dataBase: SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(dataBaseFile.absolutePath, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
            dataBase = SQLiteDatabase.openDatabase(dataBaseFile.absolutePath, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS or SQLiteDatabase.OPEN_READWRITE)
        return dataBase
    }

    fun formatName(name: String): String {
        return name.replace(' ', '_')
                .replace('`', '\'')
                .replace('"', '\'')
    }

    private fun updateNotification(subTask: String, progressPercent: Int){

        actualBroadcast.let {

            if (it.hasExtra(EXTRA_TITLE)) {
                onGoingNotification.apply {
                    setContentTitle(it.getStringExtra(EXTRA_TITLE))
                    setContentText(subTask)
                    setProgress(100, progressPercent, progressPercent == -1)
                    setContentIntent(
                            PendingIntent.getActivity(serviceContext, PENDING_INTENT_REQUEST_ID,
                                    activityIntent.putExtras(actualBroadcast),
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                    )
                }
                AppInstance.notificationManager.notify(NOTIFICATION_ID_ONGOING, onGoingNotification.build())
            }
        }
    }

    fun broadcastProgress(subTask: String, taskLog: String, showNotification: Boolean, progressPercent: Int = -1){

        if (BackupServiceKotlin.cancelAll) return

        val progress = if (progressPercent == -1) (if (isIndeterminate) progressPercent else lastProgress) else progressPercent
        lastProgress = progress

        commonTools.LBM?.sendBroadcast(
                actualBroadcast.apply {
                    putExtra(EXTRA_SUBTASK, subTask)
                    putExtra(EXTRA_TASKLOG, taskLog)
                    putExtra(EXTRA_PROGRESS_PERCENTAGE, progress)
                }
        )

        if (showNotification)
            updateNotification(subTask, progress)
    }

    fun resetBroadcast(isIndeterminateProgress: Boolean, title: String, newIntentType: String = ""){

        this.isIndeterminate = isIndeterminateProgress
        val progress = if (!isIndeterminateProgress) 0 else -1

        actualBroadcast.apply {
            if (newIntentType != "") putExtra(EXTRA_PROGRESS_TYPE, newIntentType)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SUBTASK, "")
            putExtra(EXTRA_TASKLOG, "")
            putExtra(EXTRA_PROGRESS_PERCENTAGE, progress)
        }

        commonTools.LBM?.sendBroadcast(actualBroadcast)
        updateNotification("", progress)

    }

    fun cancelTask(suProcess: Process?, vararg pids: Int) {

            commonTools.tryIt {
                val killProcess = Runtime.getRuntime().exec("su")

                val writer = BufferedWriter(OutputStreamWriter(killProcess.outputStream))
                fun killId(pid: Int) {
                    writer.write("kill -9 $pid\n")
                    writer.write("kill -15 $pid\n")
                }

                for (pid in pids)
                    if (pid != -999) killId(pid)

                writer.write("exit\n")
                writer.flush()

                commonTools.tryIt { killProcess.waitFor() }
                commonTools.tryIt { suProcess?.waitFor() }
            }

    }

    var customPreExecuteFunction: (() -> Unit)? = null

    abstract fun postExecuteFunction()

    override fun onPreExecute() {
        super.onPreExecute()
        File(actualDestination).mkdirs()
        customPreExecuteFunction?.invoke()
    }

    override fun onPostExecute(result: Any?) {
        super.onPostExecute(result)
        postExecuteFunction()
    }
}