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
import kotlinx.coroutines.CompletableDeferred
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

    // ✅ FIX — authLoading stays true until BOTH the AuthStateListener fires AND
    //    loadProfile() completes its first Firestore read. Previously authLoading
    //    was set to false immediately after launching loadProfile(), causing the
    //    nav decision tree to fire before family data arrived — routing users with
    //    a family to family_setup on every cold launch.
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
            currentUser.collectLatest { user ->
                if (user == null) {
                    profileJob?.cancel()
                    _profile.value = null
                    _family.value  = null
                    _authLoading.value = false
                } else {
                    // ✅ FIX: suspend until loadProfile() finishes its first Firestore
                    //    read before clearing authLoading. Previously loadProfile() was
                    //    launched-and-forgotten, so _authLoading became false while
                    //    _family was still null, sending returning users to family_setup.
                    loadProfile(user.uid)
                    // loadProfile() is now a suspend fun — this line runs AFTER it returns.
                    _authLoading.value = false
                }
            }
        }
    }

    private suspend fun loadProfile(uid: String) {
        profileJob?.cancel()

        // 🐛 ROOT CAUSE OF "can't log in" / infinite splash:
        //    getFamilyFlow() is a callbackFlow backed by addSnapshotListener — it
        //    NEVER completes on its own. The old code did
        //        profileJob = launch { ... .collect { ... } }
        //        profileJob?.join()
        //    For any user whose Firestore profile already has a non-blank
        //    familyId (i.e. anyone who has ever created/joined a family),
        //    .collect{} never returns, so the launch{} job never finishes,
        //    so join() suspends forever, so loadProfile() never returns, so
        //    _authLoading is NEVER set to false. The app sits on the
        //    CircularProgressIndicator splash screen forever — even though
        //    Firebase Auth itself signed the user in successfully.
        //
        // ✅ FIX: wait only for the FIRST emission (via CompletableDeferred),
        //    not for the whole job. The live listener keeps running in the
        //    background inside profileJob to keep _family updated in real
        //    time; loadProfile() returns as soon as we have an initial
        //    profile + family snapshot to make a navigation decision with.
        val initialLoadDone = CompletableDeferred<Unit>()

        profileJob = viewModelScope.launch {
            try {
                val p = authRepo.getUserProfile(uid)
                if (p == null) {
                    _profile.value = null
                    _family.value  = null
                    initialLoadDone.complete(Unit)
                    return@launch
                }
                _profile.value = p

                if (p.familyId.isNotBlank()) {
                    budgetRepo.getFamilyFlow(p.familyId)
                        .catch { e ->
                            Log.e(TAG, "Family flow error", e)
                            _family.value = null
                            initialLoadDone.complete(Unit) // unblock startup even on error
                        }
                        .collect { fam ->
                            _family.value = fam
                            // complete() is idempotent — only the first
                            // emission unblocks loadProfile(); later
                            // real-time updates just update _family.
                            initialLoadDone.complete(Unit)
                        }
                } else {
                    _family.value = null
                    initialLoadDone.complete(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadProfile error for uid=$uid", e)
                initialLoadDone.complete(Unit)
            }
        }

        // Wait for the initial profile + family snapshot (or failure) before
        // returning, so authLoading is cleared only after we have real
        // navigation data — WITHOUT waiting on the never-ending listener job.
        initialLoadDone.await()
    }

    /**
     * Returns via onResult:
     *   true  → signed in successfully
     *   false → real error (show "Sign-in failed" banner)
     *   null  → user cancelled the picker (do nothing, no banner)
     */
    fun signInWithGoogle(activity: Activity, webClientId: String, onResult: (Boolean?) -> Unit) =
        viewModelScope.launch {
            val result = authRepo.signInWithGoogle(activity, webClientId)
            when {
                result.isSuccess -> onResult(true)
                // GetCredentialCancellationException — user tapped Back, not an error.
                result.exceptionOrNull()?.javaClass?.simpleName
                    ?.contains("Cancellation") == true -> onResult(null)
                else -> {
                    Log.e(TAG, "signInWithGoogle failed", result.exceptionOrNull())
                    onResult(false)
                }
            }
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
