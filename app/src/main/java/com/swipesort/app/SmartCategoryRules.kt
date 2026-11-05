package com.swipesort.app

import java.util.Locale

/**
 * Offline fusion of OCR and ML Kit image labels. No remote calls.
 * Scores are heuristic, not calibrated probabilities.
 */
internal data class CategoryProposal(
    val category: String,
    val confidence: Float,
    val needsReview: Boolean,
    val explanation: String
)

private data class TopicRule(
    val category: String,
    val strong: List<String>,
    val medium: List<String> = emptyList(),
    val weak: List<String> = emptyList()
)

private val topicRules = listOf(
    TopicRule("Чеки и платежи",
        listOf("кассовый чек", "фискальн", "итого к оплате", "перевод выполнен", "оплата прошла", "сумма платежа"),
        listOf("чек", "квитанц", "списание", "перевод", "возврат средств", "оплачено")),
    TopicRule("Путешествия",
        listOf("бронирован", "посадочный талон", "авиабилет", "booking confirmation", "номер брони"),
        listOf("отель", "гостиниц", "аэропорт", "flight", "hotel", "курорт", "заезд", "вылет", "жильё"),
        listOf("поездк", "тур", "маршрут")),
    TopicRule("Рецепты",
        listOf("ингредиент", "способ приготовления", "рецепт", "выпекать при", "время приготовления"),
        listOf("разогрейте духовку", "добавьте муку", "замесите", "сковород", "тесто", "запекать", "варить", "котлет")),
    TopicRule("Здоровье",
        listOf("анализ крови", "ферритин", "гемоглобин", "узи", "медицинское заключение", "диагноз"),
        listOf("врач", "дозировка", "медицинск", "пациент", "лечение", "клиник", "симптом")),
    TopicRule("Работа и учёба",
        listOf("present simple", "present continuous", "домашнее задание", "контрольная работа", "worksheet", "grammar"),
        listOf("английский язык", "учебник", "упражнение", "exercise", "школа", "урок", "vocabulary", "education"),
        listOf("задание", "класс", "teacher")),
    TopicRule("Дети и воспитание",
        listOf("детский сад", "воспитание детей", "детская психология", "развитие ребёнка", "развитие ребенка"),
        listOf("ребёнок", "ребенок", "ребёнка", "ребенка", "детей", "дочка", "пятилет", "воспитател", "родител", "поведение детей"),
        listOf("мама", "дочь", "дети")),
    TopicRule("Дети и игры",
        listOf("настольная игра", "развивающая игра", "игрушки для детей", "детская раскраска"),
        listOf("игрушк", "раскраск", "пластилин", "мультфильм", "конструктор", "поделк", "кукла")),
    TopicRule("Красота и стиль",
        listOf("размер одежды", "таблица размеров одежды", "оттенок помады", "ноты аромата"),
        listOf("обувь", "платье", "парфюм", "косметик", "макияж", "стрижка", "брюки", "юбка", "сумка")),
    TopicRule("Дом",
        listOf("жесткость воды", "жёсткость воды", "посудомоечная машина", "посудомойка", "настройки соли", "планировка квартиры"),
        listOf("интерьер", "мебель", "уборка", "кухня", "ремонт", "инструкция по эксплуатации", "бытовая техника", "стирка")),
    TopicRule("Покупки",
        listOf("оформить заказ", "добавить в корзину", "wildberries", "ozon.ru", "ozon", "артикул товара"),
        listOf("продавец", "доставка", "купить", "в наличии", "заказать", "корзина товаров", "цена за шт"),
        listOf("руб.", "₽")),
    TopicRule("Цены и скидки",
        listOf("промокод", "купон на скидку", "распродажа", "скидка 50%"),
        listOf("скидка", "кешбэк", "cashback", "акция", "sale")),
    TopicRule("Переписки",
        listOf("написать сообщение", "пересланное сообщение", "ответить в чате", "whatsapp", "telegram"),
        listOf("сообщения", "сообщение", "переписка", "чат", "в сети", "был(а) в сети")),
    TopicRule("Документы",
        listOf("паспорт", "свидетельство о рождении", "договор", "страховой полис", "снилс"),
        listOf("справка", "инн", "реквизиты", "подпись", "заявление")),
    TopicRule("Полезные советы",
        listOf("пошаговая инструкция", "как правильно", "полезные советы", "инструкция по применению"),
        listOf("рекомендации", "шаг 1", "настройте", "проверьте", "необходимо сделать", "как сделать")),
    TopicRule("Мемы",
        listOf("анекдот", "мем", "юмористическая", "прикол"),
        listOf("шутка", "смешно", "юмор"))
)

private val imageTopics = mapOf(
    "Животные" to setOf("cat", "kitten", "dog", "puppy", "pet", "bird", "animal", "rabbit", "horse"),
    "Еда и напитки" to setOf("food", "dish", "meal", "cuisine", "dessert", "cake", "baking", "bread", "fruit", "vegetable", "drink", "beverage", "ice cream", "pizza", "sushi", "pastry"),
    "Красота и стиль" to setOf("fashion", "clothing", "dress", "outfit", "coat", "jacket", "shoe", "footwear", "handbag", "cosmetics", "makeup", "hairstyle", "perfume", "jewelry"),
    "Дом" to setOf("interior design", "furniture", "living room", "bedroom", "kitchen", "couch", "sofa", "home decor", "cabinet", "wardrobe", "dining room"),
    "Дети и игры" to setOf("toy", "doll", "cartoon", "comics", "playground", "teddy bear", "board game"),
    "Путешествия" to setOf("hotel", "airplane", "aircraft", "airport", "tourism", "vacation", "resort", "landmark", "sightseeing"),
    "Природа" to setOf("flower", "forest", "tree", "mountain", "waterfall", "landscape", "sunset", "ocean", "lake", "garden")
)

internal fun rankCategory(text: String, labels: List<VisualLabel>): CategoryProposal? {
    val normalized = text.lowercase(Locale.ROOT).replace('ё', 'е')
        .replace(Regex("\\s+"), " ")
    val score = mutableMapOf<String, Float>()
    val textScore = mutableMapOf<String, Float>()
    val visionScore = mutableMapOf<String, Float>()
    for (rule in topicRules) {
        // Weights make specific phrases more important than generic OCR words.
        val strong = rule.strong.count { normalized.contains(it.replace('ё', 'е')) }
        val medium = rule.medium.count { normalized.contains(it.replace('ё', 'е')) }
        val weak = rule.weak.count { normalized.contains(it.replace('ё', 'е')) }
        val points = strong * 5f + medium * 2.1f + weak * .6f
        if (points > 0f) textScore[rule.category] = points
    }
    for (label in labels) {
        if (label.confidence < .65f) continue
        val name = label.name.lowercase(Locale.ROOT).trim()
        for ((category, dictionary) in imageTopics) {
            if (name in dictionary) {
                val visual = 3.8f * label.confidence
                // Two different image labels can reinforce, but cannot dominate exact OCR evidence.
                visionScore[category] = (visionScore[category] ?: 0f) + visual
            }
        }
    }
    (textScore.keys + visionScore.keys).forEach { cat ->
        score[cat] = (textScore[cat] ?: 0f) + (visionScore[cat] ?: 0f).coerceAtMost(6.5f)
    }
    val ranked = score.entries.sortedByDescending { it.value }
    val best = ranked.firstOrNull() ?: return null
    val second = ranked.getOrNull(1)?.value ?: 0f
    val gap = best.value - second
    // Weak, generic text (e.g. "руб.") alone must never assign a folder.
    if (best.value < 2.45f || gap < .8f) return null
    val review = best.value < 5.0f || gap < 2.0f
    val confidence = when {
        !review -> .88f
        gap >= 1.2f -> .64f
        else -> .51f
    }
    val sources = listOfNotNull(
        if ((textScore[best.key] ?: 0f) > 0f) "текст" else null,
        if ((visionScore[best.key] ?: 0f) > 0f) "изображение" else null
    ).joinToString(" + ")
    return CategoryProposal(best.key, confidence, review, sources)
}
