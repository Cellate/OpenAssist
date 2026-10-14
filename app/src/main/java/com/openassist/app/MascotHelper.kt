package com.openassist.app

data class MascotData(
    val id: String,
    val name: String,         // Short mascot name: Ozzy, Pip, Echo, Nova, etc.
    val fullName: String,     // Descriptive name for picker
    val drawableRes: Int
)

object MascotHelper {
    val ALL_MASCOTS = listOf(
        MascotData("owl-orbital", "Ozzy", "Ozzy Orbital", R.drawable.ic_mascot_owl_orbital),
        MascotData("owl", "Ozzy", "Ozzy Classic", R.drawable.ic_mascot_owl),
        MascotData("owl-cosmic", "Ozzy", "Ozzy Cosmic", R.drawable.ic_mascot_owl_cosmic),
        MascotData("owl-talons", "Ozzy", "Ozzy Perch", R.drawable.ic_mascot_owl_talons),
        MascotData("owl-radar", "Ozzy", "Ozzy Radar", R.drawable.ic_mascot_owl_radar),
        MascotData("owl-wings", "Ozzy", "Ozzy Shield", R.drawable.ic_mascot_owl_wings),
        MascotData("owl-monocle", "Ozzy", "Ozzy Scholar", R.drawable.ic_mascot_owl_monocle),
        MascotData("owl-minimal", "Ozzy", "Ozzy Minimal", R.drawable.ic_mascot_owl_minimal),
        MascotData("fox", "Pip", "Pip Nexus", R.drawable.ic_mascot_fox),
        MascotData("fox-orbital", "Pip", "Pip Orbit", R.drawable.ic_mascot_fox_orbital),
        MascotData("fox-cyber", "Pip", "Pip Cyber", R.drawable.ic_mascot_fox_cyber),
        MascotData("fox-paws", "Pip", "Pip Paws", R.drawable.ic_mascot_fox_paws),
        MascotData("fox-firefly", "Pip", "Pip Radar", R.drawable.ic_mascot_fox_firefly),
        MascotData("fox-halo", "Pip", "Pip Halo", R.drawable.ic_mascot_fox_halo),
        MascotData("fox-headset", "Pip", "Pip Voice", R.drawable.ic_mascot_fox_headset),
        MascotData("fox-monocle", "Pip", "Pip Loupe", R.drawable.ic_mascot_fox_monocle),
        MascotData("fox-minimal", "Pip", "Pip Minimal", R.drawable.ic_mascot_fox_minimal),
        MascotData("bat", "Echo", "Echo Bat", R.drawable.ic_mascot_bat),
        MascotData("otter", "Nova", "Nova Otter", R.drawable.ic_mascot_otter),
        MascotData("chameleon", "Pico", "Pico Chameleon", R.drawable.ic_mascot_chameleon),
        MascotData("tanuki", "Taro", "Taro Tanuki", R.drawable.ic_mascot_tanuki),
        MascotData("sprite", "Ollie", "Ollie Sprite", R.drawable.ic_mascot_sprite),
        MascotData("kitten", "Byte", "Byte Kitten", R.drawable.ic_mascot_kitten),
        MascotData("firefly", "Lumi", "Lumi Firefly", R.drawable.ic_mascot_firefly),
        MascotData("axolotl", "Kiko", "Kiko Axolotl", R.drawable.ic_mascot_axolotl),
        MascotData("orbital", "Iris", "Iris Loop", R.drawable.ic_mascot_orbital),
        MascotData("whale", "Moby", "Moby-Ray", R.drawable.ic_mascot_whale)
    )

    fun getMascot(style: String?): MascotData {
        val s = (style ?: "owl-orbital").lowercase().trim()
        val exact = ALL_MASCOTS.firstOrNull { it.id == s }
        if (exact != null) return exact

        return when {
            s in listOf("default", "ozzy", "") -> ALL_MASCOTS[0]
            s in listOf("pip", "fennec") -> getMascot("fox")
            s in listOf("cat", "byte", "luna") -> getMascot("kitten")
            s in listOf("echo") -> getMascot("bat")
            s in listOf("nova") -> getMascot("otter")
            s in listOf("pico") -> getMascot("chameleon")
            s in listOf("taro", "momo") -> getMascot("tanuki")
            s in listOf("ollie", "bolt", "spark") -> getMascot("sprite")
            s in listOf("lumi") -> getMascot("firefly")
            s in listOf("kiko") -> getMascot("axolotl")
            s in listOf("iris") -> getMascot("orbital")
            s in listOf("moby") -> getMascot("whale")
            s.startsWith("bat") || s.contains("bat") || s.contains("echo") -> getMascot("bat")
            s.startsWith("otter") || s.contains("otter") || s.contains("nova") -> getMascot("otter")
            s.startsWith("chameleon") || s.contains("chameleon") || s.contains("pico") -> getMascot("chameleon")
            s.startsWith("tanuki") || s.contains("tanuki") || s.contains("taro") -> getMascot("tanuki")
            s.startsWith("kitten") || s.contains("kitten") || s.contains("cat") || s.contains("byte") -> getMascot("kitten")
            s.startsWith("sprite") || s.contains("sprite") || s.contains("ollie") -> getMascot("sprite")
            s.startsWith("firefly") || s.contains("firefly") || s.contains("lumi") -> getMascot("firefly")
            s.startsWith("axolotl") || s.contains("axolotl") || s.contains("kiko") -> getMascot("axolotl")
            s.startsWith("orbital") || s.contains("orbital") || s.contains("iris") -> getMascot("orbital")
            s.startsWith("whale") || s.contains("whale") || s.contains("moby") -> getMascot("whale")
            s.startsWith("fox") -> getMascot("fox")
            s.startsWith("owl") -> ALL_MASCOTS[0]
            else -> ALL_MASCOTS[0]
        }
    }

    fun getWholesomeGreeting(mascotId: String?): String {
        val s = (mascotId ?: "owl-orbital").lowercase().trim()
        return when {
            s.startsWith("owl") -> "Welcome home, friend! Ready to explore something wonderful today? I'm right here by your side whenever curiosity strikes! ✨"
            s.startsWith("fox") -> "Nexus synthesis online! I'm so thrilled to be your companion. What brilliant questions are we tackling today? 🦊"
            s == "bat" || s == "echo" -> "Sonic radar locked on! I'm tuned to your wavelength, friend. Ready to listen and uncover insights anytime! 🦇"
            s == "otter" || s == "nova" -> "Splashing into the sea of knowledge! Having you here makes today amazing. Let's dive deep and create! 🦦"
            s == "chameleon" || s == "pico" -> "Rainbow vision active! I adapt to whatever you need, seamlessly. So happy to be your partner! 🦎"
            s == "tanuki" || s == "taro" -> "Offline, loyal, and ready to roll! Your questions stay 100% private with me. Let's conquer the day! 🦝"
            s == "kitten" || s == "byte" -> "Purr-fect day to learn together! I'm your cuddly pocket guide, always ready to assist! 🐱"
            s == "sprite" || s == "ollie" -> "Cosmic spark charged up! Let's bring great energy to everything you search and learn today! 💫"
            s == "axolotl" || s == "kiko" -> "Glowing with joy to see you! Every question is an adventure. Let's discover something cool! 🌸"
            s == "firefly" || s == "lumi" -> "Illuminating your ideas! We make an unstoppable team. Ready to shine bright! 🏮"
            s == "orbital" || s == "iris" -> "Orbit synchronized and sentinel standing by! You're the pilot, and I've got your back on every screen! 🪐"
            s == "whale" || s == "moby" -> "Deep navigation active! Navigating the boundless ocean of ideas together with you! 🐋"
            else -> "Welcome back, friend! I'm overjoyed to be your companion. Let's discover something wonderful together today! ✨"
        }
    }
}
