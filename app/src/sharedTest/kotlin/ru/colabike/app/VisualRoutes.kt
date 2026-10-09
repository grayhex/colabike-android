package ru.colabike.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideRoute

/** Trusted local synthetic GPX; preserve each trkseg rather than inventing a connecting line. */
object VisualRoutes {
    fun load(name: String): RideRoute =
        checkNotNull(javaClass.getResourceAsStream("/visual/routes/$name.gpx")).use(::parse)

    fun parse(input: java.io.InputStream): RideRoute {
        val lines = mutableListOf<List<GeoPoint>>()
        var segment = mutableListOf<GeoPoint>()
        val parser = Xml.newPullParser()
        run {
            parser.setInput(input, "UTF-8")
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when {
                    parser.eventType == XmlPullParser.START_TAG && parser.name == "trkseg" ->
                        segment = mutableListOf()
                    parser.eventType == XmlPullParser.START_TAG && parser.name == "trkpt" ->
                        segment +=
                            GeoPoint(
                                parser.getAttributeValue(null, "lat").toDouble(),
                                parser.getAttributeValue(null, "lon").toDouble(),
                            )
                    parser.eventType == XmlPullParser.END_TAG && parser.name == "trkseg" ->
                        lines += segment.toList()
                }
            }
        }
        return RideRoute(lines)
    }
}
