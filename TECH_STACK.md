# TenantShield — Google Cloud / Gemini Tech Stack

## Mobile Application Tech Stack

### Google Cloud & Gemini Technologies Used

| Technology | Product | Purpose | Model/Version |
|-----------|---------|---------|---------------|
| **Gemini REST API** | Google AI Studio | Text generation, multimodal image analysis, conversation | `gemini-3-flash-preview` |
| **Firebase Auth** | Firebase | Google Sign-In, user authentication | BOM 33.7.0 |
| **Cloud Firestore** | Firebase | User profiles, inspection records | BOM 33.7.0 |
| **Cloud Storage** | Firebase | Inspection photo uploads | BOM 33.7.0 |
| **Cloud Run** | Google Cloud | ADK proxy for Vertex AI (ADC auth) | Python 3.12 + FastAPI |
| **Agent Development Kit** | Google Cloud | Agent framework for conversation sessions | google-adk >= 0.3.0 |
| **Vertex AI** | Google Cloud | API gateway for Gemini via ADC | us-central1 |
| **Credential Manager** | Google Play Services | Google Sign-In | 1.3.0 |
| **Google Auth Library** | Google Cloud | OAuth2 token management | 1.23.0 |

### Mobile App Architecture Flow

```
┌─────────────────────────────────────────────────────────────┐
│                     ANDROID DEVICE                           │
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │   Home   │  │ Inspect  │  │ Owners   │  │ Reports  │   │
│  │  Screen  │  │  Screen  │  │  Screen  │  │  Screen  │   │
│  └────┬─────┘  └────┬─────┘  └──────────┘  └────┬─────┘   │
│       │              │                            │          │
│       │    ┌─────────▼──────────┐                │          │
│       │    │  InspectionViewModel │                │          │
│       │    │  (StateFlow bridge)  │                │          │
│       │    └─────────┬──────────┘                │          │
│       │              │                            │          │
│       │    ┌─────────▼──────────┐                │          │
│       │    │  AgentOrchestrator  │                │          │
│       │    │  (Java State Machine)│                │          │
│       │    └──┬──────┬──────┬───┘                │          │
│       │       │      │      │                     │          │
│  ┌────▼───┐ ┌▼────┐ ┌▼────┐ ┌▼─────┐            │          │
│  │Voice   │ │Inter│ │Insp │ │Filing│            │          │
│  │Service │ │Agent│ │Agent│ │Agent │            │          │
│  │STT/TTS │ └──┬──┘ └──┬──┘ └──┬───┘            │          │
│  └────────┘    │       │       │                 │          │
│                └───┬───┴───┬───┘                 │          │
│                    │       │                     │          │
│  ┌─────────────────▼───────▼─────────────────────▼────────┐ │
│  │              Firebase SDK Layer                         │ │
│  │  ┌──────────┐  ┌──────────────┐  ┌───────────────┐    │ │
│  │  │   Auth   │  │  Firestore   │  │ Cloud Storage │    │ │
│  │  │ (Google  │  │  (profiles,  │  │   (photos)    │    │ │
│  │  │ Sign-In) │  │ inspections) │  │               │    │ │
│  │  └──────────┘  └──────────────┘  └───────────────┘    │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    GOOGLE CLOUD                               │
│                                                               │
│  ┌─────────────────────┐    ┌──────────────────────────────┐ │
│  │  Google AI Studio    │    │  Cloud Run Proxy (ADK)       │ │
│  │  REST API            │    │  tenantshield-proxy          │ │
│  │                      │    │                              │ │
│  │  Endpoint:           │    │  Endpoints:                  │ │
│  │  generativelanguage  │    │  /session/start              │ │
│  │  .googleapis.com     │    │  /session/send               │ │
│  │  /v1beta             │    │  /analyze                    │ │
│  │                      │    │  /generate                   │ │
│  │  Auth: API Key       │    │  Auth: ADC (automatic)       │ │
│  └──────────┬───────────┘    └──────────────┬───────────────┘ │
│             │                               │                 │
│             └───────────┬───────────────────┘                 │
│                         ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │              Gemini 3 Flash Preview                       │ │
│  │                                                           │ │
│  │  Capabilities:                                            │ │
│  │  • Text generation (conversation, document drafting)      │ │
│  │  • Multimodal (image + text analysis)                     │ │
│  │  • JSON structured output                                 │ │
│  │  • Multi-turn conversation with history                   │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌──────────────────┐  ┌──────────────┐  ┌────────────────┐ │
│  │  Firebase Auth    │  │  Firestore   │  │ Cloud Storage  │ │
│  │  (managed)        │  │  (managed)   │  │  (managed)     │ │
│  └──────────────────┘  └──────────────┘  └────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### Mobile Pipeline Flow

```
User taps "Start Inspection"
         │
         ▼
┌─ COLLECTING_INFO ──────────────────────────────────────────┐
│  Interacting Agent ←→ Gemini REST API                      │
│  Multi-turn conversation (conversation history)            │
│  Collects: name, address, unit, complaint, inspection req  │
│  Voice: Android STT (input) + TTS (output)                 │
│  UI: Conversation screen with editable transcript          │
└────────────────────────────┬───────────────────────────────┘
                             │ All info collected
                             ▼
┌─ AWAITING_IMAGES ──────────────────────────────────────────┐
│  Camera View (CameraX)                                     │
│  User captures photos of apartment issues                  │
│  Multi-image capture + selection + EXIF correction          │
└────────────────────────────┬───────────────────────────────┘
                             │ Photos submitted
                             ▼
┌─ INSPECTING ───────────────────────────────────────────────┐
│  Inspection Agent → Gemini REST API (multimodal)           │
│  Images encoded as base64 JPEG (max 1024px)                │
│  System prompt: 16 HMC hazard types with NYC statutes      │
│  Output: hazard_level, findings[], recommended_actions[]    │
└────────────────────────────┬───────────────────────────────┘
                             │ Analysis complete
                             ▼
┌─ EXPLAINING_RESULTS ───────────────────────────────────────┐
│  Interacting Agent → Gemini REST API                       │
│  Explains findings to tenant in plain language via TTS     │
│  References Class A/B/C definitions and deadlines          │
└────────────────────────────┬───────────────────────────────┘
                             │ Explanation delivered
                             ▼
┌─ FILING ───────────────────────────────────────────────────┐
│  Filing Agent → Gemini REST API                            │
│  Generates formal NYC complaint form                       │
│  Includes: HMC statutes, hazard class, next steps          │
│  Output: ComplaintForm JSON                                │
└────────────────────────────┬───────────────────────────────┘
                             │ Complaint generated
                             ▼
┌─ COMPLETE ─────────────────────────────────────────────────┐
│  1. Save user profile → Firestore (users/{uid})            │
│  2. Upload photos → Cloud Storage (inspections/{uid}/...)  │
│  3. Save inspection record → Firestore (inspections/...)   │
│  4. Navigate to Reports screen                             │
└────────────────────────────────────────────────────────────┘
```

---

## Web Application Tech Stack

### Google Cloud & Gemini Technologies Used

| Technology | Product | Purpose | Model/Version |
|-----------|---------|---------|---------------|
| **Gemini API** | Google AI Studio | Orchestrator conversation + function calling + vision | `gemini-3-flash-preview` |
| **Gemini Live API** | Google AI Studio | Experimental real-time streaming (tests) | `gemini-3.1-flash-live-preview` |
| **Cloud Run** | Google Cloud | Deployment of 3 microservices | Python 3.11 + FastAPI |
| **google-genai SDK** | Google | Python client for Gemini | >= 1.14.0 |

### External (Non-Google) APIs

| API | Purpose |
|-----|---------|
| NYC Open Data (SODA) | Building violations, complaints, ownership records |
| NYC Geoclient | Address → BBL/BIN conversion |

### Web App Architecture Flow

```
┌──────────────────────────────────────────────────────────────┐
│                      WEB BROWSER                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  index.html + app.js + style.css                       │  │
│  │  Camera feed (WebRTC) + Chat interface                 │  │
│  └───────────────────────┬────────────────────────────────┘  │
└──────────────────────────┼───────────────────────────────────┘
                           │ WebSocket: /ws/inspect
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    GOOGLE CLOUD RUN                            │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  ORCHESTRATOR (port 8000)                               │  │
│  │  FastAPI + Uvicorn + WebSocket                          │  │
│  │                                                         │  │
│  │  ┌─────────────────────────────────────────────────┐   │  │
│  │  │  Gemini Chat Session                             │   │  │
│  │  │  Model: gemini-3-flash-preview                   │   │  │
│  │  │  Features:                                       │   │  │
│  │  │  • Multi-turn conversation                       │   │  │
│  │  │  • Function calling (4 tools)                    │   │  │
│  │  │  • Image analysis (camera frames as JPEG)        │   │  │
│  │  │  • System prompt: NYC inspector persona          │   │  │
│  │  └─────────────┬──────────┬──────────┬─────────────┘   │  │
│  │                │          │          │                   │  │
│  │    ┌───────────▼──┐  ┌───▼────┐  ┌──▼──────────────┐  │  │
│  │    │lookup_       │  │classify│  │draft_            │  │  │
│  │    │building()    │  │hazard()│  │complaint()       │  │  │
│  │    └───────┬──────┘  └───┬────┘  └──┬──────────────┘  │  │
│  │            │             │          │                   │  │
│  └────────────┼─────────────┼──────────┼──────────────────┘  │
│               │             │          │                      │
│  ┌────────────▼──────────┐  │  ┌───────▼───────────────────┐ │
│  │  DATA AGENT (8001)    │  │  │  COMPLAINT AGENT (8002)   │ │
│  │  FastAPI              │  │  │  FastAPI                   │ │
│  │                       │  │  │                            │ │
│  │  • NYC SODA API       │  └──│  • /classify               │ │
│  │    (7 datasets)       │     │    Keyword matching vs     │ │
│  │  • NYC Geoclient      │     │    hmc_citations.json      │ │
│  │    (address → BBL)    │     │    (16 hazard types)       │ │
│  │  • Owner lookup       │     │  • /draft                  │ │
│  │  • Violation history  │     │    Complaint document      │ │
│  │  • Knowledge graph    │     │    generation              │ │
│  └───────────────────────┘     └────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    EXTERNAL APIs                              │
│                                                               │
│  ┌─────────────────────────┐  ┌────────────────────────────┐ │
│  │  NYC Open Data (SODA)   │  │  NYC Geoclient API         │ │
│  │                          │  │                            │ │
│  │  Datasets:               │  │  address → BBL/BIN        │ │
│  │  • HPD Violations        │  │  (Borough-Block-Lot)      │ │
│  │  • HPD Complaints        │  │                            │ │
│  │  • DOB Complaints        │  │                            │ │
│  │  • HPD Registrations     │  │                            │ │
│  │  • ACRIS Property        │  │                            │ │
│  │  • ACRIS Legals          │  │                            │ │
│  │  • ACRIS Parties         │  │                            │ │
│  └─────────────────────────┘  └────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### Web App Pipeline Flow

```
User opens web app → WebSocket connects to /ws/inspect
         │
         ▼
┌─ GREETING ─────────────────────────────────────────────────┐
│  Gemini: "Hi! I'm TenantShield. What's your address?"     │
└────────────────────────────┬───────────────────────────────┘
                             │ User says address
                             ▼
┌─ BUILDING LOOKUP ──────────────────────────────────────────┐
│  Gemini calls: lookup_building(address, borough)           │
│    → Data Agent → NYC Geoclient (address → BBL)            │
│    → Data Agent → NYC SODA APIs (7 datasets)               │
│    → Returns: owner, violations, complaints, knowledge     │
│  Gemini summarizes: "Found X violations for this building" │
└────────────────────────────┬───────────────────────────────┘
                             │ "Show me the issue"
                             ▼
┌─ CAMERA INSPECTION ────────────────────────────────────────┐
│  User points camera → frames sent as JPEG via WebSocket    │
│  Gemini analyzes each frame for housing violations         │
│  "I see water staining on the ceiling..."                  │
│  Asks clarifying questions before classifying              │
└────────────────────────────┬───────────────────────────────┘
                             │ Hazard identified
                             ▼
┌─ HAZARD CLASSIFICATION ───────────────────────────────────┐
│  Gemini calls: classify_hazard(visual_description)         │
│    → Complaint Agent → keyword matching vs hmc_citations   │
│    → Returns: statute, class (A/B/C), deadline             │
│  If Class C → calls surface_emergency_contacts()           │
└────────────────────────────┬───────────────────────────────┘
                             │ Classification done
                             ▼
┌─ COMPLAINT DRAFTING ───────────────────────────────────────┐
│  Gemini calls: draft_complaint(building_data, hazard, ...) │
│    → Complaint Agent → generates formal complaint JSON     │
│    → Includes: property info, owner, statute, evidence     │
│  "Your complaint is ready with the legal citation."        │
└────────────────────────────────────────────────────────────┘
```

---

## Key Architectural Differences

| Aspect | Mobile App | Web App |
|--------|-----------|---------|
| **Gemini Model** | gemini-3-flash-preview | gemini-3-flash-preview |
| **API Access** | REST API (API key) + Cloud Run ADK proxy | REST API (API key) via google-genai SDK |
| **Function Calling** | No (agents handle tool logic in Java) | Yes (Gemini calls tools via function declarations) |
| **Agent Coordination** | Java state machine (AgentOrchestrator) | Gemini orchestrates via function calling |
| **Building Data** | Not integrated (placeholder) | NYC Open Data SODA API (7 datasets) |
| **Hazard Classification** | Gemini AI (prompt-based) | Deterministic keyword matching (hmc_citations.json) |
| **Communication** | HTTP REST (OkHttp) | WebSocket (real-time bidirectional) |
| **Image Input** | CameraX capture → base64 | WebRTC camera → JPEG frames via WebSocket |
| **Data Persistence** | Firebase (Firestore + Storage) | None (no database yet) |
| **Authentication** | Firebase Auth (Google Sign-In) | None |
| **Deployment** | Android APK + Cloud Run proxy | Cloud Run (3 microservices via Docker) |
| **Complaint Output** | On-device PDF generation | JSON complaint document |

---

## Shared Google Cloud Project

**Project ID:** `crucial-bucksaw-371623`
**Region:** `us-central1` (mobile) / `us-east1` (web, configurable)

### Services Under This Project
- Firebase Auth
- Cloud Firestore
- Cloud Storage
- Cloud Run (tenantshield-proxy)
- Vertex AI API
- Gemini API
- Artifact Registry
- Cloud Build
