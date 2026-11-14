package com.freshcheck.app.ui.nutrition

data class NutrientValue(val name: String, val per100g: Double, val unit: String, val explanation: String)
data class NutritionProfile(
    val name: String, val category: String, val energyKcalPer100g: Double,
    val nutrients: List<NutrientValue>, val source: String, val isDemo: Boolean
)
fun scaledNutrient(valuePer100g: Double, grams: Int): Double {
    require(grams in 25..500)
    return valuePer100g * grams / 100.0
}
/** Synthetic numbers solely for UI testing. Replace with attributed, verified composition data.
 * No inference from camera output; nutrient values describe a serving basis, never a measured specimen.
 */
val DemoNutrition = NutritionProfile("Avocado", "Fruit", 150.0, listOf(
    NutrientValue("Protein", 2.0, "g", "Protein is one of the macronutrients shown in food composition tables."),
    NutrientValue("Carbohydrates", 9.0, "g", "Food composition sources may define total carbohydrate differently."),
    NutrientValue("Fat", 13.0, "g", "Fat is reported by mass here, not as a daily intake target."),
    NutrientValue("Dietary fibre", 6.0, "g", "Fibre may be included within total carbohydrate; do not add these figures together."),
    NutrientValue("Vitamin C", 10.0, "mg", "Vitamins are reported in source-specific units; milligrams differ from grams."),
    NutrientValue("Potassium", 400.0, "mg", "Mineral amounts vary by produce variety and preparation.")
), "FreshCheck synthetic UI fixture · not verified food-composition data", true)
