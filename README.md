![Android](https://img.shields.io/badge/Platform-Android-green)




![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple)




![Firebase](https://img.shields.io/badge/Backend-Firebase-orange)




![License](https://img.shields.io/badge/License-MIT-blue)







# 💰 Vaulto V2 — Family + Personal Budget App

Real-time expense tracker with **Firebase** sync. Track family spending together and personal pocket money privately.

---

## ✨ Features

| Feature | Description |
|---|---|
| 👨‍👩‍👧‍👦 Family Space | Shared budget, all members see expenses in real time |
| 🔒 Personal Space | Private pocket money, only you can see |
| 🔑 Google Sign-In | Each member logs in with their Google account |
| ☁️ Firebase Sync | Data syncs instantly across all phones |
| 📊 Analytics | Category + member breakdown charts |
| 🏷️ Custom Categories | Add Momos, Samosa, anything! |
| 📅 Month Navigation | Browse any past month |

---

## 🚀 Firebase Setup (Required — 10 minutes)

### Step 1 — Create Firebase Project
1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **"Add project"** → name it `Vaulto` → Continue
3. Disable Google Analytics (optional) → **Create project**

### Step 2 — Add Android App
1. In Firebase Console → click the **Android icon** (</> button)
2. Package name: `com.vaulto`
3. App nickname: `Vaulto`
4. Click **Register app**
5. **Download `google-services.json`**
6. Place it in: `app/google-services.json` (in the `app/` folder)

### Step 3 — Enable Google Sign-In
1. Firebase Console → **Authentication** → **Sign-in method**
2. Click **Google** → Enable → **Save**
3. Copy the **Web client ID** shown there
4. Open `app/src/main/res/values/strings.xml`
5. Replace `YOUR_WEB_CLIENT_ID_HERE` with your actual Web client ID
6. Also replace `YOUR_WEB_CLIENT_ID_HERE` in `MainActivity.kt`

### Step 4 — Create Firestore Database
1. Firebase Console → **Firestore Database** → **Create database**
2. Choose **Start in test mode** (for development)
3. Pick any region → **Enable**

### Step 5 — Add Firestore Security Rules (for production)
In Firebase Console → Firestore → Rules, paste:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth.uid == userId;
      match /categories/{catId} {
        allow read, write: if request.auth.uid == userId;
      }
    }
    match /families/{familyId} {
      allow read: if request.auth.uid in resource.data.members;
      allow create: if request.auth != null;
      allow update: if request.auth.uid in resource.data.members;
    }
    match /expenses/{expenseId} {
      allow read, write: if request.auth != null &&
        (resource.data.userId == request.auth.uid ||
         request.auth.uid in get(/databases/$(database)/documents/families/$(resource.data.familyId)).data.members);
      allow create: if request.auth != null;
    }
    match /budgets/{budgetId} {
      allow read, write: if request.auth != null;
    }
  }
}
```

---

## ▶️ Run the App

1. Open in **Android Studio**
2. Place `google-services.json` in `app/` folder
3. Replace Web client ID in `strings.xml` and `MainActivity.kt`
4. Hit **Run ▶️**

---

## 📁 Project Structure

```
app/src/main/java/com/vaulto/
├── auth/
│   └── AuthRepository.kt       ← Google Sign-In + Firebase Auth
├── data/
│   ├── model/Models.kt         ← Data classes (UserProfile, Expense, etc.)
│   └── repository/
│       └── BudgetRepository.kt ← Firestore read/write
├── ui/
│   ├── screens/                ← Login, Home, AddExpense, Analytics, Settings
│   ├── components/Components.kt
│   └── theme/Theme.kt
├── viewmodel/MainViewModel.kt
└── MainActivity.kt
```

---

## 🔥 Firestore Data Structure

```
/users/{uid}
  name, email, emoji, familyId
  /categories/{catId}   ← custom categories

/families/{familyId}
  name, monthlyBudget, members[]

/expenses/{expenseId}
  spaceType, familyId, userId, amount, ...

/budgets/{uid_month_year}
  amount (personal monthly budget)
```

---

## 🤝 How Family Sharing Works

1. **One person** creates the family → gets a Family ID
2. Go to **Settings** → copy the Family ID
3. Share it (WhatsApp, message)
4. Other members → open app → **Join Family** → paste the ID
5. Everyone sees the same family expenses instantly ✅

---

## 📄 License
MIT — free to use and modify.
