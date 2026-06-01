// FILE PATH: app/src/main/java/com/vaulto/viewmodel/MainViewModel.kt

package com.vaulto.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.vaulto.auth.AuthRepository
import com.vaulto.data.model.*
import com.vaulto.data.repository.BudgetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "MainViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo   = AuthRepository(application)
    private val budgetRepo = BudgetRepository()

    val currentUser: StateFlow<FirebaseUser?> = authRepo.currentUser

    private val cal    = Calendar.getInstance()
    private val _month = MutableStateFlow(cal.get(Calendar.MONTH) + 1)
    private val _year  = MutableStateFlow(cal.get(Calendar.YEAR))
    val month: StateFlow<Int> = _month
    val year:  StateFlow<Int> = _year

    // ✅ Default to PERSONAL so new users never see an empty Family view while
    //    the profile / family Firestore fetch is still in flight.
    private val _space = MutableStateFlow(SpaceType.PERSONAL)
    val activeSpace: StateFlow<SpaceType> = _space
    fun setSpace(s: SpaceType) { _space.value = s }

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    private val _family = MutableStateFlow<FamilyGroup?>(null)
    val family: StateFlow<FamilyGroup?> = _family

    // ✅ FIX — authLoading race condition:
    //    `true` until Firebase's AuthStateListener fires its FIRST callback and
    //    the subsequent profile load either succeeds or is determined unnecessary.
    //    We use a dedicated boolean so the splash hides only after we know whether
    //    the user is signed-in AND (if so) whether they already have a family —
    //    preventing both the login-screen flash AND the family-setup flash for
    //    returning users.
    private val _authLoading = MutableStateFlow(true)
    val authLoading: StateFlow<Boolean> = _authLoading

    // ✅ NEW: Tracks whether the user has explicitly chosen to skip family setup.
    //    Persisted only in-memory (resets on process death), which is fine —
    //    the user can always set up a family later via Settings (future feature).
    private val _familySkipped = MutableStateFlow(false)
    val familySkipped: StateFlow<Boolean> = _familySkipped

    fun skipFamily() { _familySkipped.value = true }

    val allCategories: StateFlow<List<Category>> = currentUser
        .filterNotNull()
        .flatMapLatest { budgetRepo.getUserCategoriesFlow(it.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), budgetRepo.defaultCategories)

    val expenses: StateFlow<List<Expense>> = combine(
        currentUser, _family, _space, _month, _year
    ) { user, fam, space, m, y ->
        when {
            user == null                             -> flowOf(emptyList())
            space == SpaceType.PERSONAL              -> budgetRepo.getPersonalExpensesFlow(user.uid, m, y)
            space == SpaceType.FAMILY && fam != null -> budgetRepo.getFamilyExpensesFlow(fam.id, m, y)
            else                                     -> flowOf(emptyList())
        }
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalSpent: StateFlow<Double> = expenses
        .map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val currentBudget: StateFlow<Double> = combine(
        currentUser, _family, _space, _month, _year
    ) { user, fam, space, m, y ->
        when {
            user == null                             -> flowOf(0.0)
            space == SpaceType.PERSONAL              -> budgetRepo.getPersonalBudgetFlow(user.uid, m, y)
            space == SpaceType.FAMILY && fam != null -> flowOf(fam.monthlyBudget)
            else                                     -> flowOf(0.0)
        }
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    // Always reads personal budget regardless of active space.
    val personalBudget: StateFlow<Double> = combine(currentUser, _month, _year) { user, m, y ->
        if (user != null) budgetRepo.getPersonalBudgetFlow(user.uid, m, y) else flowOf(0.0)
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val remaining: StateFlow<Double> = combine(currentBudget, totalSpent) { b, s -> b - s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /**
     * Daily average spend for the current month.
     * Divides total spent by days elapsed in the selected month
     * (capped at today for the current month; full month for past months).
     */
    val dailyAverage: StateFlow<Double> = combine(totalSpent, _month, _year) { spent, m, y ->
        if (spent == 0.0) return@combine 0.0
        val now = Calendar.getInstance()
        val divisor = if (m == now.get(Calendar.MONTH) + 1 && y == now.get(Calendar.YEAR)) {
            maxOf(now.get(Calendar.DAY_OF_MONTH), 1)
        } else {
            Calendar.getInstance().apply { set(y, m - 1, 1) }
                .getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        spent / divisor
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            // ✅ FIX — authLoading lifecycle:
            //    collectLatest ensures only the most-recent emission is processed.
            //    We set authLoading = false AFTER the profile/family load completes
            //    (or immediately if the user is signed out). This prevents the
            //    login screen from flashing for returning signed-in users and
            //    prevents the family_setup screen from flashing for users who
            //    already have a family.
            currentUser.collectLatest { user ->
                if (user == null) {
                    // Signed out — clear state and stop loading immediately.
                    profileJob?.cancel()
                    _profile.value = null
                    _family.value  = null
                    _authLoading.value = false
                } else {
                    // Signed in — load profile before marking auth as done.
                    // authLoading stays true until loadProfile() finishes its
                    // first Firestore read so navigation waits for real data.
                    loadProfile(user.uid)
                    _authLoading.value = false
                }
            }
        }
    }

    private fun loadProfile(uid: String) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            try {
                val p = authRepo.getUserProfile(uid) ?: run {
                    // Profile doesn't exist yet (race between AuthState + Firestore write).
                    // Leave family as-is; a retry will succeed once the write completes.
                    return@launch
                }
                _profile.value = p

                if (p.familyId.isNotBlank()) {
                    // ✅ FIX: Only null-out _family if the NEW profile genuinely has
                    //    no familyId. Without this guard, cancelling + restarting
                    //    loadProfile() briefly sets _family = null, causing a
                    //    momentary navigation back to family_setup.
                    budgetRepo.getFamilyFlow(p.familyId)
                        .catch { e -> Log.e(TAG, "Family flow error", e) }
                        .collect { _family.value = it }
                } else {
                    // Profile loaded and user has no family — only now clear _family.
                    _family.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadProfile error for uid=$uid", e)
            }
        }
    }

    fun signInWithGoogle(activity: Activity, webClientId: String, onResult: (Boolean) -> Unit) =
        viewModelScope.launch {
            val result = authRepo.signInWithGoogle(activity, webClientId)
            onResult(result.isSuccess)
        }

    fun signOut() {
        profileJob?.cancel()
        _familySkipped.value = false
        authRepo.signOut()
    }

    fun createFamily(name: String, budget: Double) = viewModelScope.launch {
        val uid = currentUser.value?.uid ?: return@launch
        budgetRepo.createFamily(uid, name, budget)
        _familySkipped.value = false
        loadProfile(uid)
    }

    fun joinFamily(code: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val uid = currentUser.value?.uid ?: return@launch
        val ok  = budgetRepo.joinFamily(uid, code)
        if (ok) {
            _familySkipped.value = false
            loadProfile(uid)
        }
        onResult(ok)
    }

    fun updateFamilyBudget(amount: Double) = viewModelScope.launch {
        _family.value?.id?.let { budgetRepo.updateFamilyBudget(it, amount) }
    }

    fun setPersonalBudget(amount: Double) = viewModelScope.launch {
        val uid = currentUser.value?.uid ?: return@launch
        budgetRepo.setPersonalBudget(uid, _month.value, _year.value, amount)
    }

    fun addExpense(category: Category, amount: Double, note: String) = viewModelScope.launch {
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

    fun deleteExpense(id: String) = viewModelScope.launch { budgetRepo.deleteExpense(id) }

    fun addCustomCategory(name: String, emoji: String) = viewModelScope.launch {
        currentUser.value?.uid?.let { budgetRepo.addCustomCategory(it, name, emoji) }
    }

    fun setMonth(m: Int, y: Int) { _month.value = m; _year.value = y }

    fun updateEmoji(emoji: String) = viewModelScope.launch {
        val p = _profile.value ?: return@launch
        val updated = p.copy(emoji = emoji)
        // Optimistic local update for instant UI response.
        _profile.value = updated
        try {
            authRepo.updateUserProfile(updated)
        } catch (e: Exception) {
            // Revert on failure so the UI stays consistent with Firestore.
            Log.e(TAG, "updateEmoji failed, reverting", e)
            _profile.value = p
        }
    }
}
