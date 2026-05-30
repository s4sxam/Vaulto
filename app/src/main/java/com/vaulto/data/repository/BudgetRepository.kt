package com.vaulto.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.vaulto.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BudgetRepository {
    private val db = FirebaseFirestore.getInstance()

    // ─── DEFAULT CATEGORIES ────────────────────────────────────────────────
    val defaultCategories = listOf(
        Category("cat_1",  "Shopping",      "🛒",  "#FF6B6B", true),
        Category("cat_2",  "UPI Payment",   "📱",  "#4ECDC4", true),
        Category("cat_3",  "Food & Dining", "🍽️",  "#FFBE0B", true),
        Category("cat_4",  "Transport",     "🚗",  "#3A86FF", true),
        Category("cat_5",  "Medical",       "💊",  "#FF006E", true),
        Category("cat_6",  "Festival",      "🎉",  "#AB47BC", true),
        Category("cat_7",  "Vegetables",    "🥦",  "#78C552", true),
        Category("cat_8",  "Snacks",        "🥟",  "#FF9F43", true),
    )

    // ─── FAMILY GROUP ───────────────────────────────────────────────────────
    suspend fun createFamily(creatorUid: String, familyName: String, budget: Double): String {
        val familyId = UUID.randomUUID().toString()
        val family = FamilyGroup(
            id            = familyId,
            name          = familyName,
            createdBy     = creatorUid,
            members       = listOf(creatorUid),
            monthlyBudget = budget
        )
        db.collection("families").document(familyId).set(family).await()
        db.collection("users").document(creatorUid).update("familyId", familyId).await()
        return familyId
    }

    suspend fun joinFamily(uid: String, familyId: String): Boolean {
        val doc = db.collection("families").document(familyId).get().await()
        if (!doc.exists()) return false
        db.collection("families").document(familyId)
            .update("members", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
        db.collection("users").document(uid).update("familyId", familyId).await()
        return true
    }

    fun getFamilyFlow(familyId: String): Flow<FamilyGroup?> = callbackFlow {
        val listener = db.collection("families").document(familyId)
            .addSnapshotListener { snap, error ->
                // ✅ BUG 5 FIX: Don't silently ignore Firestore errors.
                if (error != null) {
                    close(error)   // propagate to the collector
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(FamilyGroup::class.java))
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateFamilyBudget(familyId: String, budget: Double) {
        db.collection("families").document(familyId).update("monthlyBudget", budget).await()
    }

    // ─── PERSONAL BUDGET ────────────────────────────────────────────────────
    suspend fun setPersonalBudget(uid: String, month: Int, year: Int, amount: Double) {
        val id = "${uid}_${month}_${year}"
        db.collection("budgets").document(id).set(
            Budget(id, SpaceType.PERSONAL.name, month, year, amount)
        ).await()
    }

    fun getPersonalBudgetFlow(uid: String, month: Int, year: Int): Flow<Double> = callbackFlow {
        val id = "${uid}_${month}_${year}"
        val listener = db.collection("budgets").document(id)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snap?.toObject(Budget::class.java)?.amount ?: 0.0)
            }
        awaitClose { listener.remove() }
    }

    // ─── CATEGORIES ─────────────────────────────────────────────────────────
    fun getUserCategoriesFlow(uid: String): Flow<List<Category>> = callbackFlow {
        val listener = db.collection("users").document(uid)
            .collection("categories")
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val custom = snap?.toObjects(Category::class.java) ?: emptyList()
                trySend(defaultCategories + custom)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addCustomCategory(uid: String, name: String, emoji: String) {
        val id = UUID.randomUUID().toString()
        db.collection("users").document(uid)
            .collection("categories")
            .document(id)
            .set(Category(id, name, emoji, "#78C552", false)).await()
    }

    // ─── EXPENSES ────────────────────────────────────────────────────────────
    suspend fun addExpense(expense: Expense) {
        val id    = UUID.randomUUID().toString()
        val final = expense.copy(id = id)
        db.collection("expenses").document(id).set(final).await()
    }

    suspend fun deleteExpense(id: String) {
        db.collection("expenses").document(id).delete().await()
    }

    // ✅ BUG 5 FIX: Propagate Firestore errors instead of silently swallowing them.
    //
    //    These queries use compound filters + orderBy("date"), which requires a
    //    Firestore composite index. Without the index the query fails silently
    //    (snap is null, _ was ignored). Now we surface the error so the UI can
    //    show a meaningful message.
    //
    //    Required indexes to create in Firebase Console → Firestore → Indexes:
    //      Collection: expenses
    //      Index 1: spaceType ASC, userId ASC, month ASC, year ASC, date DESC
    //      Index 2: spaceType ASC, familyId ASC, month ASC, year ASC, date DESC

    fun getPersonalExpensesFlow(uid: String, month: Int, year: Int): Flow<List<Expense>> =
        callbackFlow {
            val listener = db.collection("expenses")
                .whereEqualTo("spaceType", SpaceType.PERSONAL.name)
                .whereEqualTo("userId", uid)
                .whereEqualTo("month", month)
                .whereEqualTo("year", year)
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, error ->
                    if (error != null) { close(error); return@addSnapshotListener }
                    trySend(snap?.toObjects(Expense::class.java) ?: emptyList())
                }
            awaitClose { listener.remove() }
        }

    fun getFamilyExpensesFlow(familyId: String, month: Int, year: Int): Flow<List<Expense>> =
        callbackFlow {
            val listener = db.collection("expenses")
                .whereEqualTo("spaceType", SpaceType.FAMILY.name)
                .whereEqualTo("familyId", familyId)
                .whereEqualTo("month", month)
                .whereEqualTo("year", year)
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, error ->
                    if (error != null) { close(error); return@addSnapshotListener }
                    trySend(snap?.toObjects(Expense::class.java) ?: emptyList())
                }
            awaitClose { listener.remove() }
        }
}
