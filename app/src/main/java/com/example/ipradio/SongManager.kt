package com.example.ipradio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Objects
import kotlin.math.log

class SongManager {
    private val _text = MutableLiveData<String>()
    val text: LiveData<String> get() = _text

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun fetchText(radio: RadioData.Radio) {
        coroutineScope.launch {
            val result = makeNetworkRequest(radio.infoDataUrl)
            withContext(Dispatchers.Main) {
                val songInfo = parseData(result, radio.name)
                radio.songAuthor = songInfo
                _text.value = songInfo
            }
        }
    }

    private fun parseData(jsonString: String, radioName: String) : String
    {
        if (radioName == "SoftRockRadio.net")
        {
            return jsonString
        }

        // Clean
        var startInd = jsonString.indexOf("{")
        var cleanStr = jsonString.trim()
        if (startInd >= 0)
        {
            cleanStr = jsonString.substring(startInd)
        }

        val jsonElement: JsonElement?
        try {
            jsonElement = Json.parseToJsonElement(cleanStr)
        }
        catch (e: SerializationException) {
            e.printStackTrace()
            // Handle JSON parsing specific exceptions
//            log("Failed to parse JSON")
            return ""
        }
        if (jsonElement is JsonObject) {

            if (radioName == "ZRock") {
//{"main_slider":{"before":{"title":"","artist":"","time":"0","timer":"0","image":"/static/bg/microsites/zrock/img/music_top.jpg"},"now":{"title":"NO PAIN NO GAIN ( LIVE )","artist":"SCORPIONS","time":"244218","timer":"107769","image":"/static/bg/microsites/zrock/img/music_top.jpg"},"next":{"title":"SMOOTH SAILING","artist":"QUEENS OF THE STONE AGE","time":"224130","timer":"0","image":"/static/bg/microsites/zrock/img/music_top.jpg"}},"shrink_slider":{"title":"NO PAIN NO GAIN ( LIVE )","artist":"SCORPIONS","image":"/static/bg/microsites/zrock/img/music_top.jpg"}}
                val mainSlider = jsonElement["main_slider"]
                val now = mainSlider?.jsonObject?.get("now")
                val title = now?.jsonObject?.get("title").toString()
                val artist = now?.jsonObject?.get("artist").toString()
                val time = now?.jsonObject?.get("time")
                val timer = now?.jsonObject?.get("timer")
                return artist.replace("\"", "") + " - " + title.replace("\"", "")

            } else if (radioName == "1Rock") {
// {"nowplaying":[{"album":"","artist":"U2","duration":"00:03:37","iTunesTrackUrl":"","id":"4241","imageUrl":"https://is4-ssl.mzstatic.com/image/thumb/Music125/v4/e8/b4/67/e8b467ef-b318-5845-06b2-13f2574ab0c8/17UMGIM98829.rgb.jpg/360x360bb.jpg","status":"playing","time":"2024-08-01T20:04:36+03:00","title":"EVEN BETTER THE REAL THING","type":"song"},{"album":"","artist":"IRON MAIDEN","duration":"00:04:23","iTunesTrackUrl":"","id":"2037","imageUrl":"https://lastfm.freetls.fastly.net/i/u/174s/0b05ebbc6af93b2c88a5c9fa476974ca.png","status":"history","time":"2024-08-01T20:00:05+03:00","title":"THE NUMBER OF THE BEAST","type":"song"},{"album":"","artist":"RED HOT CHILI PEPPERS","duration":"00:03:32","iTunesTrackUrl":"","id":"4669","imageUrl":"https://lastfm.freetls.fastly.net/i/u/174s/e6605f39212f48d1b3968e78b5423456.png","status":"playing","time":"2024-08-01T19:56:29+03:00","title":"BY THE WAY","type":"song"},{"album":"","artist":"KISS","duration":"00:03:39","iTunesTrackUrl":"","id":"2977","imageUrl":"https://lastfm.freetls.fastly.net/i/u/174s/60a993d1a58a478880fe13e0415d7153.png","status":"history","time":"2024-08-01T19:52:43+03:00","title":"FOREVER","type":"song"},{"album":"","artist":"QUEEN","duration":"00:03:36","iTunesTrackUrl":"","id":"2187","imageUrl":"https://lastfm.freetls.fastly.net/i/u/174s/1aec5cac8403fbda275b8200b77c8318.png","status":"history","time":"2024-08-01T19:49:02+03:00","title":"ANOTHER ONE BITES THE DUST","type":"song"},{"album":"","artist":"SCORPIONS","duration":"00:04:09","iTunesTrackUrl":"","id":"1485","imageUrl":"https://is2-ssl.mzstatic.com/image/thumb/Music124/v4/65/29/ca/6529cad4-827a-8aa5-ebbc-f9de2ea33af2/00602567825265.rgb.jpg/360x360bb.jpg","status":"history","time":"2024-08-01T19:44:48+03:00","title":"ROCK YOU LIKE A HURICANE","type":"song"},{"album":"","artist":"JUDAS PRIEST","duration":"00:03:10","iTunesTrackUrl":"","id":"3817","imageUrl":"https://lastfm.freetls.fastly.net/i/u/174s/3aeb025283b742bf81fd81780ccae24d.png","status":"history","time":"2024-08-01T19:41:32+03:00","title":"BEFORE THE DAWN","type":"song"},{"album":"","artist":"DEF LEPPARD","duration":"00:05:00","iTunesTrackUrl":"","id":"2423","imageUrl":"https://lastfm.freetls.fastly.net/i/u/174s/c3d513a711ac41c1c10a3f7fb274e52f.png","status":"history","time":"2024-08-01T19:36:28+03:00","title":"LOVE BITES","type":"song"},{"album":"","artist":"ROLLING STONES","duration":"00:03:25","iTunesTrackUrl":"","id":"56246","imageUrl":"https://is1-ssl.mzstatic.com/image/thumb/Music128/v4/ef/9c/b0/ef9cb08b-0eb5-25f4-2082-3e113f96e1b5/00602537190911.rgb.jpg/360x360bb.jpg","status":"history","time":"2024-08-01T19:32:59+03:00","title":"START ME UP","type":"song"},{"album":"","artist":"DEEP PURPLE","duration":"00:08:34","iTunesTrackUrl":"","id":"13967","imageUrl":"https://lastfm.freetls.fastly.net/i/u/174s/cd8d952cfe584dafb6ff66d3816d519e.png","status":"history","time":"2024-08-01T19:24:19+03:00","title":"CHILD IN TIME","type":"song"},{"album":"","artist":"GARBAGE","duration":"00:03:37","iTunesTrackUrl":"","id":"2567","imageUrl":"https://lastfm.freetls.fastly.net/i/u/174s/e1ee4a6e9e1e40c28ed510e0272b8ceb.png","status":"history","time":"2024-08-01T19:20:33+03:00","title":"ONLY HAPPY WHEN IT RAINS","type":"song"},{"album":"","artist":"AC / DC","duration":"00:03:05","iTunesTrackUrl":"","id":"32662","imageUrl":"https://is4-ssl.mzstatic.com/image/thumb/Music114/v4/06/5f/19/065f1970-b71c-d18a-e756-3458f6ee51ff/source/360x360bb.jpg","status":"history","time":"2024-08-01T19:17:29+03:00","title":"SHOT IN THE DARK","type":"song"}]}
                val nowplaying = jsonElement["nowplaying"]
                val now = nowplaying?.jsonArray?.get(0)
                val artist = now?.jsonObject?.get("artist").toString()
                val title = now?.jsonObject?.get("title").toString()
                return artist.replace("\"", "") + " - " + title.replace("\"", "")

            } else if (radioName == "Magic FM") {
// {"hash":"8ef7e65294df4f46230d8f3d1b2a43e2","ts":1722531881,"performer":"Justin Timberlake","name":"CRY ME A RIVER","img":"https://m.netinfo.bg/magic/images/default.svg"}
                val artist = jsonElement["performer"].toString()
                val title = jsonElement["name"].toString()
                return artist.replace("\"", "") + " - " + title.replace("\"", "")
            }
            else if (radioName == "RockFM" || radioName == "Cadena100") {
// {"id": null, "uuid": "DFLT", "value": "{\"image\": \"\", \"author\": \"\", \"title\": \"RockFM\"}"}%
// {"id": null, "uuid": "DFLT", "value": "{\"image\": \"\", \"author\": \"MIKE OLDFIELD\", \"title\": \"MOONLIGHT SHADOW\"}"}%
                val value = jsonElement["value"]?.jsonPrimitive?.content
                if (value != null) {
                    // Parse the 'value' string into a JsonObject
                    val nestedJsonElement = Json.parseToJsonElement(value)
                    if (nestedJsonElement is JsonObject) {
                        val author = nestedJsonElement["author"]?.jsonPrimitive?.content.toString()
                        val title = nestedJsonElement["title"]?.jsonPrimitive?.content.toString()

                        return author.replace("\"", "") + " - " + title.replace("\"", "")
//                        return "Author: $author, Title: $title"
                    }
                }
            }
            else if (radioName == "SoftRockRadio.net")
            {
                //https://status.rcast.net/70632

            }
        }
        println(jsonElement)
        return ""
    }

    private suspend fun makeNetworkRequest(urlString: String): String {
        return withContext(Dispatchers.IO) {
            if (urlString.isEmpty()) {
                return@withContext "{}"
            }
            val url = URL(urlString)
            val urlConnection = url.openConnection() as HttpURLConnection
            try {
                val inputStream = urlConnection.inputStream
                inputStream.bufferedReader().use {
                    it.readText()
                }
            }
            catch (e: ConnectException) {
                e.printStackTrace()
                // Handle ConnectException specifically
                "Request failed due to connection error"
            } catch (e: IOException) {
                e.printStackTrace()
                // Handle other IOExceptions (e.g., other network failures)
                "Request failed due to network error"
            }
            finally {
                urlConnection.disconnect()
            }
        }
    }
}
