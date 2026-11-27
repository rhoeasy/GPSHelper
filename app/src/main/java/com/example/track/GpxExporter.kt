package com.example.track

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates standards-compliant GPX 1.1 XML including elevation (<ele>) and
 * Garmin TrackPointExtension (<gpxtpx:hr>, <gpxtpx:speed>) for zero-permission sharing.
 */
object GpxExporter {

    fun buildGpxXml(
        session: TrackSessionEntity,
        points: List<TrackPointEntity>
    ): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val sb = StringBuilder(points.size * 180 + 512)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine(
            """<gpx version="1.1" creator="GPS Overlay HUD" """ +
                """xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">"""
        )
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(session.name)}</name>")
        sb.appendLine("    <time>${isoFormat.format(Date(session.startTimeMs))}</time>")
        sb.appendLine("  </metadata>")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(session.name)}</name>")
        sb.appendLine("    <type>${session.activityMode.name.lowercase(Locale.US)}</type>")
        sb.appendLine("    <trkseg>")

        for (pt in points) {
            sb.append(
                String.format(
                    Locale.US,
                    "      <trkpt lat=\"%.6f\" lon=\"%.6f\">\n",
                    pt.latitude,
                    pt.longitude
                )
            )
            pt.altitudeMeters?.let { alt ->
                sb.append(String.format(Locale.US, "        <ele>%.1f</ele>\n", alt))
            }
            sb.append("        <time>${isoFormat.format(Date(pt.timestampMs))}</time>\n")
            if (pt.heartRateBpm != null || pt.speedMps > 0f) {
                sb.append("        <extensions>\n")
                sb.append("          <gpxtpx:TrackPointExtension>\n")
                pt.heartRateBpm?.let { hr ->
                    sb.append("            <gpxtpx:hr>$hr</gpxtpx:hr>\n")
                }
                sb.append(String.format(Locale.US, "            <gpxtpx:speed>%.2f</gpxtpx:speed>\n", pt.speedMps))
                sb.append("          </gpxtpx:TrackPointExtension>\n")
                sb.append("        </extensions>\n")
            }
            sb.append("      </trkpt>\n")
        }

        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    fun exportToShareUri(
        context: Context,
        session: TrackSessionEntity,
        points: List<TrackPointEntity>
    ): Uri {
        val gpxDir = File(context.cacheDir, "gpx").apply { mkdirs() }
        val fileDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(session.startTimeMs))
        val prefix = when (session.activityMode) {
            ActivityMode.KARTING -> "karting"
            ActivityMode.HIKING -> "hiking"
            ActivityMode.OFFROAD_CROSSING -> "offroad"
        }
        val outFile = File(gpxDir, "${prefix}_${fileDate}.gpx")
        outFile.writeText(buildGpxXml(session, points), Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
