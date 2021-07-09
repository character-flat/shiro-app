package com.lagradost.shiro.utils

import DataStore.getKey
import DataStore.getKeys
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class DownloadFileWorkManager(val context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val keys = context.getKeys(VideoDownloadManager.KEY_RESUME_PACKAGES)
        val resumePkg = keys.mapNotNull { k -> context.getKey<VideoDownloadManager.DownloadResumePackage>(k) }

        for (pkg in resumePkg) { // ADD ALL CURRENT DOWNLOADS
            VideoDownloadManager.downloadFromResume(context, pkg)
        }

        // ADD QUEUE
        val resumeQueue =
            context.getKey<List<VideoDownloadManager.DownloadQueueResumePackage>>(VideoDownloadManager.KEY_RESUME_QUEUE_PACKAGES)
                ?.toList()
        if (resumeQueue != null && resumeQueue.isNotEmpty()) {
            val sorted = resumeQueue.sortedBy { item -> item.index }
            for (queueItem in sorted) {
                VideoDownloadManager.downloadFromResume(context, queueItem.pkg)
            }
        }

        return Result.success()
    }
}