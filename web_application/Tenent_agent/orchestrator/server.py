"""
TenantShield — Orchestrator Server (Web Edition)
=================================================
Standard Gemini API (gemini-3-flash-preview) with function calling.
Serves the web frontend and handles WebSocket inspection sessions.

WebSocket path: /ws/inspect
Web UI:         /

Protocol — Client → Server (JSON text frames):
  {"type": "start", "language": "en"}
  {"type": "text",  "content": "80 Woodruff Ave, Brooklyn"}
  {"type": "frame", "data": "base64_jpeg_string"}
  {"type": "camera_toggle", "active": true}

Protocol — Server → Client (JSON text frames):
  {"type": "text",      "content": "...agent speech..."}
  {"type": "data",      "content": { ...building_json... }}
  {"type": "hazard",    ...}
  {"type": "action",    "action": "start_camera", ...}
  {"type": "complaint", "content": { ...complaint_json... }}
  {"type": "error",     "code": "...", "message": "..."}

Run:
  pip install -r requirements.txt
  GOOGLE_API_KEY=xxx uvicorn orchestrator.server:app --reload --port 8000
"""

import asyncio
import base64
import json
import logging
import os
import traceback
from pathlib import Path

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

from orchestrator.tools import TOOL_REGISTRY, TOOL_DECLARATIONS
from orchestrator.prompt import SYSTEM_PROMPT

# ─────────────────────────────────────────────
# Logging
# ─────────────────────────────────────────────

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("orchestrator")

# ─────────────────────────────────────────────
# Gemini configuration
# ─────────────────────────────────────────────

GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY", "")
GEMINI_MODEL = "gemini-3-flash-preview"

try:
    from google import genai
    from google.genai import types
    GENAI_AVAILABLE = True
except ImportError:
    GENAI_AVAILABLE = False
    logger.warning("google-genai not installed — pip install google-genai")

# ─────────────────────────────────────────────
# FastAPI app
# ─────────────────────────────────────────────

app = FastAPI(
    title="TenantShield Orchestrator",
    description="Gemini-powered housing inspection with web UI",
    version="2.0.0",
)
app.add_middleware(
    CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"],
)

STATIC_DIR = Path(__file__).parent / "static"

active_connections: set[WebSocket] = set()


# ─────────────────────────────────────────────
# Serve web frontend
# ─────────────────────────────────────────────

@app.get("/")
async def index():
    return FileResponse(STATIC_DIR / "index.html")


# Mount static AFTER the root route so "/" doesn't get captured
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


# ─────────────────────────────────────────────
# Helper: send JSON to client
# ─────────────────────────────────────────────

async def send_json(ws: WebSocket, payload: dict) -> bool:
    try:
        await ws.send_json(payload)
        return True
    except Exception:
        return False


# ─────────────────────────────────────────────
# Tool call dispatcher
# ─────────────────────────────────────────────

async def dispatch_tool_call(
    fn_name: str, fn_args: dict, ws: WebSocket, session_state: dict
) -> dict:
    """Execute a tool call and send side-effect messages to the client."""
    tool_entry = TOOL_REGISTRY.get(fn_name)
    if not tool_entry:
        return {"error": f"Unknown tool: {fn_name}"}

    # Inject stored data for draft_complaint — Gemini often sends incomplete args
    if fn_name == "draft_complaint":
        if session_state.get("building_data"):
            fn_args.setdefault("building_data", session_state["building_data"])
        if session_state.get("last_hazard"):
            lh = session_state["last_hazard"]
            fn_args.setdefault("hazard_type", lh.get("hazard_type", ""))
            fn_args.setdefault("hazard_class", lh.get("hazard_class", ""))
            fn_args.setdefault("statute", lh.get("statute", ""))
        logger.info(f"draft_complaint args: building_data={'yes' if fn_args.get('building_data') else 'MISSING'}, "
                     f"hazard_type={fn_args.get('hazard_type', 'MISSING')}, "
                     f"hazard_class={fn_args.get('hazard_class', 'MISSING')}, "
                     f"statute={fn_args.get('statute', 'MISSING')}")

    result = await tool_entry["fn"](**fn_args)

    # Error from downstream agent
    if isinstance(result, dict) and result.get("error"):
        await send_json(ws, {
            "type": "error",
            "code": fn_name.upper() + "_FAILED",
            "message": result["error"],
        })
        return result

    # Route side-effects to client
    if fn_name == "lookup_building":
        session_state["building_data"] = result
        await send_json(ws, {"type": "data", "content": result})
        await send_json(ws, {"type": "action", "action": "start_camera"})

    elif fn_name == "classify_hazard":
        session_state["last_hazard"] = result
        await send_json(ws, {
            "type": "hazard",
            "hazard_class": result.get("hazard_class", "B"),
            "hazard_type": result.get("hazard_type", ""),
            "statute": result.get("statute", ""),
            "description": result.get("statute_text", ""),
        })

    elif fn_name == "surface_emergency_contacts":
        await send_json(ws, {
            "type": "action",
            "action": result.get("action", "show_emergency_panel"),
            "reason": result.get("reason", ""),
        })

    elif fn_name == "draft_complaint":
        await send_json(ws, {"type": "complaint", "content": result})

    return result


# ─────────────────────────────────────────────
# Process Gemini responses (handle function calls)
# ─────────────────────────────────────────────

async def process_response(response, chat, ws, session_state: dict):
    """Handle a Gemini response — text or function calls (recursive)."""

    # Function calls → execute, send results back, process next response
    fn_calls = response.function_calls
    if fn_calls:
        func_parts = []
        for fc in fn_calls:
            fn_name = fc.name
            fn_args = dict(fc.args) if fc.args else {}
            logger.info(f"Tool call: {fn_name}({list(fn_args.keys())})")

            result = await dispatch_tool_call(fn_name, fn_args, ws, session_state)
            func_parts.append(
                types.Part.from_function_response(name=fn_name, response=result)
            )

        # Send all function results back to Gemini and process its next response
        next_response = await chat.send_message(func_parts)
        await process_response(next_response, chat, ws, session_state)
        return

    # Text response → send to client
    if response.text:
        text = response.text.strip()
        if text and "NO_NEW_HAZARD" not in text:
            await send_json(ws, {"type": "text", "content": text})


# ─────────────────────────────────────────────
# Gemini session handler
# ─────────────────────────────────────────────

async def run_session(ws: WebSocket, language: str = "en"):
    if not GENAI_AVAILABLE or not GOOGLE_API_KEY:
        await send_json(ws, {
            "type": "error",
            "code": "GEMINI_UNAVAILABLE",
            "message": "Gemini API not configured. Set GOOGLE_API_KEY env var.",
        })
        return

    client = genai.Client(api_key=GOOGLE_API_KEY)

    # Build tool declarations
    fn_decls = []
    for decl in TOOL_DECLARATIONS:
        try:
            fn_decls.append(types.FunctionDeclaration(**decl))
        except Exception as e:
            logger.warning(f"Skipping tool {decl.get('name')}: {e}")

    config = types.GenerateContentConfig(
        system_instruction=f"Language: {language}\n{SYSTEM_PROMPT}",
        tools=[types.Tool(function_declarations=fn_decls)],
    )

    chat = client.aio.chats.create(model=GEMINI_MODEL, config=config)
    chat_lock = asyncio.Lock()
    session_state = {"building_data": None}

    # ── Initial greeting ──
    try:
        async with chat_lock:
            response = await chat.send_message(
                "Session started. Greet the tenant and ask for their building address."
            )
            await process_response(response, chat, ws, session_state)
    except Exception as e:
        logger.error(f"Greeting error: {e}")
        await send_json(ws, {
            "type": "text",
            "content": "Welcome to TenantShield! Tell me your building address to get started.",
        })

    # ── State ──
    latest_frame: dict = {"data": None}
    camera_active: dict = {"on": False}

    # ── Background frame analysis ──
    async def auto_analyze():
        while True:
            await asyncio.sleep(8)
            if not camera_active["on"] or not latest_frame["data"]:
                continue
            frame_b64 = latest_frame["data"]
            latest_frame["data"] = None  # consume

            try:
                frame_bytes = base64.b64decode(frame_b64)
                parts = [
                    types.Part.from_bytes(data=frame_bytes, mime_type="image/jpeg"),
                    types.Part.from_text(
                        text=(
                            "Here is the latest camera frame from the tenant's apartment. "
                            "Analyze it for housing code violations. If you see a hazard, "
                            "describe it and call classify_hazard. If nothing notable, "
                            "respond with just NO_NEW_HAZARD."
                        )
                    ),
                ]
                async with chat_lock:
                    resp = await chat.send_message(parts)
                    await process_response(resp, chat, ws, session_state)
            except Exception as e:
                logger.error(f"Auto-analyze error: {e}")

    analyze_task = asyncio.create_task(auto_analyze())

    # ── Main receive loop ──
    try:
        while True:
            raw = await ws.receive_text()
            data = json.loads(raw)
            msg_type = data.get("type", "")

            if msg_type == "frame":
                latest_frame["data"] = data.get("data", "")
                camera_active["on"] = True

            elif msg_type == "text":
                content = data.get("content", "").strip()
                if not content:
                    continue

                parts = []

                # Attach latest frame if available
                if latest_frame["data"]:
                    try:
                        frame_bytes = base64.b64decode(latest_frame["data"])
                        parts.append(
                            types.Part.from_bytes(
                                data=frame_bytes, mime_type="image/jpeg"
                            )
                        )
                    except Exception:
                        pass
                    latest_frame["data"] = None

                parts.append(types.Part.from_text(text=content))

                try:
                    async with chat_lock:
                        resp = await chat.send_message(parts)
                        await process_response(resp, chat, ws, session_state)
                except Exception as e:
                    logger.error(f"Gemini error: {e}\n{traceback.format_exc()}")
                    await send_json(ws, {
                        "type": "error",
                        "code": "GEMINI_ERROR",
                        "message": str(e),
                    })

            elif msg_type == "camera_toggle":
                camera_active["on"] = data.get("active", False)
                if not camera_active["on"]:
                    latest_frame["data"] = None

    except WebSocketDisconnect:
        logger.info("Client disconnected")
    except Exception as e:
        logger.error(f"Session error: {e}\n{traceback.format_exc()}")
    finally:
        analyze_task.cancel()


# ─────────────────────────────────────────────
# WebSocket endpoint
# ─────────────────────────────────────────────

@app.websocket("/ws/inspect")
async def websocket_inspect(websocket: WebSocket):
    await websocket.accept()
    active_connections.add(websocket)
    logger.info(f"Client connected ({len(active_connections)} total)")

    language = "en"
    try:
        first_raw = await asyncio.wait_for(
            websocket.receive_text(), timeout=30.0
        )
        try:
            start_msg = json.loads(first_raw)
            if start_msg.get("type") == "start":
                language = start_msg.get("language", "en")
                logger.info(f"Session started, language={language}")
        except json.JSONDecodeError:
            pass

        await run_session(websocket, language)

    except asyncio.TimeoutError:
        await send_json(websocket, {
            "type": "error",
            "code": "START_TIMEOUT",
            "message": "No start message received within 30 seconds.",
        })
    except WebSocketDisconnect:
        logger.info("Client disconnected")
    except Exception as e:
        logger.error(f"WebSocket error: {e}\n{traceback.format_exc()}")
    finally:
        active_connections.discard(websocket)
        logger.info(f"Connection closed ({len(active_connections)} remaining)")


# ─────────────────────────────────────────────
# REST endpoints
# ─────────────────────────────────────────────

@app.get("/health")
async def health():
    return {
        "status": "ok",
        "agent": "tenantshield-orchestrator",
        "gemini_available": GENAI_AVAILABLE and bool(GOOGLE_API_KEY),
        "model": GEMINI_MODEL,
        "active_connections": len(active_connections),
    }


@app.get("/.well-known/agent.json")
async def agent_card():
    return {
        "name": "TenantShield Orchestrator",
        "description": "AI housing inspector — web UI + Gemini function calling",
        "version": "2.0.0",
        "endpoints": {
            "web": {"path": "/", "description": "Web UI"},
            "websocket": {"path": "/ws/inspect", "protocol": "WebSocket"},
        },
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("orchestrator.server:app", host="0.0.0.0", port=8000, reload=True)
