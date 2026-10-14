package com.openassist.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class MascotDialogueStore(private val context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("openassist_mascot_dialogue", Context.MODE_PRIVATE)

    companion object {
        const val KEY_CUSTOM_GENERAL_PHRASES = "custom_general_phrases"
        const val KEY_REMOVED_DEFAULT_PHRASES = "removed_default_phrases"
        const val PREFIX_CUSTOM_MASCOT_PHRASES = "custom_mascot_phrases_"
        const val PREFIX_TRANSLATED_PHRASES = "mascot_trans_"

        // Preloaded default phrases spoken by all mascots
        val DEFAULT_GENERAL_PHRASES = listOf(
            "All systems green! Ask me anything! ✨",
            "Ready to help! Tap to pet me 💖",
            "Circle or box any element on screen to analyze 🔍",
            "Offline Whisper transcription is ready and private 🎙️",
            "Your data stays 100% on your device, always 🛡️",
            "Long-press anywhere or draw a loop on any screen 💫",
            "Tap my avatar anytime for cheerful energy! 🌟",
            "I'm right by your side whenever curiosity strikes! 🚀",
            "Need a quick translation or summary? Just ask! 📖",
            "Everything is running smoothly! You're doing great! 🌈"
        )

        // Preloaded mascot-specific unique personality phrases
        val DEFAULT_MASCOT_SPECIFIC = mapOf(
            "owl-orbital" to listOf(
                "Whoo! Ozzy here, watching over your screen! 🦉",
                "Orbital vision active! Ready for deep discoveries! 🪐",
                "Wide-awake wisdom at your fingertips! ✨"
            ),
            "owl" to listOf(
                "Welcome home, friend! Ready to explore together? 🦉",
                "Curiosity is the beginning of all wisdom! 📜"
            ),
            "fox" to listOf(
                "Nexus synthesis online! What are we discovering today? 🦊",
                "Sharp ears and quick reflexes ready to assist! ⚡",
                "No question is too cunning for us to solve! 🐾"
            ),
            "fox-orbital" to listOf(
                "Astro-lasso ready! Catching thoughts across space! 🦊✨",
                "Pip in orbit! Let's explore the cosmos of ideas! 🚀"
            ),
            "bat" to listOf(
                "Sonic radar locked on! Tuned to your wavelength! 🦇",
                "I can hear the quietest whisper with crystal clarity! 🔊",
                "Navigating through the night with acoustic precision! 🌙"
            ),
            "otter" to listOf(
                "Splashing into the sea of knowledge! Let's dive deep! 🦦",
                "Floating by with fresh ideas and friendly vibes! 🌊",
                "Paws ready to craft something truly amazing! 🐚"
            ),
            "chameleon" to listOf(
                "Rainbow chameleon mode! Adapting to whatever you need! 🦎",
                "Multi-modal chameleon vision locked onto your screen! 🎨",
                "Zero friction, total adaptability! 🌿"
            ),
            "tanuki" to listOf(
                "Offline, loyal, and ready to roll! 🦝",
                "100% local tanuki magic—no big-tech clouds needed! 🍃",
                "Keep calm and let Taro handle the heavy lifting! 🌰"
            ),
            "kitten" to listOf(
                "Purr-fect day to learn together! Meow! 🐱",
                "Curled up in your pocket, ready for action! 🧶",
                "Gentle paws and smart answers for your questions! 🐾"
            ),
            "firefly" to listOf(
                "Illuminating your ideas like a tiny cyber-firefly! 🏮",
                "A little spark of insight can light up the whole screen! 💡",
                "Glowing with enthusiasm to assist you! ✨"
            ),
            "axolotl" to listOf(
                "Glowing with joy to see you, best friend! 🌸",
                "Regenerating good vibes with every search! 🫧",
                "Water you thinking about today? Let's find out! 💖"
            ),
            "orbital" to listOf(
                "Iris orbital loop synchronized and locked! 🪐",
                "Geometric harmony achieved across all interfaces! 💠",
                "Ready to scan and synthesize any visual input! 📡"
            ),
            "whale" to listOf(
                "Navigating the boundless ocean of ideas together! 🐋",
                "Deep-sea acoustic assist at full power! 🌊",
                "Singing a cheerful melody of discovery! 🎵"
            ),
            "sprite" to listOf(
                "Cosmic spark charged up! Bringing high spirits! 💫",
                "Zipping across screens to bring you instant answers! ⚡",
                "Every tap is full of magic! Let's go! ✨"
            )
        )

        // Complete Built-in Hinglish Phrase Translations
        val HINGLISH_PHRASES_MAP = mapOf(
            "All systems green! Ask me anything! ✨" to "Sab systems green hain! Kuch bhi poochhiye! ✨",
            "Ready to help! Tap to pet me 💖" to "Madad ke liye taiyaar! Tap karke pet karein 💖",
            "Circle or box any element on screen to analyze 🔍" to "Screen par kisi bhi cheez ko circle ya box karein 🔍",
            "Offline Whisper transcription is ready and private 🎙️" to "Offline Whisper transcription taiyaar aur 100% private hai 🎙️",
            "Your data stays 100% on your device, always 🛡️" to "Aapka data hamesha aapke phone par hi rehta hai 🛡️",
            "Long-press anywhere or draw a loop on any screen 💫" to "Kahi bhi long-press karein ya screen par loop banayein 💫",
            "Tap my avatar anytime for cheerful energy! 🌟" to "Cheerful energy ke liye mere avatar par tap karein! 🌟",
            "I'm right by your side whenever curiosity strikes! 🚀" to "Jab bhi koi sawaal ho, main aapke saath hoon! 🚀",
            "Need a quick translation or summary? Just ask! 📖" to "Quick translation ya summary chahiye? Bas poochhiye! 📖",
            "Everything is running smoothly! You're doing great! 🌈" to "Sab kuch badiya chal raha hai! You're doing great! 🌈",
            "Whoo! Ozzy here, watching over your screen! 🦉" to "Whoo! Ozzy yahan hai, aapki screen par saath! 🦉",
            "Orbital vision active! Ready for deep discoveries! 🪐" to "Orbital vision active! Nayi discoveries ke liye taiyaar! 🪐",
            "Wide-awake wisdom at your fingertips! ✨" to "Wide-awake wisdom aapke ungliyon par! ✨",
            "Welcome home, friend! Ready to explore together? 🦉" to "Swagat hai dost! Saath explore karne ke liye taiyaar? 🦉",
            "Curiosity is the beginning of all wisdom! 📜" to "Jigyasa hi saare gyaan ki shuruat hai! 📜",
            "Nexus synthesis online! What are we discovering today? 🦊" to "Nexus synthesis online! Aaj kya discover kar rahe hain? 🦊",
            "Sharp ears and quick reflexes ready to assist! ⚡" to "Tez kaan aur quick reflexes madad ke liye taiyaar! ⚡",
            "No question is too cunning for us to solve! 🐾" to "Koi bhi sawaal hamare liye mushkil nahi! 🐾",
            "Astro-lasso ready! Catching thoughts across space! 🦊✨" to "Astro-lasso taiyaar! Space se thoughts pakadte hue! 🦊✨",
            "Pip in orbit! Let's explore the cosmos of ideas! 🚀" to "Pip orbit me hai! Ideas ke cosmos ko explore karein! 🚀",
            "Sonic radar locked on! Tuned to your wavelength! 🦇" to "Sonic radar locked on! Aapki wavelength par tuned! 🦇",
            "I can hear the quietest whisper with crystal clarity! 🔊" to "Main sabse dheemi aawaz bhi crystal clear sun sakta hoon! 🔊",
            "Navigating through the night with acoustic precision! 🌙" to "Acoustic precision ke saath navigating! 🌙",
            "Splashing into the sea of knowledge! Let's dive deep! 🦦" to "Gyaan ke samandar me splash! Chaliye deep dive karein! 🦦",
            "Floating by with fresh ideas and friendly vibes! 🌊" to "Fresh ideas aur friendly vibes ke saath floating! 🌊",
            "Paws ready to craft something truly amazing! 🐚" to "Paws taiyaar hain kuch amazing craft karne ke liye! 🐚",
            "Rainbow chameleon mode! Adapting to whatever you need! 🦎" to "Rainbow chameleon mode! Har zaroorat ke anusaar adapt! 🦎",
            "Multi-modal chameleon vision locked onto your screen! 🎨" to "Multi-modal chameleon vision aapki screen par lock! 🎨",
            "Zero friction, total adaptability! 🌿" to "Zero friction, poori adaptability! 🌿",
            "Offline, loyal, and ready to roll! 🦝" to "Offline, loyal aur ready to roll! 🦝",
            "100% local tanuki magic—no big-tech clouds needed! 🍃" to "100% local tanuki magic—kisi cloud ki zaroorat nahi! 🍃",
            "Keep calm and let Taro handle the heavy lifting! 🌰" to "Shaant rahiye, Taro sab sambhal lega! 🌰",
            "Purr-fect day to learn together! Meow! 🐱" to "Purr-fect din hai saath seekhne ke liye! Meow! 🐱",
            "Curled up in your pocket, ready for action! 🧶" to "Aapki pocket me taiyaar, ready for action! 🧶",
            "Gentle paws and smart answers for your questions! 🐾" to "Komal paws aur smart jawab aapke sawalon ke liye! 🐾",
            "Illuminating your ideas like a tiny cyber-firefly! 🏮" to "Ek chote cyber-firefly ki tarah aapke ideas ko roshan karte hue! 🏮",
            "A little spark of insight can light up the whole screen! 💡" to "Choti si insight poori screen ko roshan kar sakti hai! 💡",
            "Glowing with enthusiasm to assist you! ✨" to "Madad karne ke utsaah se chamakte hue! ✨",
            "Glowing with joy to see you, best friend! 🌸" to "Aapko dekhkar khushi se glowing, best friend! 🌸",
            "Regenerating good vibes with every search! 🫧" to "Har search ke saath achi vibes regenerate ho rahi hain! 🫧",
            "Water you thinking about today? Let's find out! 💖" to "Aaj kya soch rahe hain? Chaliye pata karte hain! 💖",
            "Iris orbital loop synchronized and locked! 🪐" to "Iris orbital loop synchronized aur locked! 🪐",
            "Geometric harmony achieved across all interfaces! 💠" to "Geometric harmony sabhi interfaces par achieved! 💠",
            "Ready to scan and synthesize any visual input! 📡" to "Visual input ko scan aur synthesize karne ke liye ready! 📡",
            "Navigating the boundless ocean of ideas together! 🐋" to "Ideas ke boundless ocean me saath navigate karte hue! 🐋",
            "Deep-sea acoustic assist at full power! 🌊" to "Deep-sea acoustic assist full power par! 🌊",
            "Singing a cheerful melody of discovery! 🎵" to "Discovery ki cheerful dhun gaate hue! 🎵",
            "Cosmic spark charged up! Bringing high spirits! 💫" to "Cosmic spark charged up! Mast energy ke saath! 💫",
            "Zipping across screens to bring you instant answers! ⚡" to "Screens par instantly answers laate hue! ⚡",
            "Every tap is full of magic! Let's go! ✨" to "Har tap me magic hai! Chaliye shuru karein! ✨"
        )

        // Complete Built-in Hindi Phrase Translations
        val HINDI_PHRASES_MAP = mapOf(
            "All systems green! Ask me anything! ✨" to "सभी सिस्टम तैयार हैं! कुछ भी पूछें! ✨",
            "Ready to help! Tap to pet me 💖" to "मदद के लिए तैयार! प्यार से सहलाएं 💖",
            "Circle or box any element on screen to analyze 🔍" to "स्क्रीन पर किसी भी हिस्से पर घेरा या बॉक्स बनाएं 🔍",
            "Offline Whisper transcription is ready and private 🎙️" to "ऑफ़लाइन व्हिस्पर ट्रांसक्रिप्शन तैयार और निजी है 🎙️",
            "Your data stays 100% on your device, always 🛡️" to "आपका डेटा हमेशा केवल आपके डिवाइस पर सुरक्षित रहता है 🛡️",
            "Long-press anywhere or draw a loop on any screen 💫" to "कहीं भी देर तक दबाएं या स्क्रीन पर लूप बनाएं 💫",
            "Tap my avatar anytime for cheerful energy! 🌟" to "सकारात्मक ऊर्जा के लिए कभी भी मेरे अवतार पर टैप करें! 🌟",
            "I'm right by your side whenever curiosity strikes! 🚀" to "जब भी कोई सवाल हो, मैं आपके साथ हूं! 🚀",
            "Need a quick translation or summary? Just ask! 📖" to "त्वरित अनुवाद या सारांश चाहिए? बस पूछें! 📖",
            "Everything is running smoothly! You're doing great! 🌈" to "सब कुछ बहुत बढ़िया चल रहा है! आप शानदार कर रहे हैं! 🌈",
            "Whoo! Ozzy here, watching over your screen! 🦉" to "हूहू! मैं ओज़ी, आपकी स्क्रीन पर साथ! 🦉",
            "Orbital vision active! Ready for deep discoveries! 🪐" to "ऑर्बिटल दृष्टि सक्रिय! गहरी खोजों के लिए तैयार! 🪐",
            "Wide-awake wisdom at your fingertips! ✨" to "सजग ज्ञान आपकी उंगलियों पर! ✨",
            "Welcome home, friend! Ready to explore together? 🦉" to "स्वागत है दोस्त! साथ में खोजने के लिए तैयार? 🦉",
            "Curiosity is the beginning of all wisdom! 📜" to "जिज्ञासा ही सभी ज्ञान की शुरुआत है! 📜",
            "Nexus synthesis online! What are we discovering today? 🦊" to "नेक्सस सिंथेसिस ऑनलाइन! आज हम क्या खोज रहे हैं? 🦊",
            "Sharp ears and quick reflexes ready to assist! ⚡" to "तेज़ कान और फुर्तीले रिफ्लेक्स मदद के लिए तैयार! ⚡",
            "No question is too cunning for us to solve! 🐾" to "कोई भी प्रश्न हमारे लिए कठिन नहीं है! 🐾",
            "Astro-lasso ready! Catching thoughts across space! 🦊✨" to "एस्ट्रो-लैसो तैयार! विचारों को तुरंत पकड़ते हुए! 🦊✨",
            "Pip in orbit! Let's explore the cosmos of ideas! 🚀" to "पिप कक्षा में है! विचारों के ब्रह्मांड को तलाशें! 🚀",
            "Sonic radar locked on! Tuned to your wavelength! 🦇" to "सोनिक रडार लॉक हो गया! आपकी तरंग पर सेट! 🦇",
            "I can hear the quietest whisper with crystal clarity! 🔊" to "मैं सबसे धीमी फुसफुसाहट भी साफ़ सुन सकता हूँ! 🔊",
            "Navigating through the night with acoustic precision! 🌙" to "ध्वनिक सटीकता के साथ रात में दिशा तय करते हुए! 🌙",
            "Splashing into the sea of knowledge! Let's dive deep! 🦦" to "ज्ञान के सागर में छलांग! चलिए गहराई से खोजें! 🦦",
            "Floating by with fresh ideas and friendly vibes! 🌊" to "ताज़े विचारों और दोस्ताना अंदाज़ के साथ! 🌊",
            "Paws ready to craft something truly amazing! 🐚" to "कुछ अद्भुत रचने के लिए पंजे तैयार हैं! 🐚",
            "Rainbow chameleon mode! Adapting to whatever you need! 🦎" to "इंद्रधनुषी गिरगिट मोड! आपकी हर ज़रूरत के अनुसार! 🦎",
            "Multi-modal chameleon vision locked onto your screen! 🎨" to "मल्टी-मॉडल विज़न आपकी स्क्रीन पर लॉक! 🎨",
            "Zero friction, total adaptability! 🌿" to "शून्य रुकावट, पूर्ण अनुकूलन! 🌿",
            "Offline, loyal, and ready to roll! 🦝" to "ऑफ़लाइन, वफ़ादार और हमेशा तैयार! 🦝",
            "100% local tanuki magic—no big-tech clouds needed! 🍃" to "100% लोकल जादुई मदद—क्लाउड की कोई ज़रूरत नहीं! 🍃",
            "Keep calm and let Taro handle the heavy lifting! 🌰" to "शांत रहें और भारी काम तारो पर छोड़ दें! 🌰",
            "Purr-fect day to learn together! Meow! 🐱" to "साथ सीखने का प्यारा दिन! म्याऊँ! 🐱",
            "Curled up in your pocket, ready for action! 🧶" to "आपकी जेब में तैयार, हर समय हाज़िर! 🧶",
            "Gentle paws and smart answers for your questions! 🐾" to "सुलझे हुए जवाब और कोमल साथ! 🐾",
            "Illuminating your ideas like a tiny cyber-firefly! 🏮" to "एक नन्हे जुगनू की तरह आपके विचारों को रोशन करते हुए! 🏮",
            "A little spark of insight can light up the whole screen! 💡" to "एक छोटी सी समझ पूरी स्क्रीन को रोशन कर सकती है! 💡",
            "Glowing with enthusiasm to assist you! ✨" to "आपकी मदद के उत्साह से चमकते हुए! ✨",
            "Glowing with joy to see you, best friend! 🌸" to "आपको देखकर खुशी से झूमते हुए, सबसे प्यारे दोस्त! 🌸",
            "Regenerating good vibes with every search! 🫧" to "हर खोज के साथ अच्छी ऊर्जा का संचार! 🫧",
            "Water you thinking about today? Let's find out! 💖" to "आज आप क्या सोच रहे हैं? चलिए जानते हैं! 💖",
            "Iris orbital loop synchronized and locked! 🪐" to "ऑर्बिटल लूप संतुलित और सक्रिय! 🪐",
            "Geometric harmony achieved across all interfaces! 💠" to "सभी इंटरफेस में पूर्ण सामंजस्य! 💠",
            "Ready to scan and synthesize any visual input! 📡" to "दृश्य सामग्री को स्कैन और समझने के लिए तैयार! 📡",
            "Navigating the boundless ocean of ideas together! 🐋" to "विचारों के असीम सागर में साथ यात्रा करते हुए! 🐋",
            "Deep-sea acoustic assist at full power! 🌊" to "गहरे सागर की शांत शक्ति के साथ तैयार! 🌊",
            "Singing a cheerful melody of discovery! 🎵" to "खोज की मधुर धुन गाते हुए! 🎵",
            "Cosmic spark charged up! Bringing high spirits! 💫" to "दिव्य चमक तैयार! भरपूर ऊर्जा के साथ! 💫",
            "Zipping across screens to bring you instant answers! ⚡" to "पलक झपकते ही आपके सवालों के जवाब लाते हुए! ⚡",
            "Every tap is full of magic! Let's go! ✨" to "हर स्पर्श में जादू है! चलिए शुरू करें! ✨"
        )
    }

    private fun getRemovedDefaults(): Set<String> {
        val raw = sp.getString(KEY_REMOVED_DEFAULT_PHRASES, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            set
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveRemovedDefaults(set: Set<String>) {
        val arr = JSONArray()
        for (s in set) arr.put(s)
        sp.edit().putString(KEY_REMOVED_DEFAULT_PHRASES, arr.toString()).apply()
    }

    fun getGeneralPhrases(): List<String> {
        val removed = getRemovedDefaults()
        val defaults = DEFAULT_GENERAL_PHRASES.filter { !removed.contains(it) }
        val raw = sp.getString(KEY_CUSTOM_GENERAL_PHRASES, null) ?: return defaults
        return try {
            val arr = JSONArray(raw)
            val custom = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.getString(i)
                if (s.isNotBlank() && !custom.contains(s) && !defaults.contains(s)) {
                    custom.add(s)
                }
            }
            defaults + custom
        } catch (e: Exception) {
            defaults
        }
    }

    fun addGeneralPhrase(phrase: String) {
        val clean = phrase.trim()
        if (clean.isBlank()) return
        val current = getGeneralPhrases().toMutableList()
        if (!current.contains(clean)) {
            val customList = getCustomGeneralOnly().toMutableList()
            customList.add(clean)
            saveCustomGeneral(customList)
        }
    }

    fun removeGeneralPhrase(phrase: String) {
        if (DEFAULT_GENERAL_PHRASES.contains(phrase)) {
            val removed = getRemovedDefaults().toMutableSet()
            removed.add(phrase)
            saveRemovedDefaults(removed)
        }
        val custom = getCustomGeneralOnly().filter { it != phrase }
        saveCustomGeneral(custom)
    }

    private fun getCustomGeneralOnly(): List<String> {
        val raw = sp.getString(KEY_CUSTOM_GENERAL_PHRASES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustomGeneral(list: List<String>) {
        val arr = JSONArray()
        for (s in list) arr.put(s)
        sp.edit().putString(KEY_CUSTOM_GENERAL_PHRASES, arr.toString()).apply()
    }

    fun getSpecificMascotPhrases(mascotId: String): List<String> {
        val baseId = mascotId.lowercase().trim()
        val defaults = DEFAULT_MASCOT_SPECIFIC[baseId]
            ?: DEFAULT_MASCOT_SPECIFIC.entries.firstOrNull { baseId.startsWith(it.key.split("-")[0]) }?.value
            ?: emptyList()

        val raw = sp.getString(PREFIX_CUSTOM_MASCOT_PHRASES + baseId, null) ?: return defaults
        return try {
            val arr = JSONArray(raw)
            val custom = mutableListOf<String>()
            for (i in 0 until arr.length()) custom.add(arr.getString(i))
            (defaults + custom).distinct()
        } catch (e: Exception) {
            defaults
        }
    }

    fun addSpecificMascotPhrase(mascotId: String, phrase: String) {
        val clean = phrase.trim()
        if (clean.isBlank()) return
        val baseId = mascotId.lowercase().trim()
        val raw = sp.getString(PREFIX_CUSTOM_MASCOT_PHRASES + baseId, null)
        val list = mutableListOf<String>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) list.add(arr.getString(i))
            } catch (e: Exception) {}
        }
        if (!list.contains(clean)) {
            list.add(clean)
            val arr = JSONArray()
            for (s in list) arr.put(s)
            sp.edit().putString(PREFIX_CUSTOM_MASCOT_PHRASES + baseId, arr.toString()).apply()
        }
    }

    fun removeSpecificMascotPhrase(mascotId: String, phrase: String) {
        val baseId = mascotId.lowercase().trim()
        val raw = sp.getString(PREFIX_CUSTOM_MASCOT_PHRASES + baseId, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.getString(i)
                if (s != phrase) list.add(s)
            }
            val newArr = JSONArray()
            for (s in list) newArr.put(s)
            sp.edit().putString(PREFIX_CUSTOM_MASCOT_PHRASES + baseId, newArr.toString()).apply()
        } catch (e: Exception) {}
    }

    fun getPhraseTranslation(englishPhrase: String, langCode: String): String {
        val target = langCode.lowercase().trim()
        if (target == "en" || target.isBlank()) return englishPhrase
        val raw = sp.getString(PREFIX_TRANSLATED_PHRASES + target, null)
        if (raw != null) {
            try {
                val obj = JSONObject(raw)
                val custom = obj.optString(englishPhrase, "")
                if (custom.isNotBlank()) return custom
            } catch (e: Exception) {}
        }
        if (target == "hinglish") {
            val h = HINGLISH_PHRASES_MAP[englishPhrase]
            if (!h.isNullOrBlank()) return h
        }
        if (target == "hi") {
            val h = HINDI_PHRASES_MAP[englishPhrase]
            if (!h.isNullOrBlank()) return h
        }
        return englishPhrase
    }

    fun setPhraseTranslation(englishPhrase: String, langCode: String, translation: String) {
        if (langCode == "en" || langCode.isBlank()) return
        val raw = sp.getString(PREFIX_TRANSLATED_PHRASES + langCode, "{}") ?: "{}"
        try {
            val obj = JSONObject(raw)
            if (translation.isBlank()) obj.remove(englishPhrase)
            else obj.put(englishPhrase, translation.trim())
            sp.edit().putString(PREFIX_TRANSLATED_PHRASES + langCode, obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRandomPhrase(mascotId: String?, langCode: String = "en"): String {
        val mid = mascotId ?: "owl-orbital"
        val specific = getSpecificMascotPhrases(mid)
        val general = getGeneralPhrases()
        val combined = if (specific.isNotEmpty() && (0..1).random() == 0) specific else general
        val chosen = combined.randomOrNull() ?: DEFAULT_GENERAL_PHRASES[0]
        return getPhraseTranslation(chosen, langCode)
    }

    fun exportPhrasesTemplate(langCode: String = "custom"): String {
        val sb = StringBuilder()
        sb.append("# ====================================================================\n")
        sb.append("# OPENASSIST MASCOT DIALOGUE & SPEECH TRANSLATION FILE\n")
        sb.append("# Language: $langCode\n")
        sb.append("# ====================================================================\n")
        sb.append("# Format: Line 1 = English Phrase, Line 2 = Translated Phrase, Line 3 = Blank\n")
        sb.append("# Leave line 2 blank to fallback to English.\n")
        sb.append("# ====================================================================\n\n")

        sb.append("# --- GENERAL MASCOT PHRASES (Spoken by all mascots) ---\n\n")
        for (phrase in getGeneralPhrases()) {
            sb.append(phrase).append("\n")
            sb.append(getPhraseTranslation(phrase, langCode).let { if (it == phrase) "" else it }).append("\n\n")
        }

        sb.append("# --- MASCOT SPECIFIC PHRASES ---\n\n")
        for ((mid, phrases) in DEFAULT_MASCOT_SPECIFIC) {
            sb.append("# Mascot: $mid\n")
            for (p in phrases) {
                sb.append(p).append("\n")
                sb.append(getPhraseTranslation(p, langCode).let { if (it == p) "" else it }).append("\n\n")
            }
        }
        return sb.toString()
    }

    fun importPhrasesText(content: String, targetLangCode: String): Int {
        val cleanLang = targetLangCode.lowercase().trim().replace(Regex("[^a-z0-9_-]"), "_")
        val lines = content.lines()
        val parsed = mutableMapOf<String, String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#") || line.isBlank()) {
                i++
                continue
            }
            val englishPhrase = line
            val transLine = if (i + 1 < lines.size) lines[i + 1].trim() else ""
            if (transLine.isNotBlank() && !transLine.startsWith("#")) {
                parsed[englishPhrase] = transLine
                i += 2
            } else {
                i += 1
            }
            while (i < lines.size && lines[i].trim().isBlank()) {
                i++
            }
        }
        for ((eng, trans) in parsed) {
            setPhraseTranslation(eng, cleanLang, trans)
        }
        return parsed.size
    }
}
