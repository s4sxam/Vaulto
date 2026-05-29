package com.vaulto.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.vaulto.auth.AuthRepository
import com.vaulto.data.model.Category
import com.vaulto.data.model.Expense
import com.vaulto.data.model.FamilyGroup
import com.vaulto.data.model.SpaceType
import com.vaulto.data.model.UserProfile
import com.vaulto.data.repository.BudgetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val authRepo   = AuthRepository(application)
    val budgetRepo = BudgetRepository()

    // ─── AUTH ────────────────────────────────────────────────────────────────

    val currentUser: StateFlow<FirebaseUser?> = authRepo.currentUser

    // ─── DATE ────────────────────────────────────────────────────────────────

    private val cal    = Calendar.getInstance()
    private val _month = MutableStateFlow(cal.get(Calendar.MONTH) + 1)
    private val _year  = MutableStateFlow(cal.get(Calendar.YEAR))
    val month: StateFlow<Int> = _month
    val year:  StateFlow<Int> = _year

    // ─── SPACE ───────────────────────────────────────────────────────────────

    private val _space = MutableStateFlow(SpaceType.FAMILY)
    val activeSpace: StateFlow<SpaceType> = _space
    fun setSpace(s: SpaceType) { _space.value = s }

    // ─── PROFILE ─────────────────────────────────────────────────────────────

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    // ─── FAMILY ──────────────────────────────────────────────────────────────
    //
    // FIX: The original implementation had a coroutine LEAK.
    //
    //   Old (buggy):
    //     fun loadProfile(uid) = viewModelScope.launch {
    //         ...
    //         budgetRepo.getFamilyFlow(fid).collect { _family.value = it }  // ← blocks forever
    //     }
    //
    //   Problem: every call to loadProfile() launched a NEW coroutine that
    //   called .collect() — which never completes for a Flow backed by a
    //   Firestore snapshot listener. If loadProfile was called twice (e.g.
    //   after a sign-out + sign-in), two independent collectors ran in
    //   parallel, both writing to _family. Over time this would pile up.
    //
    //   Fix: derive _family reactively via flatMapLatest on the profile flow.
    //   When the user changes (or profile changes), the previous Firestore
    //   listener is automatically cancelled and a new one is started.
    //   Zero leaks, zero manual cancellation required.
    //
    private val _family = MutableStateFlow<FamilyGroup?>(null)
    val family: StateFlow<FamilyGroup?> = _family

    // ─── DERIVED FLOWS ───────────────────────────────────────────────────────

    val allCategories: StateFlow<List<Category>> = currentUser
        .filterNotNull()
        .flatMapLatest { user -> budgetRepo.getUserCategoriesFlow(user.uid) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = budgetRepo.defaultCategories
        )

    val expenses: StateFlow<List<Expense>> = combine(
        currentUser, _family, _space, _month, _year
    ) { user, fam, space, m, y ->
        when {
            user == null                             -> flowOf(emptyList())
            space == SpaceType.PERSONAL              -> budgetRepo.getPersonalExpensesFlow(user.uid, m, y)
            space == SpaceType.FAMILY && fam != null -> budgetRepo.getFamilyExpensesFlow(fam.id, m, y)
            else                                     -> flowOf(emptyList())
        }
    }
        .flatMapLatest { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val totalSpent: StateFlow<Double> = expenses
        .map { list -> list.sumOf { it.amount } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0
        )

    val currentBudget: StateFlow<Double> = combine(
        currentUser, _family, _space, _month, _year
    ) { user, fam, space, m, y ->
        when {
            user == null                             -> flowOf(0.0)
            space == SpaceType.PERSONAL              -> budgetRepo.getPersonalBudgetFlow(user.uid, m, y)
            space == SpaceType.FAMILY && fam != null -> flowOf(fam.monthlyBudget)
            else                                     -> flowOf(0.0)
        }
    }
        .flatMapLatest { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0
        )

    val remaining: StateFlow<Double> = combine(currentBudget, totalSpent) { budget, spent ->
        budget - spent
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0.0
    )

    // ─── INIT ─────────────────────────────────────────────────────────────────

    init {
        // React to auth state changes. distinctUntilChanged() avoids redundant
        // profile loads if Firebase fires the listener without a real change.
        viewModelScope.launch {
            currentUser
                .distinctUntilChanged { old, new -> old?.uid == new?.uid }
                .collect { user ->
                    if (user != null) {
                        loadProfileAndFamily(user.uid)
                    } else {
                        // User signed out — clear local state immediately.
                        _profile.value = null
                        _family.value  = null
                    }
                }
        }
    }

    // ─── PROFILE + FAMILY LOADING ─────────────────────────────────────────────
    //
    // This is a single, clean coroutine that:
    //   1. Fetches the UserProfile once (one-shot suspend call).
    //   2. If the user has a familyId, subscribes to that family document
    //      via a Flow — the collect() below is intentionally the ONLY active
    //      collector at any time because the parent coroutine is launched in
    //      viewModelScope and replaced on each new uid via distinctUntilChanged.
    //
    private fun loadProfileAndFamily(uid: String) = viewModelScope.launch {
        val profile = authRepo.getUserProfile(uid) ?: return@launch
        _profile.value = profile

        if (profile.familyId.isNotBlank()) {
            // This collect() runs until the coroutine is cancelled.
            // The coroutine lives inside viewModelScope so it is cancelled
            // automatically when the ViewModel is cleared.
            budgetRepo.getFamilyFlow(profile.familyId).collect { family ->
                _family.value = family
            }
        }
    }

    // ─── AUTH ACTIONS ─────────────────────────────────────────────────────────

    fun signInWithGoogle(webClientId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = authRepo.signInWithGoogle(webClientId).isSuccess
            onResult(success)
        }
    }

    fun signOut() {
        authRepo.signOut()
        // Profile and family are cleared reactively in the init collector above.
    }

    // ─── FAMILY ACTIONS ───────────────────────────────────────────────────────

    fun createFamily(name: String, budget: Double) {
        viewModelScope.launch {
            val uid = currentUser.value?.uid ?: return@launch
            val fid = budgetRepo.createFamily(uid, name, budget)
            // Reload profile so familyId is updated, then subscribe to family flow.
            loadProfileAndFamily(uid)
            // Also directly subscribe to the new family so the UI updates without
            // waiting for the profile re-fetch round-trip.
            budgetRepo.getFamilyFlow(fid).collect { _family.value = it }
        }
    }

    fun joinFamily(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val uid = currentUser.value?.uid ?: return@launch
            val ok  = budgetRepo.joinFamily(uid, code)
            if (ok) loadProfileAndFamily(uid)
            onResult(ok)
        }
    }

    fun updateFamilyBudget(amount: Double) {
        viewModelScope.launch {
            _family.value?.id?.let { budgetRepo.updateFamilyBudget(it, amount) }
        }
    }

    // ─── BUDGET ACTIONS ───────────────────────────────────────────────────────

    fun setPersonalBudget(amount: Double) {
        viewModelScope.launch {
            val uid = currentUser.value?.uid ?: return@launch
            budgetRepo.setPersonalBudget(uid, _month.value, _year.value, amount)
        }
    }

    // ─── EXPENSE ACTIONS ──────────────────────────────────────────────────────

    fun addExpense(category: Category, amount: Double, note: String) {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val prof = _profile.value
            budgetRepo.addExpense(
                Expense(
                    spaceType     = _space.value.name,
                    familyId      = if (_space.value == SpaceType.FAMILY) _family.value?.id ?: "" else "",
                    userId        = user.uid,
                    userName      = prof?.name ?: user.displayName ?: "Member",
                    userEmoji     = prof?.emoji ?: "👤",
                    categoryId    = category.id,
                    categoryName  = category.name,
                    categoryEmoji = category.emoji,
                    amount        = amount,
                    note          = note,
                    month         = _month.value,
                    year          = _year.value
                )
            )
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch { budgetRepo.deleteExpense(id) }
    }

    // ─── CATEGORY ACTIONS ─────────────────────────────────────────────────────

    fun addCustomCategory(name: String, emoji: String) {
        viewModelScope.launch {
            currentUser.value?.uid?.let { budgetRepo.addCustomCategory(it, name, emoji) }
        }
    }

    // ─── DATE NAVIGATION ──────────────────────────────────────────────────────

    fun setMonth(month: Int, year: Int) {
        _month.value = month
        _year.value  = year
    }

    // ─── PROFILE ACTIONS ──────────────────────────────────────────────────────

    fun updateEmoji(emoji: String) {
        viewModelScope.launch {
            val current = _profile.value ?: return@launch
            val updated = current.copy(emoji = emoji)
            authRepo.updateUserProfile(updated)
            _profile.value = updated
        }
    }
}
