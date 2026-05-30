// FILE PATH: app/src/main/java/com/vaulto/viewmodel/MainViewModel.kt

package com.vaulto.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.google.firebase.auth.FirebaseUser
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

    // ✅ FIX: Repositories are private — UI must go through ViewModel methods only.
    //    Exposing repos directly breaks MVVM and lets UI bypass validation logic.
    private val authRepo   = AuthRepository(application)
    private val budgetRepo = BudgetRepository()

    val currentUser: StateFlow<FirebaseUser?> = authRepo.currentUser

    private val cal    = Calendar.getInstance()
    private val _month = MutableStateFlow(cal.get(Calendar.MONTH) + 1)
    private val _year  = MutableStateFlow(cal.get(Calendar.YEAR))
    val month: StateFlow<Int> = _month
    val year:  StateFlow<Int> = _year

    private val _space = MutableStateFlow(SpaceType.FAMILY)
    val activeSpace: StateFlow<SpaceType> = _space
    fun setSpace(s: SpaceType) { _space.value = s }

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile

    private val _family = MutableStateFlow<FamilyGroup?>(null)
    val family: StateFlow<FamilyGroup?> = _family

    // ✅ FIX: Separate loading state so VaultoApp can show a true splash screen
    //    until Firebase resolves auth — avoids the login-screen flash for users
    //    that are already signed in.
    private val _authLoading = MutableStateFlow(true)
    val authLoading: StateFlow<Boolean> = _authLoading

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

    // Independent personal budget — unaffected by the active space toggle.
    // SettingsScreen always reads real personal budget regardless of which
    // space (Family / Personal) is currently selected on HomeScreen.
    val personalBudget: StateFlow<Double> = combine(currentUser, _month, _year) { user, m, y ->
        if (user != null) budgetRepo.getPersonalBudgetFlow(user.uid, m, y) else flowOf(0.0)
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val remaining: StateFlow<Double> = combine(currentBudget, totalSpent) { b, s -> b - s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    // Tracks the active profile/family listener so we never have two running in parallel.
    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            // collectLatest cancels the previous block when a new user emission arrives,
            // preventing multiple concurrent Firestore listeners.
            currentUser.collectLatest { user ->
                _authLoading.value = false   // Firebase has resolved — safe to navigate.
                if (user != null) {
                    loadProfile(user.uid)
                } else {
                    // Signed out — wipe local state immediately.
                    _profile.value = null
                    _family.value  = null
                }
            }
        }
    }

    // ✅ FIX: loadProfile catches errors from the family flow so a Firestore
    //    outage doesn't silently kill profileJob and leave the UI stuck on
    //    FamilySetupScreen even though the user already has a family.
    private fun loadProfile(uid: String) {
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            try {
                val p = authRepo.getUserProfile(uid) ?: return@launch
                _profile.value = p
                if (p.familyId.isNotBlank()) {
                    budgetRepo.getFamilyFlow(p.familyId)
                        .catch { e -> Log.e(TAG, "Family flow error", e) }
                        .collect { _family.value = it }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadProfile error for uid=$uid", e)
            }
        }
    }

    fun signInWithGoogle(activity: Activity, webClientId: String, onResult: (Boolean) -> Unit) =
        viewModelScope.launch {
            onResult(authRepo.signInWithGoogle(activity, webClientId).isSuccess)
        }

    fun signOut() {
        profileJob?.cancel()
        authRepo.signOut()
        // _profile and _family are cleared by the collectLatest block when
        // currentUser emits null after signOut().
    }

    // ✅ FIX: createFamily cancels profileJob before collecting a new family
    //    flow to avoid two concurrent Firestore listeners on the same document.
    fun createFamily(name: String, budget: Double) = viewModelScope.launch {
        val uid = currentUser.value?.uid ?: return@launch
        val fid = budgetRepo.createFamily(uid, name, budget)
        // Reload the full profile — this will pick up the new familyId and
        // start a single, authoritative family flow listener.
        loadProfile(uid)
    }

    fun joinFamily(code: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val uid = currentUser.value?.uid ?: return@launch
        val ok  = budgetRepo.joinFamily(uid, code)
        if (ok) loadProfile(uid)
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
        authRepo.updateUserProfile(updated)
        _profile.value = updated
    }
}
