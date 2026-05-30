package com.vaulto.viewmodel

import android.app.Activity
import android.app.Application
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val authRepo   = AuthRepository(application)
    val budgetRepo = BudgetRepository()

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

    val allCategories: StateFlow<List<Category>> = currentUser
        .filterNotNull()
        .flatMapLatest { budgetRepo.getUserCategoriesFlow(it.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), budgetRepo.defaultCategories)

    val expenses: StateFlow<List<Expense>> = combine(
        currentUser, _family, _space, _month, _year
    ) { user, fam, space, m, y ->
        when {
            user == null                              -> flowOf(emptyList())
            space == SpaceType.PERSONAL               -> budgetRepo.getPersonalExpensesFlow(user.uid, m, y)
            space == SpaceType.FAMILY && fam != null  -> budgetRepo.getFamilyExpensesFlow(fam.id, m, y)
            else                                      -> flowOf(emptyList())
        }
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpent: StateFlow<Double> = expenses
        .map { list -> list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val currentBudget: StateFlow<Double> = combine(
        currentUser, _family, _space, _month, _year
    ) { user, fam, space, m, y ->
        when {
            user == null                              -> flowOf(0.0)
            space == SpaceType.PERSONAL               -> budgetRepo.getPersonalBudgetFlow(user.uid, m, y)
            space == SpaceType.FAMILY && fam != null  -> flowOf(fam.monthlyBudget)
            else                                      -> flowOf(0.0)
        }
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ✅ BUG 10 FIX: Separate personal budget flow that is independent of the
    //    active space. Settings screen must always show the real personal budget
    //    even when the user has Family space selected.
    val personalBudget: StateFlow<Double> = combine(currentUser, _month, _year) { user, m, y ->
        if (user != null) budgetRepo.getPersonalBudgetFlow(user.uid, m, y) else flowOf(0.0)
    }.flatMapLatest { it }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val remaining: StateFlow<Double> = combine(currentBudget, totalSpent) { b, s -> b - s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ✅ BUG 3 FIX: Track the profile-loading job so we can cancel it before
    //    starting a new one. The original code launched a new coroutine on every
    //    currentUser emission without cancelling the previous one, leading to
    //    multiple concurrent Firestore listeners on getFamilyFlow().
    private var profileJob: Job? = null

    init {
        viewModelScope.launch {
            // ✅ Use collectLatest so that if currentUser emits a new value before
            //    the previous block finishes, the previous block is cancelled.
            currentUser.collectLatest { user ->
                if (user != null) {
                    loadProfile(user.uid)
                } else {
                    // User signed out — clear local state.
                    _profile.value = null
                    _family.value  = null
                }
            }
        }
    }

    private fun loadProfile(uid: String) {
        // Cancel any in-flight profile/family listener before starting a new one.
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            val p = authRepo.getUserProfile(uid) ?: return@launch
            _profile.value = p
            if (p.familyId.isNotBlank()) {
                // collect (not collectLatest) here is fine — profileJob itself
                // is cancelled when a new profile load is triggered.
                budgetRepo.getFamilyFlow(p.familyId).collect { _family.value = it }
            }
        }
    }

    // ✅ BUG 1 FIX: Accept Activity so AuthRepository can pass it to
    //    CredentialManager.getCredential(). applicationContext caused the sign-in
    //    bottom-sheet to fail silently, producing "Sign-in failed".
    fun signInWithGoogle(activity: Activity, webClientId: String, onResult: (Boolean) -> Unit) =
        viewModelScope.launch {
            onResult(authRepo.signInWithGoogle(activity, webClientId).isSuccess)
        }

    // ✅ BUG 6: signOut() clears state here; navigation back to "login" is driven
    //    by the LaunchedEffect(currentUser) in VaultoApp (see MainActivity.kt).
    fun signOut() {
        profileJob?.cancel()
        authRepo.signOut()
        // _profile and _family are cleared by the collectLatest block above
        // when currentUser emits null after signOut().
    }

    fun createFamily(name: String, budget: Double) = viewModelScope.launch {
        val uid = currentUser.value?.uid ?: return@launch
        val fid = budgetRepo.createFamily(uid, name, budget)
        budgetRepo.getFamilyFlow(fid).collect { _family.value = it }
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
