package com.centinel.app.data.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.centinel.app.data.repository.ApiResult
import com.centinel.app.data.repository.CentinelRepository
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream

sealed class ReportDownloadResult {
    data class Success(val openIntent: Intent) : ReportDownloadResult()
    data class Error(val message: String) : ReportDownloadResult()
}

object ReportDownloader {

    /**
     * Downloads GET /report/{scanId} and saves it under this app's
     * external-files directory (no storage permission needed on any API
     * level, since it's app-scoped), then builds a chooser Intent to view
     * or share it via FileProvider.
     *
     * This is intentionally NOT saved to the public Downloads collection —
     * that would need MediaStore (API 29+) or WRITE_EXTERNAL_STORAGE
     * (below it), which is meaningfully more code for a report the user
     * can already view/share/save-elsewhere immediately from the chooser
     * this returns.
     */
    suspend fun download(context: Context, repo: CentinelRepository, scanId: String): ReportDownloadResult {
        return when (val result = repo.getReportPdf(scanId)) {
            is ApiResult.Error -> ReportDownloadResult.Error(result.message)
            is ApiResult.Success -> {
                try {
                    val file = writeToFile(context, scanId, result.data)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ReportDownloadResult.Success(Intent.createChooser(intent, "Open Centinel report"))
                } catch (e: Exception) {
                    ReportDownloadResult.Error("Couldn't save the report: ${e.message}")
                }
            }
        }
    }

    private fun writeToFile(context: Context, scanId: String, body: ResponseBody): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "centinel_report_${scanId.take(8)}.pdf")
        body.byteStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }
}
