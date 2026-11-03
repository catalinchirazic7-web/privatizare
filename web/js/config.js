// Configurarea Firebase: același proiect și aceeași bază de date ca aplicația Android
// (app/google-services.json și OnlineConfig.DATABASE_URL din Kotlin).
// Cheia API din Firebase nu e secretă: regulile bazei de date sunt cele care protejează datele.

export const FIREBASE_CONFIG = {
  apiKey: 'AIzaSyCGND396nEE-bNIxtdZ8S7pWyAhuxpV4oE',
  authDomain: 'privatizare.firebaseapp.com',
  databaseURL: 'https://privatizare-default-rtdb.europe-west1.firebasedatabase.app',
  projectId: 'privatizare',
  storageBucket: 'privatizare.firebasestorage.app',
  messagingSenderId: '106119323988',
  // appId-ul aplicației Android. Pentru RTDB nu e obligatoriu; o aplicație Web nouă din consolă ar avea alt appId.
  appId: '1:106119323988:android:8ca1538d15604942d768c1',
};

export const ONLINE_DATABASE_URL = FIREBASE_CONFIG.databaseURL;

/** Cât așteptăm răspunsul bazei de date înainte să afișăm o eroare de conectare (ms). */
export const CONNECT_TIMEOUT_MS = 10_000;

/** Donații (aceeași adresă ca în aplicația Android). */
export const DONATE = { url: 'https://revolut.me/catalidl7u', label: 'Donează' };
