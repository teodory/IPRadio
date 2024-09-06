package com.example.ipradio

import java.time.Duration

class RadioData {

    data class Radio(
        val name: String,
        val url: String,
        val image: String,
        var songAuthor: String,
//        val songName: String,
//        val time: Int,
//        val duration: Int,
        val logo: Int,
        val infoDataUrl: String,
        val headers: Map<String, String>
    )


    val radios = listOf(
        Radio(
            "ZRock",
            "https://web.static.btv.bg/radio/zrock-radio-proxy/",
//                  https://cdn.bweb.bg/radio/z-rock.mp3
//                "https://zrock.bg/lbin/njoy/show_info_ajax.php",
//                "https://46.10.150.243:80/z-rock.mp3",
            "https://zrock.bg/static/bg/microsites/zrock/img/logo.png",
            "",
            0,
                "", //"https://zrock.bg/lbin/zrock/refresh_song_ajax.php?view=desktop",
//                "https://zrock.bg/lbin/zrock/refresh_song_ajax.php",
                emptyMap()
        ),
        Radio(
            "1Rock",
            "https://25543.live.streamtheworld.com/RADIO_1_ROCKAAC_L.aac",
//                "https://play.global.audio/radio1rock128",
            "https://www.radio1rock.bg/theme_assets/radio1rock/images/logo.png?v=0",
            "",
            0,
            "https://meta.metacast.eu/aim/?radio=radio1rock",
            // https://meta.metacast.eu/?radio=radio1rock&songsNumber=3&jsonp=1&callback=jQuery22407916947746979056_1720725053132&_=1720725053134"
            emptyMap()
        ), //"http://play.global.audio/radio1rock.opus"
        Radio(
            "Darik", "https://darikradio.by.host.bg:8000/S2-128",
            "https://darikradio.bg/img/logo.new.png",
            "",
            R.drawable.darik_logo,
            "",
            emptyMap()
        ),
//                "https://assets.jobs.bg/assets/logo/2017-01-08/b_b0672448dc12b4ce95282a03786fbd9e.png"),
        Radio(
            "FM+",
            "http://193.108.24.21:8000/fmplus?file=.mp3",
            "http://fmplus.net/playernew/i/logo_fmplus.png",
            "",
            0,
            "",
            emptyMap()
        ),
        Radio(
            "Magic FM", "https://bss1.neterra.tv/magicfm/magicfm.m3u8",
            "https://m.netinfo.bg/media/images/50346/50346532/orig-orig-magic-tv-radio.jpg",
            "",
            R.drawable.magic_tv_radio,
            "https://www.magic.bg/magic/song.php",
            emptyMap()
            //{"hash":"d2c38c83cbd49af7c3d5aa396e1853cd","ts":1720723874,"performer":"Sophie B Hawkins","name":"RIGHT BESIDE YOU","img":"https:\/\/m.netinfo.bg\/magic\/images\/default.svg"}
        ),
        Radio(
            "Tangra Mega Rock", "http://stream-bg-01.radiotangra.com:8000/Tangra-high",
            "http://www.radiotangra.com/img/logo_tangra.png",
            "",
            0,
            "",
            emptyMap()
        ),

        Radio(
            "RockFM",
            "https://rockfm-cope.flumotion.com/chunks.m3u8",
//            "http://www.w3.org/2000/svg",
            "https://static.mytuner.mobi/media/tvos_radios/7WH7nKXkwa.png",
            "",
            0,
            // "https://bo-cope-webtv.flumotion.com/api/active?format=json&podId=78"
            // {"id": null, "uuid": "DFLT", "value": "{\"image\": \"\", \"author\": \"KINGS OF LEON\", \"title\": \"USE SOMEBODY\"}"}
            "https://bo-cope-webtv.flumotion.com/api/active?format=json&podId=78",
            emptyMap()
        ),

        Radio(
                "Cadena100",
        "https://cadena100-cope.flumotion.com/chunks.m3u8",
//        "http://www.w3.org/2000/svg",
//        {"id": null, "uuid": "DFLT", "value": "{\"image\": \"\", \"author\": \"\", \"title\": \"RockFM\"}"}%
        "https://cadena100-cdnsta.agilecontent.com/cadena100-frontend/public/assets/img/cadena-100@3x-798d508aa9118a7b741a03695b4ad779.png",
            "",
        0,
            "https://bo-cope-webtv.flumotion.com/api/active?format=json&podId=76",
        emptyMap()
        ),
        Radio(
            "SoftRockRadio.net",
            "https://gold.streamguys1.com/softrock.mp3",
//            "https://softrockradio.net/__static/7c186515-6b3c-414b-a6f9-7bcf631fbbbf/image_desktop",
            "https://cdn-radiotime-logos.tunein.com/s113401d.png",
            "",
            0,
            "https://status.rcast.net/70632",
            emptyMap()
        ),
        Radio(
            "Melody",
            "http://193.108.24.6:8000/melody?file=.mp3",
//            "http://fmplus.net/playernew/melody.php",
            "https://radiomelody.bg/i/logo.png",
            "",
            0,
            "",
            emptyMap()
        ),
//        Radio(
//            "Antena",
//            "https://live.radioantena.bg/RadioAntena",
//            "https://radioantena.bg/radio-antena-logo.jpg",
//            0,
//            mapOf(
//                "Host" to "live.radioantena.bg" ,
//                "Referer" to "https://radioantena.bg/"
//            )
//        )
    )



}