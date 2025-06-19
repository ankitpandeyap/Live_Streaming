# 🎥 LiveStream - Real-Time Streaming Platform

A live streaming platform built with **React**, **Spring Boot**, **Redis**, and **FFmpeg** in Docker. It features adaptive HLS streaming, private video recordings, a JWT-auth secured backend, real-time chat, follower notifications, and a modern dark-themed UI.

Think of it as a lightweight clone of Instagram Live — built for learning purposes.

---

## 🚀 Features

- 🔒 **JWT Authentication** — Secure login, registration, and protected APIs
- 🎥 **Live Streaming** — Stream from webcam using MediaRecorder API
- 🧩 **HLS Adaptive Streaming** — Live video is converted using FFmpeg (Docker)
- 💬 **Real-Time Chat** — Each stream has its own chat via WebSocket and Redis Pub/Sub
- 🧠 **Private Recordings** — Each user's stream is stored and accessible only by the owner
- 👥 **Follower System** — Follow/unfollow users with a dashboard view
- 🔔 **Notifications** — Get notified when someone you follow goes live
- 🌙 **Dark Modern UI** — Clean, responsive, and user-focused design

---

## 🧱 Tech Stack

| Layer         | Tech                        |
|--------------|-----------------------------|
| Frontend     | React, HLS.js, WebSocket     |
| Backend      | Spring Boot, JWT Auth, Redis |
| Streaming    | MediaRecorder, FFmpeg (Docker) |
| Messaging    | WebSocket + Redis Pub/Sub    |
| Video Format | HLS (HTTP Live Streaming)    |
| Containerization | Docker, Docker Compose   |

---

## 📁 Project Structure

 📦 livestream-app  
├── backend/              # Spring Boot API (JWT, WebSocket, Stream Management)  
├── frontend/             # React App (UI, Video Player, Chat)  
├── docker/  
│   ├── ffmpeg/           # FFmpeg service for HLS encoding  
│   └── redis/            # Redis for chat functionality and Pub/Sub  
├── recordings/           # User-specific directories for stored HLS recordings  
├── docker-compose.yml    # Docker Compose configuration for full-stack setup  
└── README.md             # Project documentation (you're reading it!)



---

## 🛠️ Setup Instructions

### 1. Clone the Repository

```
git clone https://github.com/yourusername/livestream-app.git
cd livestream-app
```

2. Run Redis & FFmpeg Services
```
docker-compose up -d
```
3. Backend (Spring Boot)
Set up .env or application.properties for JWT secret, Redis URL, etc.

Run backend app:
```
cd backend

./mvnw spring-boot:run
```
4. Frontend (React)
```
cd frontend
npm install
npm start
```

🧪 Key Components
MediaRecorder API – captures webcam data and streams via WebSocket

FFmpeg (Docker) – encodes WebM/raw chunks to .m3u8 (HLS)

WebSocket + Redis – enables real-time chat and stream push to followers

Spring Boot – handles auth, stream management, chat relay, and access control

React – dashboard, notifications, stream player modal, and chat UI

🔐 Security
All routes except /login and /register are secured with JWT

Access control enforced for private streams

User roles and ownership checks on backend

📸 Screenshots (Coming Soon)
Dashboard View

Stream Player Modal

My Recordings Page

Real-time Chat in Live View

📚 Learning Goals
Full-stack media streaming using browser and Docker tools

Adaptive video delivery with FFmpeg and HLS

Real-time WebSocket communication with Redis

Clean UI/UX with state management in React

Microservices-style architecture using Docker

📌 Roadmap
 Backend with JWT Auth and stream management

 MediaRecorder stream to WebSocket

 FFmpeg live HLS encoding (Docker)

 Real-time chat with Redis Pub/Sub

 Follow system and notifications

 Admin/moderation tools (optional)

 React Native app (future)

🧑‍💻 Author
Maintained by [Ankit Pandey].
Open to collaboration or feedback — this project is built for learning and growth.
