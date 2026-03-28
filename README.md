# TenantShield

A building inspection and tenant complaint filing platform powered by multi-agent AI (A2A) using Gemini.

## Project Structure

```
TenantShield/
├── mobile_application/     # Android app (Kotlin + Java)
├── web_application/        # Web application
└── README.md
```

## Mobile Application

Android app built with Jetpack Compose, CameraX, and a multi-agent AI system:
- **Interacting Agent** — Voice-based user interaction via Gemini Live API
- **Inspection Agent** — Multimodal image analysis for building defects
- **Filing Agent** — Automated complaint form generation

Tech stack: Kotlin, Java, Jetpack Compose, CameraX, Firebase (Auth, Firestore, Storage), Gemini API, Vertex AI

See [mobile_application/](mobile_application/) for setup instructions.

## Web Application

See [web_application/](web_application/) for details.
