# SentinelAI TAK Plugin

## Goal for Phase 8
Make the plugin production-ready with tests, cleanup, and documentation that guide CivTAK/ATAK users through configuration and mission analysis workflows.

## Configuration
1. Open the SentinelAI settings screen from the plugin menu.
2. Enter the SentinelAI backend URL (e.g., `https://10.0.2.2:8000` for local emulator access) and the shared API key issued by the backend operator.
3. Adjust the request timeout (default: 45 seconds) if the network link is slow.
4. Optionally enable debug logging for additional diagnostics.
5. Tap **Save**. The values are stored in SharedPreferences for reuse on next launch.
6. Use **Test Connection** (calls `/healthz`) to verify connectivity before sending mission analysis requests.

## Mission Analysis Usage
- Open the **Mission Analysis** panel from the toolbar or plugin menu.
- Provide a question or instruction for SentinelAI (e.g., "Summarize activity near the convoy").
- Choose which context to include:
  - **Selected markers**: attach map marker metadata and observations.
  - **Map extent**: include the current map view center and extent description.
  - **Mission notes**: append mission notes if available from TAK.
- Select a **time window** (start/end) relevant to the request.
- Submit to send a `MissionAnalysisRequest` to `/api/v1/analysis/mission`.
- The response displays the backend-selected intent, summary, risks, and recommendations.

## Marker Context-Menu Usage
- Long-press or select a marker on the map.
- Choose **Ask SentinelAI about this marker** from the context menu.
- Optionally refine the question and time window before submitting.
- The marker’s metadata is attached as a `MARKER_CONTEXT` signal in the mission analysis payload.

## Known Limitations
- TAK SDK integration points are placeholders; host platforms must wire plugin lifecycle hooks and register context menus.
- No offline queuing—requests require connectivity when submitted.
- The plugin depends on the SentinelAI backend for all analysis and intent selection; it does not perform on-device AI processing.
- Only HTTPS endpoints are supported; ensure certificates are trusted by the device.
