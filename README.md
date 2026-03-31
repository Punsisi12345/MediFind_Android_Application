# MediFind: Full-Stack Healthcare & Pharmacy Solution

<p align="center">
  <img src="https://ibb.co/hJhvn3KR" width="128" alt="MediFind Logo" />
</p>

## 🚀 Experience it Live

* **Public Download (Android APK):** [Download Latest MediFind Release](https://github.com/Punsisi12345/MediFind_Android_Application/releases/latest)
* **Admin Web Dashboard:** [medifind-admin.web.app](YOUR_FIREBASE_HOSTING_URL) * **Backend API Status:** <img src="https://api.render.com/deploy/srv-YOUR_SERVICE_ID/status" alt="Render Build Status"> ---

## 📖 Introduction

**MediFind** is a comprehensive full-stack solution designed to bridge the gap between patients and local pharmacies. This repository contains the **Android Application** for end-users. 

The application utilizes a sophisticated **Hybrid Architecture**, leveraging **Firebase** for real-time data sync, authentication, and offline capabilities, alongside a secure, dedicated **Spring Boot API** (hosted on Render) for sensitive administrative tasks like managing system notices.

## 🏗️ Technical Architecture & Workflow

MediFind goes "way ahead" of a standard university project by implementing an industry-standard event-driven design.

<p align="center">
  <img src="https://i.imgur.com/your-architecture-diagram.png" width="80%"/> 
</p>

### Key Architectural Flows:

1.  **User Data Flow (Android <-> Firebase):** The Android app communicates *directly* with Firestore for common operations like reading the medicine catalog, managing the shopping cart, and viewing order history. This allows for real-time updates and seamless **offline support** handled entirely by the Firebase SDK.

2.  **Admin Data Flow (React Dash <-> Spring Boot <-> Firebase):** The Admin Dashboard (React) sends commands (e.g., creating a new promotion banner) to the Spring Boot API via securely routed **Axios** calls. The Spring Boot backend validates the request using secure environment credentials and then updates the master **Firestore database**.

3.  **Cross-Platform Retrofit Call (Android <-> Spring Boot):** To demonstrate secure RESTful API communication, the Android app uses **Retrofit** to fetch system-wide "Notices" (managed by the admin) directly from the Spring Boot server, ensuring they are always up-to-date and validated.

---

## 🛠️ Tech Stack (This Repository: Android App)

* **Language:** Java
* **Design Pattern:** MVVM (Model-View-ViewModel) with Livedata
* **Real-time Database:** Google Firestore (Firebase SDK)
* **Networking:** Retrofit 2 (Type-safe HTTP client for Java/Android)
* **UI Components:** RecyclerView (with custom adapters), Fragment navigation, material design
* **JSON Parsing:** GSON

## 🌐 The Full Ecosystem

This Android application is part of a larger, integrated system:

1.  **Backend API:** Java 21 / Spring Boot API (Deployed on Render via Docker).
2.  **Web Dashboard:** React / Vite PWA (Hosted on Firebase Hosting).
3.  **Infrastructure:** Firebase Firestore, Authentication, and Cloud Storage.

---

## 👨‍💻 Author

**Punsisi Upul Ranga**
* [GitHub Portal](https://github.com/Punsisi12345)
* [Connect on LinkedIn](YOUR_LINKEDIN_URL) ```

---

### How to get a real Architecture Diagram link:
If you want to include a diagram where `https://i.imgur.com/your-architecture-diagram.png` is right now, the easiest way is to draw a quick flowchart (using a free tool like Excalidraw or draw.io), take a screenshot, and drag that image directly into a GitHub issue comment box. GitHub will automatically upload it and generate a secure `https://user-images.githubusercontent.com/...` link. You can just copy that link and paste it into your `README.md`!

Would you like me to guide you on how to grab that "Live Status" badge from your Render dashboard so your GitHub page shows a cool green "Passing" icon when your Spring Boot server is online?
