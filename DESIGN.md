# SentinelAI TAK Plugin – DESIGN


### Scope and Responsibilities

This document describes the *SentinelAI TAK plugin* that runs inside CivTAK on Android. All AI reasoning, OpenAI API calls, and external data ingest (ADS-B aircraft tracks, APRS packets, and weather) are performed by the **SentinelAI backend service** (a FastAPI application running on a server).

The TAK plugin itself **never** talks to OpenAI or external data providers directly. Instead, it:

- Collects mission context from TAK (map cursor, overlays, chat messages, user form fields).
- Sends that context to the SentinelAI backend `/api/v1/analysis/mission` endpoint.
- Renders the structured JSON response (intent, summary, risks, recommendations) and any overlay geometry returned by the backend.

In the rest of this document:

- **"TAK plugin" / "plugin"** always refers to the Android client extension running inside CivTAK.
- **"SentinelAI backend" / "analysis engine" / "backend service"** always refers to the remote SentinelAI FastAPI service.

Backend behavior is described only to clarify what the plugin depends on; it is implemented in the separate SentinelAI backend codebase.

## 1. Overview

The SentinelAI TAK plugin (for CivTAK/ATAK) provides an AI assistant directly inside the TAK client.
It lets users send structured **mission analysis requests** to the SentinelAI backend and receive
AI-generated assessments.

Key capabilities:

- Ask natural-language questions about missions, routes, and markers.
- Send selected map objects, mission metadata, and notes to the backend as a **single mission analysis payload**.
- Receive a classified **mission intent** plus summary, risks, and recommendations from the backend.
- See clearly which mission intent (e.g., Aircraft Activity Analysis, APRS Activity Analysis, Weather Impact)
  was selected by the AI for the current request.

The plugin is a standard TAK plugin packaged as an Android APK. It communicates with the SentinelAI backend via
HTTPS using a configurable hostname and API key. Mission analysis is performed via the backend’s
`/api/v1/analysis/mission` endpoint, which **combines intent classification and mission analysis in a single call**.

---

## 2. Goals and Non-Goals

### Goals

1. **Fit naturally into TAK workflows**
   - Context menu actions on markers (e.g., “Ask SentinelAI…”).
   - A dedicated panel for free-form mission questions and responses.
   - Minimal extra clicks.

2. **Minimal configuration burden**
   - Simple configuration screen for backend URL and shared API key.
   - Reasonable defaults to get started quickly.

3. **Safe and predictable behavior**
   - Clearly indicate which data (markers, notes, mission metadata) is being sent to the backend.
   - Fail gracefully when backend is unreachable.

4. **Leverage backend mission intents without exposing complexity**
   - Plugin sends a structured mission payload; the backend selects the best intent (e.g.:
     Situational Awareness, Route Risk Assessment, Weather Impact, Airspace Deconfliction,
     Aircraft Activity Analysis, Radio Signal Activity Analysis).
   - UI shows the selected intent label and the analysis but does not require the user to manage intents directly.

5. **Extensible UI**
   - Start with basic mission analysis and marker-focused queries; allow future expansion
     (alerts, mission timelines, richer visualization of AI output).

### Non-Goals

- The plugin will not store long-term data or act as a full note-taking system.
- No device-local AI models in v1.
- No complex multi-user identity mapping beyond what TAK already manages.
- The plugin will not perform its own intent classification; that is entirely handled by the backend.

---

## 3. User Experience

### 3.1 Core Interactions

1. **Mission Analysis Panel**

   - Accessible via a toolbar button or menu item (e.g., “SentinelAI Mission Analysis”).
   - Provides:
     - Text input for describing what the user wants analyzed (e.g., “Summarize activity near this route”,
       “List any low-altitude aircraft with abnormal patterns”, “Summarize recent APRS traffic near my team”). 
     - Optional toggles such as:
       - “Include selected markers”
       - “Include current map extent as mission location”
       - “Include mission notes (if available)"
     - Controls for selecting a **time window** (start / end) relative to “now” or using predefined windows
       (e.g., last 15/30/60 minutes).
     - A chat-style view of recent analyses (request + response summary).

   - When the user submits, the plugin builds a mission analysis payload and posts to `/api/v1/analysis/mission`.
     The backend returns:
     - `intent` (e.g., `AIR_ACTIVITY_ANALYSIS`)
     - `summary`
     - `risks` (list of strings)
     - `recommendations` (list of strings)

   - The panel displays:
     - Selected **intent label** (e.g., “Aircraft Activity Analysis”)
     - Summary text
     - Risks and recommendations as bullet lists.

2. **Context Menu on Map Objects**

   - When a user long-presses or selects a marker, a context menu item appears:
     - “Ask SentinelAI about this marker”
   - The plugin assembles marker metadata and uses it as a **signal** in the mission payload, for example:
     a `REQUEST_INFO` signal describing the marker and user’s question.
   - A small dialog lets the user refine the question and time window.
   - The request is sent through the same `/api/v1/analysis/mission` pipeline; the intent is selected by the backend.

3. **Quick Mission Summary**

   - A simple action such as “Summarize current mission” or “Quick situational snapshot”.
   - The plugin sends a generic `REQUEST_INFO` signal plus any available mission metadata and current map extent.
   - Backend will typically resolve this to `SITUATIONAL_AWARENESS`, but the plugin remains agnostic and just
     displays the returned intent label and analysis.

### 3.2 Configuration UI

- **Backend configuration screen**:
  - `Backend URL` (e.g., `https://sentinelai.ddnsfree.com` or a VPN-accessible address)
  - `API Key` (shared with backend)
  - Optional:
    - `Request Timeout` (seconds)
    - Debug logging toggle

- Validation:
  - “Test connection” button calls `/api/v1/ping` and displays the result (“OK” or error message).

---

## 4. Architecture

### 4.1 High-Level Components

- **Plugin Entry Point**
  - TAK plugin registration class that hooks into the TAK plugin infrastructure.
  - Registers UI components (panels, menu items, context menu actions).

- **UI Layer**
  - Fragments/activities for:
    - Mission Analysis panel
    - Configuration screen
  - Uses TAK’s UI integration patterns where possible.

- **Networking Layer**
  - Lightweight HTTP client (e.g., OkHttp) with:
    - Base URL from configuration.
    - `X-Sentinel-API-Key` header (or similar shared secret header).
    - JSON serialization (e.g., Moshi or Gson).
  - Main endpoint:
    - `POST /api/v1/analysis/mission` – combined intent classification + mission analysis.
    - `GET /api/v1/ping` – basic connectivity check.

- **Mission Context Builder**
  - Collects data from TAK APIs and UI selections:
    - Selected markers (coordinates, labels, types, attributes).
    - Optional mission metadata (if exposed by TAK environment).
    - The user’s natural-language request text.
    - Time window (start / end) and location (lat/lon + description) derived from current map or marker.
  - Produces the `mission`, `signals`, `notes`, `location`, `time_window`, and `client_metadata` objects expected
    by `/api/v1/analysis/mission`.
  - The builder **does not assign mission intents**; it only structures the mission context.

- **Persistence**
  - Small local storage:
    - Backend URL and API key.
    - Optional user preferences (e.g., default time window, whether to include selected markers by default).
  - Prefer TAK-approved mechanisms for preferences (e.g., SharedPreferences or TAK plugin-specific storage helpers).

### 4.2 Data Flow: “Ask about marker”

1. User long-presses a marker and selects “Ask SentinelAI about this marker…”.
2. Plugin queries TAK APIs to obtain marker details.
3. Plugin presents a small dialog:
   - Request text (pre-filled, editable).
   - Time window selector.
4. Plugin constructs mission analysis request:

   ```json
   {
     "mission_id": "tak-mission-or-session-id-if-available",
     "mission_metadata": {},
     "signals": [
       {
         "type": "REQUEST_INFO",
         "description": "User question about selected marker(s) and mission context.",
         "timestamp": "2025-12-11T04:57:00Z",
         "metadata": {
           "selected_markers": [
             {
               "uid": "MARKER-123",
               "type": "friendly_unit",
               "lat": 43.615,
               "lon": -116.201,
               "elevation_m": 850,
               "label": "Alpha Squad"
             }
           ]
         }
       }
     ],
     "notes": "Optional additional notes or mission comments from the user.",
     "location": {
       "latitude": 43.615,
       "longitude": -116.201,
       "description": "Current map center / marker location"
     },
     "time_window": {
       "start": "2025-12-11T04:42:00Z",
       "end": "2025-12-11T04:57:00Z"
     },
     "client_metadata": {
       "tak_device_id": "<device id or alias>",
       "plugin_version": "<version>",
       "platform": "CivTAK-Android"
     }
   }
   ```

5. Plugin sends `POST /api/v1/analysis/mission` with this JSON body.
6. Backend:
   - Ingests any relevant sensor feeds (ADS-B, APRS, weather, etc.) based on location/time window.
   - Runs **intent classification + mission analysis** in one OpenAI call.
   - Returns `intent`, `summary`, `risks`, `recommendations`.
7. Plugin displays:
   - Intent label (e.g., “Aircraft Activity Analysis”).
   - Summary paragraph.
   - Risks and recommendations as bullet lists.

### 4.3 Data Flow: Free-form mission question

1. User opens the Mission Analysis panel and types a question like:
   - “Summarize recent low-altitude aircraft activity near my current location.”
2. User chooses options:
   - Time window (e.g., last 20 minutes).
   - Whether to include selected markers or notes.
3. Plugin builds a mission analysis payload (same shape as above but without marker metadata if none selected).
4. Plugin sends `POST /api/v1/analysis/mission`.
5. Backend classifies intent, performs analysis, and returns results.
6. Plugin appends the result to the panel’s chat/history view.

---

## 5. Request and Response Contracts

The plugin targets the backend’s **mission analysis** contract, not the lower-level chat contract.

### 5.1 Mission Analysis Request (from plugin)

The plugin sends a subset of the full backend mission-analysis schema; server-side ingestors enrich
it with additional data (ADS-B, APRS, weather) as needed.

```json
{
  "mission_id": "string (optional, if available from TAK)",
  "mission_metadata": {},
  "signals": [
    {
      "type": "REQUEST_INFO",
      "description": "Natural-language description of what the user wants analyzed.",
      "timestamp": "ISO 8601 UTC string",
      "metadata": {}
    }
  ],
  "notes": "Optional free-form notes or concatenated mission notes.",
  "location": {
    "latitude": 43.565,
    "longitude": -116.223,
    "description": "Boise Airport area (for example)"
  },
  "time_window": {
    "start": "ISO 8601 UTC string",
    "end": "ISO 8601 UTC string"
  },
  "client_metadata": {
    "tak_device_id": "Some ID or alias",
    "plugin_version": "0.1.0",
    "platform": "CivTAK-Android"
  }
}
```

Fields like `weather_snapshot`, `air_traffic_tracks`, `air_traffic_summary`, `aprs_messages`, and
`aprs_summary` are **populated by the backend** and do not need to be supplied by the plugin.

### 5.2 Mission Analysis Response (to plugin)

The plugin expects the simplified mission analysis output:

```json
{
  "intent": "AIR_ACTIVITY_ANALYSIS",
  "summary": "Concise mission summary.",
  "risks": [
    "Risk item 1",
    "Risk item 2"
  ],
  "recommendations": [
    "Recommendation 1",
    "Recommendation 2"
  ]
}
```

- `intent` is a machine-readable intent identifier (e.g. `SITUATIONAL_AWARENESS`, `AIR_ACTIVITY_ANALYSIS`,
  `RADIO_SIGNAL_ACTIVITY_ANALYSIS`, etc.).
- The plugin maps `intent` to a human-readable label (if needed) for display.
- `summary`, `risks`, and `recommendations` are directly rendered in the UI.

The plugin does **not** need to handle raw OpenAI responses; the backend is responsible for enforcing JSON format
and stripping any extraneous content (markdown fences, etc.).

---

## 6. Error Handling and Resilience

### 6.1 Network Errors

- If the backend is unreachable:
  - Show a clear message: “SentinelAI backend not reachable.”
  - Provide a “Retry” button and a shortcut to open the configuration screen.
  - Offer a “Test connection” action that hits `/api/v1/ping`.

### 6.2 Backend Errors

- If the backend returns an error payload or a non-200 status:
  - Display a short, user-friendly message extracted from the backend’s error response (if available).
  - Include basic diagnostic info in logs (HTTP status, error code).

### 6.3 Timeouts

- Configurable mission-analysis timeout (default e.g. 45 seconds; aligned with backend settings).
- On timeout:
  - Display “The mission analysis request took too long; please try again or narrow the time window.”

---

## 7. Security and Privacy

- Use HTTPS-only connections to the backend.
- Do not log sensitive content (full prompts, precise unit locations) in plaintext on the device.
  - Limit logging to high-level events (request started/finished, status codes).
- API key is stored securely in TAK-approved configuration storage.
- UI clearly indicates when data is being sent off-device to SentinelAI.

---

## 8. Development and Build

### 8.1 Project Structure (high-level)

```text
sentinel-tak-plugin/
  README.md
  DESIGN.md
  app/
    src/
      main/
        AndroidManifest.xml
        java/
          ... plugin entry points, UI, networking, context builder ...
        res/
          layout/
          values/
  gradle/
  build.gradle
  settings.gradle
```

- Built with Gradle as a standard Android project.
- Includes TAK plugin dependencies as provided by the CivTAK/ATAK SDK.

### 8.2 Configuration for Builds

- Build flavors (optional):
  - `dev`: default backend URL can be `https://10.0.2.2:8000` (for local emulator connecting to a host machine backend).
  - `prod`: backend URL from configuration; no hard-coded production endpoints.

- API key:
  - Never hard-code real production keys.
  - For development, a non-sensitive key can be embedded or loaded from a local config file, with clear separation
    from production configuration.

---

## 9. Testing

### 9.1 Unit Tests

- Mission context builder:
  - Given combinations of selected markers, user-entered text, and time window, verify the constructed JSON payload
    matches the `/api/v1/analysis/mission` schema.
- Configuration storage:
  - Verify save/load behavior for backend URL, API key, and timeout preferences.

### 9.2 Integration Tests (Manual / Emulator + Backend)

- Start local backend with the mission-analysis pipeline enabled.
- Run plugin in emulator or physical device.
- Exercise:
  - Ping backend from configuration screen.
  - Submit free-form mission analysis requests.
  - Use marker context menu to request analysis.
- Validate via backend logs:
  - Requests match expected JSON structure.
  - Backend successfully returns intent, summary, risks, and recommendations.
  - UI displays selected intent label and content correctly.

---

## 10. Future Enhancements

- **Intent-aware UI hints (read-only)**:
  - Color or icon variations based on returned intent (e.g., weather vs airspace vs radio signals).
- **Inline map annotations**:
  - When analysis references specific locations or aircraft, offer to create new markers from the response.
- **Mission timeline view**:
  - Show a chronological history of mission analyses for situational review.
- **Offline queuing (opt-in)**:
  - Queue mission analysis requests when offline and send when connectivity resumes.
- **Telemetry (opt-in)**:
  - Plugin usage metrics (without sensitive mission content) to help tune backend capacity and prompt design.
