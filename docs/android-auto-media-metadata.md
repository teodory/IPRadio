# Android Auto playback metadata

This note covers the standard media UI that Android Auto projects from the phone. The app supplies the current metadata through its media session. Android Auto controls the playback and dashboard layouts. [Android Auto app experience](https://docs.partner.android.com/drivingux/android-auto/apps/overview) · [MediaLibraryService](https://developer.android.com/media/media3/session/serve-content)

## The three layouts

| Layout | Visible metadata and evidence |
| --- | --- |
| Full-screen playback | Google documents artwork, a title, an optional subtitle and an explicit-content indicator. Its session example sends the song in `DISPLAY_TITLE` and artist text in `DISPLAY_SUBTITLE`. A field-by-field test on a Galaxy S20+ projected to DHU 2.1 confirmed that `displayTitle` and `subtitle` supplied the visible text even when `title` and `artist` were also populated. [Playback view](https://docs.partner.android.com/drivingux/android-auto/apps/playback-view) · [Session metadata example](https://developer.android.com/training/cars/media/create-media-browser/content-styles#display-additional-metadata-indicators) · [TwinSen Radio field test](https://github.com/szczepl/twinsen-radio/blob/main/FINDINGS.md#1-which-field-goes-where) |
| Larger dashboard card alongside navigation | Google's example shows artwork and two text lines, “You got to listen” and “Michael Evans.” [Larger card example](https://docs.partner.android.com/drivingux/android-auto/product-experience/system-ui/images/cntar-nav-music.png) |
| Compact dashboard card with another card above | In the matching example, a message card appears above the player. The artwork area shrinks and both short text lines remain visible. [Compact card example](https://docs.partner.android.com/drivingux/android-auto/product-experience/system-ui/images/cntar-nav-msg.png) |

Google documents that the dashboard adapts to screen size and makes room for other cards. Its playback and dashboard guides leave the number of rendered title lines, truncation, and handling of `\n` unspecified. The dashboard illustrations use a short title that fits on one line. [Android Auto content area](https://docs.partner.android.com/drivingux/android-auto/product-experience/system-ui/content-area#minimized-view) · [Playback view](https://docs.partner.android.com/drivingux/android-auto/apps/playback-view)

### Observed on the VW ID.3

| Layout | Title | Subtitle |
| --- | --- | --- |
| Full-screen playback | Up to two large lines, with more width available than on the dashboard | One smaller line |
| Larger dashboard card | Up to two large lines | One smaller line |
| Compact dashboard card | One large line. Overflow and text after the first `\n` fall outside the visible line. | One smaller line |

In the two-line layouts, the title wraps at the available width. A `\n` reached on the first line starts the second line. If text before `\n` already wraps onto line two, text after the break falls beyond the visible two lines. These limits are owner observations for this setup. An independent Coolwalk user also reported title wrapping to two lines and subsequent truncation on the media tile. [Coolwalk user report](https://www.reddit.com/r/AndroidAuto/comments/110ahck/)

The Desktop Head Unit's **Media Playback Status** is an auxiliary display. In the TwinSen test its Song, Artist and Album labels corresponded to `displayTitle`, `subtitle` and `description`. The Passat instrument cluster also showed the description in that experiment. On the projected UI, a wrapped title can occupy two lines while still coming from a single field. [Google DHU guide](https://developer.android.com/training/cars/testing/dhu#media-playback-status) · [TwinSen Radio field test](https://github.com/szczepl/twinsen-radio/blob/main/FINDINGS.md#1-which-field-goes-where)

## Fields sent by Media3

The following mappings were checked in `LegacyConversions.convertToMediaMetadataCompat()` in the Media3 1.4.1 dependency IPRadio uses. The Android framework recommends the display keys for user-facing title and subtitle text. [IPRadio dependency version](../app/build.gradle#L61) · [AndroidX conversion source](https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/LegacyConversions.java) · [Android `MediaMetadata` reference](https://developer.android.com/reference/android/media/MediaMetadata)

| Media3 field | Legacy metadata key |
| --- | --- |
| `title` | `TITLE` |
| `artist` | `ARTIST` |
| `albumTitle` | `ALBUM` |
| `displayTitle` | `DISPLAY_TITLE` |
| `subtitle` | `DISPLAY_SUBTITLE` |
| `description` | `DISPLAY_DESCRIPTION` |
| `station` | No mapping in the legacy conversion |

Android's `MediaMetadata.getDescription()` uses the three display fields when `DISPLAY_TITLE` is nonempty. Otherwise it fills its title, subtitle and description from the first three nonempty values in `TITLE`, `ARTIST`, `ALBUM`, `ALBUM_ARTIST`, `WRITER`, `AUTHOR`, `COMPOSER` order. This is the framework fallback for consumers of `getDescription()`. Google publishes no corresponding per-card fallback rules for Android Auto. [Android framework source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaMetadata.java)

## Station name

The standard player and dashboard cards have no documented station-name slot. Media3's `station` field stores the station name for Media3 readers. `MEDIA_TYPE_RADIO_STATION` classifies the item. Apps can include station identity in `displayTitle` or `subtitle` as part of the display text. Google documents optional links from subtitles and descriptions for cars that support them, without specifying a separate description field in these projected views. [Media3 station field](https://developer.android.com/reference/androidx/media3/common/MediaMetadata.Builder#setStation(java.lang.CharSequence)) · [Media3 media type](https://developer.android.com/reference/androidx/media3/common/MediaMetadata.MediaType) · [Android Auto playback view](https://docs.partner.android.com/drivingux/android-auto/apps/playback-view) · [Playback metadata links](https://developer.android.com/training/cars/media/enable-playback#add_links_from_currently-playing_item)

A Car App Library media app can supply a separate header title in its full-screen `MediaPlaybackTemplate` using `Header.Builder.setTitle()`. This requires Car API 8 or later and a registered media playback token. The host still populates the track information from the media session. [Templated media app guide](https://developer.android.com/training/cars/apps/media#create_your_playback_template) · [MediaPlaybackTemplate reference](https://developer.android.com/reference/androidx/car/app/media/model/MediaPlaybackTemplate)

Google currently limits Play Store distribution of templated media apps to internal and closed testing tracks under its Early Access Program. [Media app distribution](https://developer.android.com/design/ui/cars/guides/app-types/create-media-apps) · [Android for Cars app categories](https://developer.android.com/training/cars#supported-app-categories)
