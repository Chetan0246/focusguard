package dev.chetan.focusguard.core

/**
 * Maps an app to a coarse [Category] using (1) a curated list of common student apps,
 * (2) Android's ApplicationInfo.category hint, (3) keyword heuristics on the package/label.
 * Pure Kotlin so it is unit-testable on the JVM.
 */
object AppCategories {
    private val known: Map<String, Category> = mapOf(
        // study
        "com.google.android.apps.classroom" to Category.STUDY,
        "org.coursera.android" to Category.STUDY,
        "com.udemy.android" to Category.STUDY,
        "org.khanacademy.android" to Category.STUDY,
        "com.duolingo" to Category.STUDY,
        "in.swayam.nptel" to Category.STUDY,
        "com.ankidroid" to Category.STUDY,
        "com.ichi2.anki" to Category.STUDY,
        "com.quizlet.quizletandroid" to Category.STUDY,
        "com.byjus.thelearningapp" to Category.STUDY,
        "com.unacademyapp" to Category.STUDY,
        "com.physicswallah.app" to Category.STUDY,
        "com.leetcode.app" to Category.STUDY,
        "com.hackerrank.android" to Category.STUDY,
        // productive
        "com.google.android.apps.docs" to Category.PRODUCTIVE,
        "com.google.android.apps.docs.editors.docs" to Category.PRODUCTIVE,
        "com.google.android.apps.docs.editors.sheets" to Category.PRODUCTIVE,
        "com.google.android.keep" to Category.PRODUCTIVE,
        "com.notion.id" to Category.PRODUCTIVE,
        "com.microsoft.office.onenote" to Category.PRODUCTIVE,
        "com.microsoft.office.word" to Category.PRODUCTIVE,
        "com.adobe.reader" to Category.PRODUCTIVE,
        "com.github.android" to Category.PRODUCTIVE,
        "com.google.android.calendar" to Category.PRODUCTIVE,
        "com.openai.chatgpt" to Category.PRODUCTIVE,
        "com.facebook.stella" to Category.PRODUCTIVE,
        // communication
        "com.whatsapp" to Category.COMMUNICATION,
        "org.telegram.messenger" to Category.COMMUNICATION,
        "com.google.android.gm" to Category.COMMUNICATION,
        "com.google.android.apps.messaging" to Category.COMMUNICATION,
        "com.discord" to Category.COMMUNICATION,
        "com.slack" to Category.COMMUNICATION,
        "com.google.android.dialer" to Category.COMMUNICATION,
        "com.facebook.orca" to Category.COMMUNICATION,
        // social
        "com.instagram.android" to Category.SOCIAL,
        "com.instagram.barcelona" to Category.SOCIAL,
        "com.facebook.katana" to Category.SOCIAL,
        "com.snapchat.android" to Category.SOCIAL,
        "com.twitter.android" to Category.SOCIAL,
        "com.reddit.frontpage" to Category.SOCIAL,
        "com.linkedin.android" to Category.SOCIAL,
        "com.pinterest" to Category.SOCIAL,
        "com.zhiliaoapp.musically" to Category.SOCIAL,
        // video
        "com.google.android.youtube" to Category.VIDEO,
        "com.netflix.mediaclient" to Category.VIDEO,
        "in.startv.hotstar" to Category.VIDEO,
        "com.amazon.avod.thirdpartyclient" to Category.VIDEO,
        "tv.twitch.android.app" to Category.VIDEO,
        "com.jio.media.ondemand" to Category.VIDEO,
        "com.spotify.music" to Category.OTHER,
        // games
        "com.supercell.clashroyale" to Category.GAMES,
        "com.supercell.clashofclans" to Category.GAMES,
        "com.dts.freefireth" to Category.GAMES,
        "com.pubg.imobile" to Category.GAMES,
        "com.activision.callofduty.shooter" to Category.GAMES,
        "com.mojang.minecraftpe" to Category.GAMES,
        "com.valvesoftware.android.steam.community" to Category.GAMES,
        // browsers
        "com.android.chrome" to Category.BROWSER,
        "org.mozilla.firefox" to Category.BROWSER,
        "com.brave.browser" to Category.BROWSER,
        "com.microsoft.emmx" to Category.BROWSER,
        "com.sec.android.app.sbrowser" to Category.BROWSER,
    )

    // android.content.pm.ApplicationInfo.CATEGORY_* constants (duplicated to stay JVM-pure).
    private const val CAT_GAME = 0
    private const val CAT_AUDIO = 1
    private const val CAT_VIDEO = 2
    private const val CAT_IMAGE = 3
    private const val CAT_SOCIAL = 4
    private const val CAT_NEWS = 5
    private const val CAT_MAPS = 6
    private const val CAT_PRODUCTIVITY = 7

    fun categorize(pkg: String, label: String, systemCategory: Int?): Category {
        known[pkg]?.let { return it }
        when (systemCategory) {
            CAT_GAME -> return Category.GAMES
            CAT_VIDEO -> return Category.VIDEO
            CAT_SOCIAL -> return Category.SOCIAL
            CAT_PRODUCTIVITY -> return Category.PRODUCTIVE
            CAT_NEWS -> return Category.BROWSER
            CAT_AUDIO, CAT_IMAGE, CAT_MAPS -> return Category.OTHER
        }
        val s = "$pkg $label".lowercase()
        return when {
            listOf("learn", "study", "class", "course", "academy", "tutor", "edu", "quiz", "exam", "nptel", "flashcard").any { it in s } -> Category.STUDY
            listOf("game", "games", "play.").any { it in s } -> Category.GAMES
            listOf("browser", "chrome", "firefox").any { it in s } -> Category.BROWSER
            listOf("video", "tv", "movie", "stream").any { it in s } -> Category.VIDEO
            listOf("chat", "messenger", "mail", "sms").any { it in s } -> Category.COMMUNICATION
            listOf("docs", "notes", "office", "pdf", "calendar", "todo", "task").any { it in s } -> Category.PRODUCTIVE
            else -> Category.OTHER
        }
    }
}
