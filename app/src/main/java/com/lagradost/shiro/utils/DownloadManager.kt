package com.lagradost.shiro.utils

import android.annotation.SuppressLint
import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.shiro.R
import com.lagradost.shiro.ui.MainActivity
import com.lagradost.shiro.ui.MainActivity.Companion.activity
import com.lagradost.shiro.ui.MainActivity.Companion.isDonor
import com.lagradost.shiro.ui.downloads.DownloadFragment.Companion.downloadsUpdated
import com.lagradost.shiro.utils.AppUtils.getColorFromAttr
import com.lagradost.shiro.utils.AppUtils.settingsManager
import com.lagradost.shiro.utils.ShiroApi.Companion.USER_AGENT
import com.lagradost.shiro.utils.ShiroApi.Companion.getFullUrlCdn
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.nio.file.Files
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.math.round

const val UPDATE_TIME = 1000
const val CHANNEL_ID = "shiro.general"
const val CHANNEL_NAME = "Downloads"
const val CHANNEL_DESCRIPTION = "The download notification channel for the shiro app"

// USED TO STOP, CANCEL AND RESUME FROM ACTION IN NOTIFICATION
class DownloadService : IntentService("DownloadService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)
            val type = intent.getStringExtra("type")
            if (id != -1 && type != null) {
                DownloadManager.invokeDownloadAction(
                    id, when (type) {
                        "resume" -> DownloadManager.DownloadStatusType.IsDownloading
                        "pause" -> DownloadManager.DownloadStatusType.IsPaused
                        "stop" -> DownloadManager.DownloadStatusType.IsStopped
                        else -> DownloadManager.DownloadStatusType.IsDownloading
                    }
                )
            }
        }
    }
}

// TODO fix this and in DataStorea
@SuppressLint("StaticFieldLeak")
object DownloadManager {
    private var localContext: Context? = null
    val downloadStatus = hashMapOf<Int, DownloadStatusType>()
    private val downloadMustUpdateStatus = hashMapOf<Int, Boolean>()

    // THIS IS GLUE TO MAKE IT INVOKE WITH ON PARAMETER
    val downloadEvent = Event<DownloadEventAndChild>()
    val downloadPauseEvent = Event<Int>()
    val downloadDeleteEvent = Event<Int>()
    val downloadStartEvent = Event<String>()

    val usingScopedStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private const val txt = "Not authorized."
    fun init(_context: Context) {
        localContext = _context
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText = CHANNEL_DESCRIPTION//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                localContext!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    data class DownloadEvent(
        @JsonProperty("id") val id: Int,
        @JsonProperty("bytes") val bytes: Long,
    )

    data class DownloadInfo(
        //val card: FastAniApi.Card?,
        @JsonProperty("episodeIndex") val episodeIndex: Int,
        @JsonProperty("animeData") val animeData: ShiroApi.AnimePageData,

        @JsonProperty("anilistID") val anilistID: Int? = null,
        @JsonProperty("malID") val malID: Int? = null,
        @JsonProperty("fillerEpisodes") val fillerEpisodes: HashMap<Int, Boolean>? = null
    )

    enum class DownloadType {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
    }

    enum class DownloadActionType {
        Pause,
        Resume,
        Stop,
    }

    enum class DownloadStatusType {
        IsPaused,
        IsDownloading,
        IsStopped,
    }

    data class DownloadParentFileMetadata(
        @JsonProperty("title") val title: String,
        @JsonProperty("coverImagePath") val coverImagePath: String,
        @JsonProperty("isMovie") val isMovie: Boolean,
        @JsonProperty("slug") val slug: String,

        @JsonProperty("anilistID") val anilistID: Int?,
        @JsonProperty("malID") val malID: Int?,
        @JsonProperty("fillerEpisodes") val fillerEpisodes: HashMap<Int, Boolean>?
    )

    // Glue for invoke()
    data class DownloadEventAndChild(
        @JsonProperty("downloadEvent") val downloadEvent: DownloadEvent,
        @JsonProperty("child") val child: DownloadFileMetadata,
    )

    data class DownloadFileMetadata(
        @JsonProperty("internalId") val internalId: Int, // UNIQUE ID BASED ON aniListId season and index
        @JsonProperty("slug") val slug: String,
        @JsonProperty("animeData") val animeData: ShiroApi.AnimePageData,

        @JsonProperty("thumbPath") val thumbPath: String?,
        @JsonProperty("videoPath") val videoPath: String,

        @JsonProperty("videoTitle") val videoTitle: String,
        @JsonProperty("episodeIndex") val episodeIndex: Int,

        @JsonProperty("downloadAt") val downloadAt: Long,
        @JsonProperty("maxFileSize") val maxFileSize: Long, // IF MUST RESUME
        @JsonProperty("downloadFileLink") val downloadFileLink: ExtractorLink, // IF RESUME, DO IT FROM THIS URL
    )

    data class DownloadFileMetadataLegacy(
        @JsonProperty("internalId") val internalId: Int, // UNIQUE ID BASED ON aniListId season and index
        @JsonProperty("slug") val slug: String,
        @JsonProperty("animeData") val animeData: ShiroApi.AnimePageData,

        @JsonProperty("thumbPath") val thumbPath: String?,
        @JsonProperty("videoPath") val videoPath: String,

        @JsonProperty("videoTitle") val videoTitle: String,
        @JsonProperty("episodeIndex") val episodeIndex: Int,

        @JsonProperty("downloadAt") val downloadAt: Long,
        @JsonProperty("maxFileSize") val maxFileSize: Long, // IF MUST RESUME
        @JsonProperty("downloadFileUrl") val downloadFileUrl: String, // IF RESUME, DO IT FROM THIS URL
    )

    fun invokeDownloadAction(id: Int, type: DownloadStatusType) {
        if (downloadStatus.containsKey(id)) {
            downloadStatus[id] = type
            downloadMustUpdateStatus[id] = true
        } else {
            downloadStatus[id] = type
        }
        if (type == DownloadStatusType.IsDownloading) {
            downloadPauseEvent.invoke(id)
        } else if (type == DownloadStatusType.IsStopped) {
            downloadDeleteEvent.invoke(id)
        }
    }

    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }

    fun convertBytesToAny(bytes: Long, digits: Int = 2, steps: Double = 3.0): Double {
        return (bytes / 1024.0.pow(steps)).round(digits)
    }

    private val cachedBitmaps = hashMapOf<String, Bitmap>()

    private fun getImageBitmapFromUrl(url: String): Bitmap? {
        if (cachedBitmaps.containsKey(url)) {
            return cachedBitmaps[url]
        }

        val bitmap = Glide.with(localContext!!)
            .asBitmap()
            .load(url).into(1080, 720) // Width and height
            .get()
        if (bitmap != null) {
            cachedBitmaps[url] = bitmap
        }
        return null
    }

    private fun censorFilename(_name: String, toLower: Boolean = false): String {
        val rex = Regex.fromLiteral("[^A-Za-z0-9\\.\\-\\: ]")
        var name = _name
        rex.replace(name, "")//Regex.Replace(name, @"[^A-Za-z0-9\.]+", String.Empty)
        name.replace(" ", "")
        if (toLower) {
            name = name.lowercase(Locale.ROOT)
        }
        return name
    }

    private fun downloadPoster(path: String, url: String) {
        thread {
            try {
                val rFile = File(path)
                if (rFile.exists()) {
                    return@thread
                }
                try {
                    rFile.parentFile.mkdirs()
                } catch (_ex: Exception) {
                    println("FAILED:::$_ex")
                }
                try {
                    rFile.createNewFile()
                } catch (e: Exception) {
                    println(e.printStackTrace())
                    e.printStackTrace()
                    activity?.runOnUiThread {
                        Toast.makeText(localContext!!, "Permission error downloading poster", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }

                val rUrl =
                    (if (url.startsWith("https://") || url.startsWith("http://")) url else getFullUrlCdn(url)).replace(
                        " ",
                        "%20"
                    )
                println("RRLL: $rUrl")
                val _url = URL(rUrl)
                val connection: URLConnection = _url.openConnection()
                for (k in ShiroApi.currentHeaders?.keys!!) {
                    connection.setRequestProperty(k, ShiroApi.currentHeaders!![k])
                }

                val input: InputStream = BufferedInputStream(connection.inputStream)
                val output: OutputStream = FileOutputStream(rFile, true)

                val buffer = ByteArray(1024)
                var count: Int

                while (true) {
                    try {
                        count = input.read(buffer)
                        if (count < 0) break

                        output.write(buffer, 0, count)
                    } catch (_ex: Exception) {
                        println("FAILEDDLOAD:::$_ex")
                    }
                }
            } catch (_ex: Exception) {
                _ex.printStackTrace()
                println("FAILEDPOSTERDLOAD:::$_ex")
            }
        }
    }

    @SuppressLint("HardwareIds")
    fun downloadEpisode(info: DownloadInfo, link: ExtractorLink, resumeIntent: Boolean = false) {
        val useExternalStorage = settingsManager!!.getBoolean("use_external_storage", false)

        // IsInResult == isDonor
        if (!isDonor) { // FINAL CHECK
            Toast.makeText(activity, txt, Toast.LENGTH_SHORT).show()
            return
        }
        val id = (info.animeData.slug + "E${info.episodeIndex}").hashCode()

        if (downloadStatus.containsKey(id)) { // PREVENT DUPLICATE DOWNLOADS
            if (downloadStatus[id] == DownloadStatusType.IsPaused) {
                downloadStatus[id] = DownloadStatusType.IsDownloading
                downloadMustUpdateStatus[id] = true
            }
            if (resumeIntent) {
                invokeDownloadAction(id, DownloadStatusType.IsDownloading)
            }
            return
        } else {
            if (resumeIntent) {
                invokeDownloadAction(id, DownloadStatusType.IsDownloading)
            }
        }

        thread {
            println("STARTING DOWNLOAD $link")
            var fullResume = false // IF FULL RESUME

            try {
                val isMovie: Boolean = info.animeData.episodes?.size ?: 0 == 1 && info.animeData.status == "finished"
                val mainTitle = info.animeData.name
                //val ep =
                //    info.animeData.episodes?.get(info.episodeIndex) //info.card.cdnData.seasons[info.seasonIndex].episodes[info.episodeIndex]
                var title = mainTitle
                if (title.replace(" ", "") == "") {
                    title = "Episode " + info.episodeIndex + 1
                }

                val basePath =
                    when {
                        useExternalStorage && usingScopedStorage -> localContext!!.filesDir
                        useExternalStorage -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        else -> activity?.filesDir
                    }
                // =================== DOWNLOAD POSTERS AND SETUP PATH ===================

                val path: String =
                    basePath.toString() +
                            "/Shiro/" +
                            censorFilename(mainTitle) +
                            if (isMovie)
                                ".mp4"
                            else
                                "/" + censorFilename("E${info.episodeIndex + 1} $title") + ".mp4"

                val posterPath = path/*.replace("/Anime/", "/Posters/")*/.replace(".mp4", ".jpg")
                //downloadPoster(posterPath, getFullUrlCdn(info.animeData.image))
                val mainPosterPath =
                    //android.os.Environment.getExternalStorageDirectory().path +
                    activity?.filesDir.toString() +
                            "/Downloads/MainPosters/" +
                            censorFilename(info.animeData.name) + ".jpg"

                downloadPoster(mainPosterPath, getFullUrlCdn(info.animeData.image))

                // =================== MAKE DIRS ===================
                val rFile = File(path)
                //if (!(usingScopedStorage && useExternalStorage)) {
                try {
                    rFile.parentFile.mkdirs()
                } catch (_ex: Exception) {
                    println("FAILED:::$_ex")
                }
                //}

                val _url = URL(link.url)

                val connection: URLConnection = _url.openConnection()

                var bytesRead = 0L
                //val androidId: String = Settings.Secure.getString(localContext?.contentResolver, Settings.Secure.ANDROID_ID)
                val referer = link.referer

                // =================== STORAGE ===================
                var fos: FileOutputStream? = null
                /*if (usingScopedStorage && useExternalStorage) {
                var scopedUri: Uri? = null
                    val resolver = localContext?.contentResolver
                    val values = ContentValues()
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    values.put(MediaStore.MediaColumns.IS_PENDING, true)
                    if (isMovie) {
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Shiro/")
                    } else {
                        values.put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_MOVIES + "/Shiro/" + censorFilename(mainTitle)
                        )
                    }
                    scopedUri = resolver?.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
                    // println("FFFFFFFFFFFFFFFFFFFFFF ${resolver.openFileDescriptor(uri, "r").use { it?.statSize ?: 0 }}")
                    if (resumeIntent) {
                        bytesRead = resolver.openFileDescriptor(scopedUri, "r").use { it?.statSize ?: 0 }
                        connection.setRequestProperty("Range", "bytes=${bytesRead.toInt()}-")
                    }
                    fos = resolver.openOutputStream(scopedUri) as FileOutputStream?
                } else {*/
                try {
                    if (!rFile.exists()) {
                        println("FILE DOESN'T EXITS")
                        rFile.createNewFile()
                    } else {
                        if (resumeIntent) {
                            bytesRead = rFile.length()
                            connection.setRequestProperty("Range", "bytes=" + rFile.length() + "-")
                        } else {
                            rFile.delete()
                            rFile.createNewFile()
                        }
                    }
                } catch (e: Exception) {
                    println(e.printStackTrace())
                    activity?.runOnUiThread {
                        Toast.makeText(localContext!!, "Permission error", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }
                //}

                // =================== CONNECTION ===================
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (referer != "") {
                    println("REFERER: $referer")
                    connection.setRequestProperty("Referer", referer)
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                }
                connection.connectTimeout = 10000
                var clen = 0
                try {
                    connection.connect()
                    clen = connection.contentLength
                    println("CONTENTNT LENGTH: $clen")
                } catch (_ex: Exception) {
                    println("CONNECT:::$_ex")
                    _ex.printStackTrace()
                }

                // =================== VALIDATE ===================
                if (clen < 5000000) { // min of 5 MB
                    clen = 0
                }
                if (clen <= 0) { // TO SMALL OR INVALID
                    showNot(0, 0, 0, DownloadType.IsFailed, info)
                    return@thread
                }

                // =================== SETUP VARIABLES ===================
                downloadStatus[id] = DownloadStatusType.IsDownloading
                val bytesTotal: Long = (clen + bytesRead.toInt()).toLong()
                val input: InputStream = BufferedInputStream(connection.inputStream)
                val output: OutputStream = fos ?: FileOutputStream(rFile, true)
                var bytesPerSec = 0L
                val buffer = ByteArray(1024)
                var count: Int
                var lastUpdate = System.currentTimeMillis()

                // =================== SET KEYS ===================
                val child = DownloadFileMetadata(
                    id,
                    info.animeData.slug,
                    info.animeData,
                    posterPath,
                    path,
                    title,
                    info.episodeIndex,
                    System.currentTimeMillis(),
                    bytesTotal,
                    link
                )
                DataStore.setKey(
                    DOWNLOAD_CHILD_KEY,
                    id.toString(), // MUST HAVE ID TO NOT OVERRIDE
                    child
                )

                DataStore.setKey(
                    DOWNLOAD_PARENT_KEY, info.animeData.slug,
                    DownloadParentFileMetadata(
                        info.animeData.name,
                        mainPosterPath,
                        isMovie,
                        info.animeData.slug,
                        info.anilistID,
                        info.malID,
                        info.fillerEpisodes
                    )
                )

                downloadStartEvent.invoke(info.animeData.slug)
                downloadsUpdated.invoke(true)

                // =================== DOWNLOAD ===================
                while (true) {
                    try {
                        count = input.read(buffer)
                        if (count < 0) break

                        bytesRead += count
                        bytesPerSec += count
                        output.write(buffer, 0, count)
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastUpdate
                        val contains = downloadMustUpdateStatus.containsKey(id)
                        if (timeDiff > UPDATE_TIME || contains) {
                            if (contains) {
                                downloadMustUpdateStatus.remove(id)
                            }

                            if (downloadStatus[id] == DownloadStatusType.IsStopped) {
                                downloadStatus.remove(id)
                                if (rFile.exists()) {
                                    rFile.delete()
                                }
                                println("FILE STOPPED")
                                //downloadDeleteEvent.invoke(id)
                                showNot(0, bytesTotal, 0, DownloadType.IsStopped, info)
                                output.flush()
                                output.close()
                                input.close()
                                return@thread
                            } else {
                                showNot(
                                    bytesRead,
                                    bytesTotal,
                                    (bytesPerSec * UPDATE_TIME) / timeDiff,

                                    if (downloadStatus[id] == DownloadStatusType.IsPaused)
                                        DownloadType.IsPaused
                                    else
                                        DownloadType.IsDownloading,

                                    info
                                )
                                DownloadEventAndChild(DownloadEvent(id, bytesRead), child).let {
                                    downloadEvent.invoke(
                                        it
                                    )
                                }

                                lastUpdate = currentTime
                                bytesPerSec = 0
                                try {
                                    if (downloadStatus[id] == DownloadStatusType.IsPaused) {
                                        downloadPauseEvent.invoke(id)
                                        while (downloadStatus[id] == DownloadStatusType.IsPaused) {
                                            Thread.sleep(100)
                                        }
                                    }
                                } catch (e: Exception) {
                                }
                            }
                        }
                    } catch (_ex: Exception) {
                        println("CONNECT TRUE:::$_ex")
                        _ex.printStackTrace()
                        fullResume = true
                        /*if (isFromPaused) {
                        } else {
                            showNot(bytesRead, bytesTotal, 0, DownloadType.IsFailed, info)
                        }*/
                        break
                    }
                }

                if (fullResume) { // IF FULL RESUME DELETE CURRENT AND DON'T SHOW DONE
                    with(NotificationManagerCompat.from(localContext!!)) {
                        cancel(id)
                    }
                } else {
                    showNot(bytesRead, bytesTotal, 0, DownloadType.IsDone, info)
                    DownloadEventAndChild(DownloadEvent(id, bytesRead), child).let {
                        downloadEvent.invoke(
                            it
                        )
                    }
                }

                output.flush()
                output.close()
                input.close()

                // If using scoped storage move the files after download because resume stuff would fuck up otherwise
                if (usingScopedStorage && useExternalStorage) {
                    moveToExternalStorage(child)
                }

                /*if (scopedUri != null) {
                    val resolver = localContext?.contentResolver
                    val values =  ContentValues ()
                    values.put(MediaStore.Images.ImageColumns.IS_PENDING, false)
                    resolver?.update(scopedUri, values, null, null)
                }*/
                downloadStatus.remove(id)
            } catch (_ex: Exception) {
                println("FATAL EX DOWNLOADING:::${_ex.printStackTrace()}")
            } finally {
                if (downloadStatus.containsKey(id)) {
                    downloadStatus.remove(id)
                }
                if (fullResume) {
                    downloadEpisode(info, link, true)
                }
            }
        }
    }

    fun moveToExternalStorage(metadata: DownloadFileMetadata): Boolean {
        try {
            val id = (metadata.animeData.slug + "E${metadata.episodeIndex}").hashCode()
            val isMovie: Boolean =
                metadata.animeData.episodes?.size ?: 0 == 1 && metadata.animeData.status == "finished"

            val name = metadata.videoPath.split("/").last()
            val dir = if (isMovie) "/Shiro/" else "/Shiro/" + censorFilename(metadata.videoTitle) + "/"
            val rFile = File(metadata.videoPath)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = localContext?.contentResolver
                val values = ContentValues()
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + dir)

                val scopedUri = resolver?.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
                val fos = resolver.openOutputStream(scopedUri) as? FileOutputStream?
                fos?.let {
                    Files.copy(rFile.toPath(), it)
                    val child = metadata.copy(
                        videoPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                            .toString() + dir + name
                    )
                    DataStore.setKey(
                        DOWNLOAD_CHILD_KEY,
                        id.toString(), // MUST HAVE ID TO NOT OVERRIDE
                        child
                    )
                    rFile.delete()
                    return true
                }
            } else {
                val basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val path: String =
                    basePath.toString() + dir + name
                val newFile = File(path)
                newFile.mkdirs()
                rFile.copyTo(newFile)

                val child = metadata.copy(
                    videoPath = path
                )
                DataStore.setKey(
                    DOWNLOAD_CHILD_KEY,
                    id.toString(), // MUST HAVE ID TO NOT OVERRIDE
                    child
                )
                rFile.delete()
                return true
            }
        } catch (e: Exception) {
            println("CRASH IN MOVING TO EXTERNAL STORAGE ${e.printStackTrace()}")
        }
        return false
    }

    private fun showNot(
        progress: Long,
        total: Long,
        progressPerSec: Long,
        type: DownloadType,
        info: DownloadInfo
    ) {
        val isMovie: Boolean = info.animeData.episodes?.size ?: 0 == 1 && info.animeData.status == "finished"

        // Create an explicit intent for an Activity in your app
        val intent = Intent(localContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(localContext, 0, intent, 0)

        val progressPro = minOf(maxOf((progress * 100 / maxOf(total, 1)).toInt(), 0), 100)

        //val ep = info.animeData.episodes?.get(info.episodeIndex)//.card.cdnData.seasons[info.seasonIndex].episodes[info.episodeIndex]
        val id = (info.animeData.slug + "E${info.episodeIndex}").hashCode()

        var title = info.animeData.name
        if (title.replace(" ", "") == "") {
            title = "Episode " + info.episodeIndex + 1
        }
        var body = ""
        if (type == DownloadType.IsDownloading || type == DownloadType.IsPaused || type == DownloadType.IsFailed) {
            if (!isMovie) {
                body += "E${info.episodeIndex + 1} - ${title}\n"
            }
            body += "$progressPro % (${convertBytesToAny(progress, 1, 2.0)} MB/${
                convertBytesToAny(
                    total,
                    1,
                    2.0
                )
            } MB)"
        }

        val builder = NotificationCompat.Builder(localContext!!, CHANNEL_ID)
            .setSmallIcon(
                when (type) {
                    DownloadType.IsDone -> R.drawable.rddone
                    DownloadType.IsDownloading -> R.drawable.rdload
                    DownloadType.IsPaused -> R.drawable.rdpause
                    DownloadType.IsFailed -> R.drawable.rderror
                    DownloadType.IsStopped -> R.drawable.rderror
                }
            )
            .setContentTitle(
                when (type) {
                    DownloadType.IsDone -> "Download Done"
                    DownloadType.IsDownloading -> "${info.animeData.name} - ${
                        convertBytesToAny(
                            progressPerSec,
                            2,
                            2.0
                        )
                    } MB/s"
                    DownloadType.IsPaused -> "${info.animeData.name} - Paused"
                    DownloadType.IsFailed -> "Download Failed"
                    DownloadType.IsStopped -> "Download Stopped"
                }
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColorized(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setColor(localContext!!.getColorFromAttr(R.attr.colorAccent))

        if (type == DownloadType.IsDownloading) {
            builder.setProgress(100, progressPro, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = getImageBitmapFromUrl(getFullUrlCdn(info.animeData.image))
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
                builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()) // NICER IMAGE
            }
        }
        if (body.contains("\n") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //println("BIG TEXT: $body")
            val b = NotificationCompat.BigTextStyle()
            b.bigText(body)
            builder.setStyle(b)
        } else {
            println("SMALL TEXT: $body")
            builder.setContentText(body)
        }

        if ((type == DownloadType.IsDownloading || type == DownloadType.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actionTypes: MutableList<DownloadManager.DownloadActionType> = ArrayList()
            // INIT
            if (type == DownloadType.IsDownloading) {
                actionTypes.add(DownloadActionType.Pause)
                actionTypes.add(DownloadActionType.Stop)
            }

            if (type == DownloadType.IsPaused) {
                actionTypes.add(DownloadActionType.Resume)
                actionTypes.add(DownloadActionType.Stop)
            }

            // ADD ACTIONS
            for ((index, i) in actionTypes.withIndex()) {
                val _resultIntent = Intent(localContext, DownloadService::class.java)

                _resultIntent.putExtra(
                    "type", when (i) {
                        DownloadActionType.Resume -> "resume"
                        DownloadActionType.Pause -> "pause"
                        DownloadActionType.Stop -> "stop"
                    }
                )

                _resultIntent.putExtra("id", id)

                val pending: PendingIntent = PendingIntent.getService(
                    localContext, 3337 + index + id,
                    _resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(
                    NotificationCompat.Action(
                        when (i) {
                            DownloadActionType.Resume -> R.drawable.rdload
                            DownloadActionType.Pause -> R.drawable.rdpause
                            DownloadActionType.Stop -> R.drawable.rderror
                        }, when (i) {
                            DownloadActionType.Resume -> "Resume"
                            DownloadActionType.Pause -> "Pause"
                            DownloadActionType.Stop -> "Stop"
                        }, pending
                    )
                )
            }
        }

        with(NotificationManagerCompat.from(localContext!!)) {
            // notificationId is a unique int for each notification that you must define
            notify(id, builder.build())
        }
    }
}