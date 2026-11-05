package com.swipesort.app

import java.util.Locale

/**
 * Conservative mapping of ML Kit's bundled, on-device image labels to virtual folders.
 * These labels describe objects, not the user's intent. Never use them for deletion.
 */
internal data class VisualLabel(val name: String, val confidence: Float)

internal fun detectVisualCategory(labels: List<VisualLabel>): String? {
    val known = mapOf(
        "Животные" to setOf("cat", "kitten", "dog", "puppy", "pet", "bird", "wildlife", "animal", "rabbit", "horse"),
        "Еда и напитки" to setOf("food", "dish", "meal", "cuisine", "dessert", "cake", "baking", "bread", "fruit", "vegetable", "drink", "beverage", "ice cream", "pizza", "sushi", "pastry"),
        "Красота и стиль" to setOf("fashion", "clothing", "dress", "outfit", "coat", "jacket", "shoe", "footwear", "handbag", "cosmetics", "makeup", "hairstyle", "perfume", "jewellery", "jewelry"),
        "Дом" to setOf("interior design", "furniture", "living room", "bedroom", "kitchen", "couch", "sofa", "home decor", "cabinet", "wardrobe", "dining room"),
        "Дети и игры" to setOf("toy", "doll", "cartoon", "comics", "playground", "teddy bear", "board game"),
        "Путешествия" to setOf("hotel", "airplane", "aircraft", "airport", "beach", "tourism", "vacation", "resort", "landmark", "sightseeing"),
        "Природа" to setOf("flower", "forest", "tree", "mountain", "waterfall", "landscape", "sunset", "ocean", "lake", "garden")
    )
    val scores = mutableMapOf<String, Float>()
    for (label in labels) {
        if (label.confidence < .75f) continue
        val normalized = label.name.lowercase(Locale.ROOT).trim()
        for ((category, terms) in known) {
            if (normalized in terms) {
                scores[category] = maxOf(scores[category] ?: 0f, label.confidence)
            }
        }
    }
    val ranked = scores.entries.sortedByDescending { it.value }
    if (ranked.isEmpty()) return null
    // Leave an ambiguous image uncategorized instead of guessing.
    if (ranked.size > 1 && ranked[0].value - ranked[1].value < .08f) return null
    return ranked.first().key
}
