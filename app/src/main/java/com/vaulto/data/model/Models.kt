package com.vaulto.data.model

// Firestore document models — all fields have defaults for Firestore deserialization

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val familyId: String = "",   // shared family group ID
    val emoji: String = "👤",
    val createdAt: Long = System.currentTimeMillis()
)

data class FamilyGroup(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val members: List<String> = emptyList(),   // list of uids
    val monthlyBudget: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

data class Category(
    val id: String = "",
    val name: String = "",
    val emoji: String = "",
    val colorHex: String = "#FF6B6B",
    val isDefault: Boolean = false
)

// SpaceType distinguishes family vs personal expenses
enum class SpaceType { FAMILY, PERSONAL }

data class Expense(
    val id: String = "",
    val spaceType: String = SpaceType.PERSONAL.name,  // stored as string in Firestore
    val familyId: String = "",      // filled only when spaceType == FAMILY
    val userId: String = "",        // who added it
    val userName: String = "",
    val userEmoji: String = "👤",
    val categoryId: String = "",
    val categoryName: String = "",
    val categoryEmoji: String = "💰",
    val amount: Double = 0.0,
    val note: String = "",
    val month: Int = 0,
    val year: Int = 0,
    val date: Long = System.currentTimeMillis()
)

data class Budget(
    val id: String = "",            // uid for personal, familyId for family
    val spaceType: String = SpaceType.PERSONAL.name,
    val month: Int = 0,
    val year: Int = 0,
    val amount: Double = 0.0
)
