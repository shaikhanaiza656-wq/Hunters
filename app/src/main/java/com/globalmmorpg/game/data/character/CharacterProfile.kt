package com.globalmmorpg.game.data.character

enum class Gender { BOY, GIRL }

/**
 * Customization option index into an art-asset catalog (e.g. hairId = 2 means
 * "hair style #2"). The actual art assets (models/sprites) are supplied by the
 * art team and loaded by id — this class only holds the real selection state,
 * no placeholder/fake asset data.
 */
data class CharacterProfile(
    val uid: String = "",
    val gender: Gender = Gender.BOY,
    val hairId: Int = 0,
    val faceId: Int = 0,
    val eyesId: Int = 0,
    val heightCm: Int = 175,
    val bodyTypeId: Int = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "gender" to gender.name,
        "hairId" to hairId,
        "faceId" to faceId,
        "eyesId" to eyesId,
        "heightCm" to heightCm,
        "bodyTypeId" to bodyTypeId
    )

    companion object {
        fun fromMap(uid: String, map: Map<String, Any?>): CharacterProfile = CharacterProfile(
            uid = uid,
            gender = (map["gender"] as? String)?.let { Gender.valueOf(it) } ?: Gender.BOY,
            hairId = (map["hairId"] as? Long)?.toInt() ?: 0,
            faceId = (map["faceId"] as? Long)?.toInt() ?: 0,
            eyesId = (map["eyesId"] as? Long)?.toInt() ?: 0,
            heightCm = (map["heightCm"] as? Long)?.toInt() ?: 175,
            bodyTypeId = (map["bodyTypeId"] as? Long)?.toInt() ?: 0
        )
    }
}

// Customization ranges used by the UI (art team can extend these catalogs later)
object CharacterOptions {
    const val HAIR_COUNT = 8
    const val FACE_COUNT = 8
    const val EYES_COUNT = 6
    const val BODY_TYPE_COUNT = 4
    const val MIN_HEIGHT_CM = 150
    const val MAX_HEIGHT_CM = 200
}
