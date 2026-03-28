"""
TenantShield — Mock Server
===========================
Fake WebSocket + REST server so Android can develop without any
backend running. Returns realistic hardcoded data matching the
REAL demo building: 80 Woodruff Ave, Brooklyn (A&E Real Estate Holdings).

Exposes:
  WS  /ws/inspect   — mimics Orchestrator's Gemini Live relay
  WS  /              — alias on port 8765 (mock-only shorthand)
  POST /lookup       — mirrors data_agent.py contract
  GET  /health

Run:
  pip install fastapi uvicorn websockets
  uvicorn mock_server:app --reload --port 8766

Android connects to: ws://10.0.2.2:8766/ws/inspect
"""

import asyncio
import json
import uuid
from datetime import datetime
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional

app = FastAPI(title="TenantShield Mock Server", version="1.0.0")
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"],
)

# ─────────────────────────────────────────────
# Demo building — 80 Woodruff Ave, Brooklyn
# Named in NYC Public Advocate's Worst Landlord Watchlist.
# A&E Real Estate Holdings (Margaret Brunn + Donald Hastings)
# 313 open violations, 73 Class C, 5 failed rat inspections.
# ─────────────────────────────────────────────

DEMO_BUILDING = {
    "address": "80 Woodruff Ave, Brooklyn NY 11226",
    "bbl": "3049970045",
    "borough": "Brooklyn",
    "registration_id": "341022",
    "owner_name": "Margaret Brunn",
    "owner_phone": None,
    "corporation_name": "A&E Real Estate Holdings",
    "manager_name": "Donald Hastings",
    "manager_phone": None,
    "hpd_online_url": "https://hpdonline.nyc.gov/hpdonline/building/341022",
    "violation_count": 313,
    "open_violations": 313,
    "class_a_count": 104,
    "class_b_count": 136,
    "class_c_count": 73,
    "highest_class": "C",
    "days_neglected": 547,
    "oldest_complaint_date": "2024-09-28",
    "violations": [
        {
            "violation_id": "V-19042781",
            "class": "C",
            "description": "Water leak near electrical outlet in bathroom ceiling",
            "statute": "NYC HMC §27-2031",
            "status": "Open",
            "date": "2026-02-14",
            "apartment": "3B",
        },
        {
            "violation_id": "V-19041200",
            "class": "C",
            "description": "No heat — apartment measured at 48°F during heat season",
            "statute": "NYC HMC §27-2029",
            "status": "Open",
            "date": "2026-01-28",
            "apartment": "3B",
        },
        {
            "violation_id": "V-19040890",
            "class": "C",
            "description": "Peeling lead-based paint on window frame, child under 6 in unit",
            "statute": "NYC HMC §27-2056.4",
            "status": "Open",
            "date": "2026-01-15",
            "apartment": "2A",
        },
        {
            "violation_id": "V-19039700",
            "class": "C",
            "description": "Missing smoke detector in apartment — no operational device found",
            "statute": "NYC HMC §27-2046.1",
            "status": "Open",
            "date": "2025-12-20",
            "apartment": "5C",
        },
        {
            "violation_id": "V-19038100",
            "class": "C",
            "description": "Broken entrance door lock — door does not latch or secure",
            "statute": "NYC HMC §27-2043",
            "status": "Open",
            "date": "2025-12-01",
            "apartment": "1A",
        },
        {
            "violation_id": "V-19037500",
            "class": "B",
            "description": "Evidence of rat activity — droppings and gnaw marks in kitchen",
            "statute": "NYC HMC §27-2018",
            "status": "Open",
            "date": "2025-11-18",
            "apartment": "3B",
        },
        {
            "violation_id": "V-19036900",
            "class": "B",
            "description": "Mold growth on bathroom ceiling exceeding 10 sq ft from persistent leak",
            "statute": "NYC HMC §27-2017.1",
            "status": "Open",
            "date": "2025-11-02",
            "apartment": "3B",
        },
        {
            "violation_id": "V-19036300",
            "class": "B",
            "description": "Elevator out of service — building has 6 stories",
            "statute": "NYC HMC §27-989",
            "status": "Open",
            "date": "2025-10-20",
            "apartment": "BLDG",
        },
        {
            "violation_id": "V-19035700",
            "class": "B",
            "description": "Roach infestation in kitchen and bathroom — active colony",
            "statute": "NYC HMC §27-2018",
            "status": "Open",
            "date": "2025-10-05",
            "apartment": "4A",
        },
        {
            "violation_id": "V-19034500",
            "class": "A",
            "description": "Peeling paint on hallway ceiling, 3rd floor — non-lead context",
            "statute": "NYC HMC §27-2013",
            "status": "Open",
            "date": "2025-09-15",
            "apartment": "BLDG",
        },
    ],
    "complaints": [
        {
            "complaint_id": "C-8871200",
            "type": "EMERGENCY",
            "category": "PLUMBING",
            "status": "Open — no inspection scheduled",
            "open_date": "2024-09-28",
            "apartment": "3B",
        },
        {
            "complaint_id": "C-8871100",
            "type": "EMERGENCY",
            "category": "HEAT/HOT WATER",
            "status": "Open",
            "open_date": "2025-01-10",
            "apartment": "3B",
        },
        {
            "complaint_id": "C-8870900",
            "type": "NON EMERGENCY",
            "category": "PEST CONTROL",
            "status": "Open",
            "open_date": "2025-03-15",
            "apartment": "3B",
        },
        {
            "complaint_id": "C-8870700",
            "type": "EMERGENCY",
            "category": "LEAD",
            "status": "Open",
            "open_date": "2025-05-20",
            "apartment": "2A",
        },
        {
            "complaint_id": "C-8870500",
            "type": "NON EMERGENCY",
            "category": "ELEVATOR",
            "status": "Open",
            "open_date": "2025-07-02",
            "apartment": "BLDG",
        },
    ],
    "complaints_count": 47,
    "dob_complaints": [
        {
            "complaint_number": "DOB-320991234",
            "category": "ILLEGAL CONVERSION",
            "status": "ACTIVE",
            "date_entered": "2025-09-01",
        },
        {
            "complaint_number": "DOB-320990800",
            "category": "ELEVATOR",
            "status": "ACTIVE",
            "date_entered": "2025-06-15",
        },
        {
            "complaint_number": "DOB-320990400",
            "category": "CONSTRUCTION SAFETY",
            "status": "ACTIVE",
            "date_entered": "2025-03-10",
        },
    ],
    "dob_count": 3,
    "knowledge_graph": {
        "owner": "A&E Real Estate Holdings",
        "total_buildings": 60,
        "total_violations": 8947,
        "worst_building": {
            "bbl": "3049970045",
            "violation_count": 313,
            "class_c_count": 73,
        },
        "buildings": [
            {"bbl": "3049970045", "violation_count": 313, "class_c_count": 73},
            {"bbl": "3051230012", "violation_count": 287, "class_c_count": 61},
            {"bbl": "3042180034", "violation_count": 245, "class_c_count": 52},
            {"bbl": "3067450089", "violation_count": 198, "class_c_count": 38},
            {"bbl": "3038920023", "violation_count": 176, "class_c_count": 31},
        ],
    },
}

# ─────────────────────────────────────────────
# Demo complaint — matches the complaint JSON shape from spec
# ─────────────────────────────────────────────

DEMO_COMPLAINT = {
    "session_id": "mock-session-001",
    "timestamp": datetime.utcnow().isoformat() + "Z",
    "property": {
        "address": "80 Woodruff Ave, Brooklyn NY 11226",
        "apartment": "3B",
        "bbl": "3049970045",
        "registration_id": "341022",
    },
    "owner": {
        "owner_name": "Margaret Brunn",
        "corporation_name": "A&E Real Estate Holdings",
        "owner_phone": None,
        "manager_name": "Donald Hastings",
        "manager_phone": None,
    },
    "violation": {
        "hazard_type": "Water leak near electrical outlet",
        "hazard_class": "C",
        "class_label": "Immediately hazardous",
        "statute": "NYC HMC §27-2031",
        "statute_text": "No owner shall permit a condition hazardous to life or safety.",
        "evidence_description": "Visible water staining on ceiling within 12 inches of light fixture.",
        "correction_deadline": "24 hours",
    },
    "history": {
        "total_violations": 313,
        "open_violations": 313,
        "class_c_count": 73,
        "class_b_count": 136,
        "days_neglected": 547,
        "last_inspection": "2026-02-14",
    },
    "evidence": {
        "frame_urls": [],
        "video_url": None,
    },
    "actions": {
        "hpd_online_url": "https://hpdonline.nyc.gov/hpdonline/building/341022",
        "nyc311_url": "https://portal.311.nyc.gov",
    },
    "next_steps": [
        "HPD must respond to Class C violations within 24 hours.",
        "If no response in 24 hours, call HPD Emergency at 212-660-4800.",
        "Keep this complaint and your photos for housing court.",
        "Contact Met Council on Housing for free legal advice: 212-693-0000.",
    ],
}

# ─────────────────────────────────────────────
# REST: POST /lookup — mirrors data_agent.py
# ─────────────────────────────────────────────

class BuildingRequest(BaseModel):
    address: str
    borough: str
    apartment: Optional[str] = None

@app.post("/lookup")
async def lookup_building(req: BuildingRequest):
    return DEMO_BUILDING

# ─────────────────────────────────────────────
# WebSocket: /ws/inspect
# Mimics the Orchestrator's Gemini Live relay.
#
# Protocol (matches the spec exactly):
#   Android → Server:
#     {"type": "start", "language": "en", "session_id": "uuid"}
#     {"type": "frame", "data": "base64_jpeg"}
#     raw PCM bytes (ignored by mock)
#
#   Server → Android:
#     {"type": "text",      "content": "...agent speech..."}
#     {"type": "data",      "content": { ...building_json... }}
#     {"type": "hazard",    "hazard_class": "C", "hazard_type": "...",
#                           "statute": "...", "description": "..."}
#     {"type": "action",    "action": "show_emergency_panel", "reason": "..."}
#     {"type": "complaint", "content": { ...complaint_json... }}
#     {"type": "error",     "code": "...", "message": "..."}
# ─────────────────────────────────────────────

async def run_mock_inspection(ws: WebSocket):
    """
    Simulates the full inspection flow with realistic delays.
    Fires the same messages the real Orchestrator would, so
    Android can exercise every UI path.
    """
    # Step 1 — Greet
    await ws.send_json({
        "type": "text",
        "content": "Welcome to TenantShield. I'm your AI housing inspector. "
                   "What's your building address?",
    })
    await asyncio.sleep(1.0)

    # Step 2 — Pretend the tenant said "80 Woodruff Ave, Brooklyn"
    await ws.send_json({
        "type": "text",
        "content": "Let me pull up your building's records.",
    })
    await asyncio.sleep(2.0)

    # Step 3 — Building data (triggers OwnerRevealSheet)
    await ws.send_json({
        "type": "data",
        "content": DEMO_BUILDING,
    })
    await asyncio.sleep(1.5)

    # Step 4 — Dramatic owner reveal narration
    await ws.send_json({
        "type": "text",
        "content": "A&E Real Estate Holdings — owned by Margaret Brunn and "
                   "Donald Hastings — has 313 open violations on this building "
                   "alone, including 73 Class C immediately hazardous. Across "
                   "their 60 properties, they have nearly 9,000 open violations.",
    })
    await asyncio.sleep(3.0)

    # Step 5 — Prompt to start camera inspection
    await ws.send_json({
        "type": "text",
        "content": "Now let's inspect your unit. Point your camera at the hazard.",
    })
    await asyncio.sleep(5.0)

    # Step 6 — Simulated camera direction
    await ws.send_json({
        "type": "text",
        "content": "I see water staining near the ceiling. Pan left — I need to "
                   "see if it's near the electrical outlet.",
    })
    await asyncio.sleep(3.0)

    await ws.send_json({
        "type": "text",
        "content": "Hold still — assessing the proximity to the electrical fixture.",
    })
    await asyncio.sleep(2.0)

    # Step 7 — Hazard classification
    await ws.send_json({
        "type": "hazard",
        "hazard_class": "C",
        "hazard_type": "Water leak near electrical",
        "statute": "NYC HMC §27-2031",
        "description": "Water intrusion within 12 inches of any electrical component",
    })
    await asyncio.sleep(1.0)

    # Step 8 — Class C triggers emergency panel
    await ws.send_json({
        "type": "action",
        "action": "show_emergency_panel",
        "reason": "Class C immediately hazardous condition detected",
    })
    await asyncio.sleep(1.5)

    await ws.send_json({
        "type": "text",
        "content": "This is a Class C immediately hazardous violation — water "
                   "near electrical. I've shown emergency contacts. Generating "
                   "your complaint now.",
    })
    await asyncio.sleep(2.0)

    # Step 9 — Complaint ready
    complaint = dict(DEMO_COMPLAINT)
    complaint["session_id"] = str(uuid.uuid4())
    complaint["timestamp"] = datetime.utcnow().isoformat() + "Z"

    await ws.send_json({
        "type": "complaint",
        "content": complaint,
    })

    await ws.send_json({
        "type": "text",
        "content": "Your complaint has been generated with the official legal "
                   "citation NYC HMC §27-2031. Tap Download to save it.",
    })


@app.websocket("/ws/inspect")
async def websocket_inspect(websocket: WebSocket):
    await websocket.accept()
    try:
        # Wait for the "start" message from Android
        first_msg = await asyncio.wait_for(
            websocket.receive_text(), timeout=30.0
        )
        try:
            parsed = json.loads(first_msg)
            if parsed.get("type") == "start":
                session_id = parsed.get("session_id", str(uuid.uuid4()))
        except json.JSONDecodeError:
            pass

        # Run the scripted demo flow
        await run_mock_inspection(websocket)

        # After the demo flow, stay alive and ignore further messages
        # so Android doesn't get a disconnect during testing
        while True:
            try:
                await asyncio.wait_for(websocket.receive(), timeout=60.0)
            except asyncio.TimeoutError:
                # Send keep-alive
                await websocket.send_json({"type": "text", "content": ""})

    except WebSocketDisconnect:
        pass
    except Exception as e:
        print(f"[mock] WebSocket error: {e}")


# Also serve on bare "/" for convenience during early Android dev
@app.websocket("/")
async def websocket_root(websocket: WebSocket):
    await websocket_inspect(websocket)


@app.get("/health")
async def health():
    return {"status": "ok", "agent": "tenantshield-mock", "mode": "offline"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("mock_server:app", host="0.0.0.0", port=8766, reload=True)
