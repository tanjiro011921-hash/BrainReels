package com.example.brainreels.data

enum class AppGroup { INSTAGRAM, FACEBOOK }

/** Apps whose Reels can be tracked. Package names are the official Android package IDs. */
enum class SourceApp(val packageName: String, val label: String, val group: AppGroup) {
    INSTAGRAM("com.instagram.android", "Instagram", AppGroup.INSTAGRAM),
    FACEBOOK("com.facebook.katana", "Facebook", AppGroup.FACEBOOK),
    FACEBOOK_LITE("com.facebook.lite", "Facebook Lite", AppGroup.FACEBOOK);

    companion object {
        fun fromPackage(pkg: String?): SourceApp? = entries.firstOrNull { it.packageName == pkg }
        fun fromName(name: String?): SourceApp? = entries.firstOrNull { it.name == name }
    }
}

/** Ordered so that comparisons (>=) work: LOW < MEDIUM < HIGH. */
enum class Confidence { LOW, MEDIUM, HIGH }

enum class ConfidencePolicy(val label: String) {
    HIGH_ONLY("High confidence only (fewest false counts)"),
    MEDIUM_AND_ABOVE("Medium and high (fewer missed Reels)")
}

enum class EventStatus {
    /** Automatically detected and counted (delta = 1). */
    COUNTED,
    /** Observed but not counted because evidence was insufficient (delta = 0). */
    UNCERTAIN,
    /** A user correction; kept separate from automatic detections (delta may be negative). */
    MANUAL_ADJUSTMENT
}

object DetectionMethod {
    const val ACCESSIBILITY_HEURISTIC = "ACCESSIBILITY_HEURISTIC"
    const val MANUAL = "MANUAL"
}
