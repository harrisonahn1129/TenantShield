"""
TenantShield — Orchestrator Tools (ADK-style)
===============================================
Four tool functions registered with the Gemini Live session.
Each is an async callable that the Orchestrator invokes when
Gemini emits a function_call.

Tool routing:
  lookup_building        → POST data_agent/lookup
  classify_hazard        → POST complaint_agent/classify
  surface_emergency_contacts → returns action payload (no HTTP call)
  draft_complaint        → POST complaint_agent/draft

All tools return dicts. Errors return {"error": "..."} so the
Orchestrator can relay them as {"type": "error"} to Android.
"""

import os
import httpx

DATA_AGENT_URL = os.getenv("DATA_AGENT_URL", "http://localhost:8001")
COMPLAINT_AGENT_URL = os.getenv("COMPLAINT_AGENT_URL", "http://localhost:8002")

# Generous timeout — data agent makes 5-6 sequential NYC API calls
TIMEOUT = 30.0


async def lookup_building(address: str, borough: str) -> dict:
    """
    Call this immediately when the tenant says their building address.
    Returns owner name, LLC, violation history, knowledge graph.
    Never wait — call this the moment any address is mentioned.
    """
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            resp = await client.post(
                f"{DATA_AGENT_URL}/lookup",
                json={"address": address, "borough": borough},
            )
            resp.raise_for_status()
            return resp.json()
    except httpx.TimeoutException:
        return {"error": "Building lookup timed out — NYC servers may be slow."}
    except httpx.ConnectError:
        return {"error": f"Cannot reach data agent at {DATA_AGENT_URL}"}
    except Exception as e:
        return {"error": f"Lookup failed: {e}"}


async def classify_hazard(visual_description: str) -> dict:
    """
    Call this when you have identified a housing hazard in the camera feed.
    Provide your visual description. Returns the official HMC statute and class.
    IMPORTANT: Use ONLY the statute returned by this tool — never invent one.
    """
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            resp = await client.post(
                f"{COMPLAINT_AGENT_URL}/classify",
                json={"description": visual_description},
            )
            resp.raise_for_status()
            return resp.json()
    except httpx.ConnectError:
        return {"error": f"Cannot reach complaint agent at {COMPLAINT_AGENT_URL}"}
    except Exception as e:
        return {"error": f"Classification failed: {e}"}


async def surface_emergency_contacts(reason: str) -> dict:
    """
    Call this when a Class C hazard is detected OR when the tenant mentions:
    emergency, urgent, gas, no heat, flooding, ceiling collapse, broken lock.
    This shows the emergency contacts panel on the tenant's phone.
    """
    # No HTTP call needed — the Orchestrator intercepts this return value
    # and sends {"type": "action", "action": "show_emergency_panel", "reason": ...}
    # directly to Android over the WebSocket.
    return {"action": "show_emergency_panel", "reason": reason}


async def draft_complaint(
    building_data: dict,
    hazard_type: str,
    hazard_class: str,
    statute: str,
    evidence_description: str,
) -> dict:
    """
    Call this after classify_hazard() to generate the official complaint.
    Use the statute from classify_hazard() — never from your own knowledge.
    """
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            resp = await client.post(
                f"{COMPLAINT_AGENT_URL}/draft",
                json={
                    "building_data": building_data,
                    "hazard_type": hazard_type,
                    "hazard_class": hazard_class,
                    "statute": statute,
                    "evidence_description": evidence_description,
                },
            )
            resp.raise_for_status()
            return resp.json()
    except httpx.ConnectError:
        return {"error": f"Cannot reach complaint agent at {COMPLAINT_AGENT_URL}"}
    except Exception as e:
        return {"error": f"Complaint draft failed: {e}"}


# ─────────────────────────────────────────────
# Tool registry — used by server.py to register
# tools with the Gemini Live session and to
# dispatch function_call responses.
# ─────────────────────────────────────────────

# Maps function name → (callable, parameter schema for Gemini)
TOOL_REGISTRY = {
    "lookup_building": {
        "fn": lookup_building,
        "declaration": {
            "name": "lookup_building",
            "description": (
                "Call this immediately when the tenant says their building address. "
                "Returns owner name, LLC, violation history, knowledge graph."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "address": {
                        "type": "string",
                        "description": "Street address, e.g. '80 Woodruff Ave'",
                    },
                    "borough": {
                        "type": "string",
                        "description": "NYC borough, e.g. 'Brooklyn'",
                    },
                },
                "required": ["address", "borough"],
            },
        },
    },
    "classify_hazard": {
        "fn": classify_hazard,
        "declaration": {
            "name": "classify_hazard",
            "description": (
                "Call when you identify a CONFIRMED housing hazard in the camera feed. "
                "Do NOT call for normal wear-and-tear or ambiguous stains. "
                "Returns the official HMC statute and class. "
                "If the result has hazard_class='NONE', the evidence was insufficient — "
                "ask the tenant for more details instead of proceeding. "
                "Use ONLY the statute returned — never invent one."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "visual_description": {
                        "type": "string",
                        "description": "Detailed description of the hazard with SPECIFIC indicators — "
                                       "e.g. 'active water dripping from ceiling within 6 inches of "
                                       "exposed light fixture' NOT just 'stain on ceiling'",
                    },
                },
                "required": ["visual_description"],
            },
        },
    },
    "surface_emergency_contacts": {
        "fn": surface_emergency_contacts,
        "declaration": {
            "name": "surface_emergency_contacts",
            "description": (
                "Call when Class C hazard detected OR tenant says: emergency, urgent, "
                "gas, no heat, flooding, ceiling collapse, broken lock."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "reason": {
                        "type": "string",
                        "description": "Why emergency contacts are needed",
                    },
                },
                "required": ["reason"],
            },
        },
    },
    "draft_complaint": {
        "fn": draft_complaint,
        "declaration": {
            "name": "draft_complaint",
            "description": (
                "Call after classify_hazard() to generate the official complaint. "
                "Use the statute from classify_hazard() — never your own knowledge."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "building_data": {
                        "type": "object",
                        "description": "Full building JSON from lookup_building()",
                    },
                    "hazard_type": {
                        "type": "string",
                        "description": "Hazard type from classify_hazard()",
                    },
                    "hazard_class": {
                        "type": "string",
                        "description": "A, B, or C from classify_hazard()",
                    },
                    "statute": {
                        "type": "string",
                        "description": "Statute string from classify_hazard()",
                    },
                    "evidence_description": {
                        "type": "string",
                        "description": "What the camera saw — detailed description",
                    },
                },
                "required": [
                    "building_data",
                    "hazard_type",
                    "hazard_class",
                    "statute",
                    "evidence_description",
                ],
            },
        },
    },
}

# Convenience: list of declarations for Gemini's tools parameter
TOOL_DECLARATIONS = [t["declaration"] for t in TOOL_REGISTRY.values()]
