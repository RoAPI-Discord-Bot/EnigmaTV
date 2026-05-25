package com.enigma.tv.ui

/**
 * Netflix-style profile icons: official TMDB **movie/show posters** (heroes in costume on art),
 * not cast [profile_path] actor headshots. Selecting a preset saves [imageUrl] for Firebase sync.
 */
data class ProfileAvatarPreset(
    val label: String,
    val imageUrl: String
)

object ProfileAvatarPresets {
    private const val IMG = "https://image.tmdb.org/t/p/w342"

    val all: List<ProfileAvatarPreset> = listOf(
        ProfileAvatarPreset("Spider-Man", "$IMG/1g0dhYtq4irTY1GPXvft6k4YLjm.jpg"),
        ProfileAvatarPreset("Iron Man", "$IMG/78lPtwv72eTNqFW9COBYI0dWDJa.jpg"),
        ProfileAvatarPreset("Captain America", "$IMG/vSNxAJTlD0r02V9sPYpOjqDZXUK.jpg"),
        ProfileAvatarPreset("Thor", "$IMG/prSfAi1xGrhLQNxVSUFh61xQ4Qy.jpg"),
        ProfileAvatarPreset("Hulk", "$IMG/gKzYx79y0AQTL4UAk1cBQJ3nvrm.jpg"),
        ProfileAvatarPreset("Black Widow", "$IMG/7JPpIjhD2V0sKyFvhB9khUMa30d.jpg"),
        ProfileAvatarPreset("Hawkeye", "$IMG/ct5pNE5dDHryHLDnxyZPYcqO1sz.jpg"),
        ProfileAvatarPreset("Doctor Strange", "$IMG/xf8PbyQcR5ucXErmZNzdKR0s8ya.jpg"),
        ProfileAvatarPreset("Black Panther", "$IMG/uxzzxijgPIY7slzFvMotPv8wjKA.jpg"),
        ProfileAvatarPreset("Captain Marvel", "$IMG/xsaHr76ReEII0iLwP12IJvu0Ger.jpg"),
        ProfileAvatarPreset("Scarlet Witch", "$IMG/frobUz2X5Pc8OiVZU8Oo5K3NKMM.jpg"),
        ProfileAvatarPreset("Loki", "$IMG/kEl2t3OhXc3Zb9FBh1AuYzRTgZp.jpg"),
        ProfileAvatarPreset("Star-Lord", "$IMG/r7vmZjiyZw9rpJMQJdXpjgiCOk9.jpg"),
        ProfileAvatarPreset("Gamora", "$IMG/y4MBh0EjBlMuOzv9axM4qJlmhzz.jpg"),
        ProfileAvatarPreset("Rocket", "$IMG/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg"),
        ProfileAvatarPreset("Deadpool", "$IMG/3E53WEZJqP6aM84D8CckXx4pIHw.jpg"),
        ProfileAvatarPreset("Wolverine", "$IMG/fnbjcRDYn6YviCcePDnGdyAkYsB.jpg"),
        ProfileAvatarPreset("Batman", "$IMG/74xTEgt7R36Fpooo50r9T25onhq.jpg"),
        ProfileAvatarPreset("Wonder Woman", "$IMG/v4ncgZjG2Zu8ZW5al1vIZTsSjqX.jpg"),
        ProfileAvatarPreset("Superman", "$IMG/cB46TSg3kGjq2eVy5kVUhlpUa1H.jpg"),
        ProfileAvatarPreset("Flash", "$IMG/tCnOuAp8gL8LPcLJLGWrOidZMZw.jpg"),
        ProfileAvatarPreset("Aquaman", "$IMG/ufl63EFcc5XpByEV2Ecdw6WJZAI.jpg"),
        ProfileAvatarPreset("Joker", "$IMG/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg"),
        ProfileAvatarPreset("Mandalorian", "$IMG/jJDP4gyTB30xMbUViOvU3YoZngF.jpg")
    )

    fun count(): Int = all.size

    fun preset(index: Int): ProfileAvatarPreset = all[index.mod(count())]

    fun imageUrl(index: Int): String = preset(index).imageUrl

    fun label(index: Int): String = preset(index).label
}
