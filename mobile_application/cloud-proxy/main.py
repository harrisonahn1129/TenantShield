"""
TenantShield Cloud Run Proxy — handles Gemini Live API with ADC.
The Android app calls this service instead of connecting directly to Vertex AI.
No API keys or service account files needed — Cloud Run uses ADC automatically.
"""

import os
import json
import uuid
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
from google import genai
from google.genai import types

app = FastAPI(title="TenantShield Proxy")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

PROJECT_ID = os.environ.get("GOOGLE_CLOUD_PROJECT", "crucial-bucksaw-371623")
REGION = os.environ.get("GOOGLE_CLOUD_REGION", "us-central1")
LIVE_MODEL = "gemini-2.0-flash-live-001"
REST_MODEL = "gemini-2.0-flash"

# Initialize the Gemini client with ADC (automatic on Cloud Run)
client = genai.Client(
    vertexai=True,
    project=PROJECT_ID,
    location=REGION,
)

# In-memory session storage (for conversation history)
sessions = {}


class StartSessionRequest(BaseModel):
    system_prompt: str


class StartSessionResponse(BaseModel):
    session_id: str


class SendMessageRequest(BaseModel):
    session_id: str
    message: str


class SendMessageResponse(BaseModel):
    response_text: str


class AnalyzeImagesRequest(BaseModel):
    system_prompt: str
    user_message: str
    images_base64: List[str]  # List of base64-encoded JPEG images


class AnalyzeImagesResponse(BaseModel):
    response_text: str


class GenerateRequest(BaseModel):
    system_prompt: str
    user_message: str


class GenerateResponse(BaseModel):
    response_text: str


@app.get("/health")
def health():
    return {"status": "ok", "project": PROJECT_ID, "region": REGION}


@app.post("/session/start", response_model=StartSessionResponse)
async def start_session(req: StartSessionRequest):
    """Start a new Live API conversation session."""
    session_id = str(uuid.uuid4())

    try:
        # Create a Live API session
        live_session = client.aio.live.connect(
            model=LIVE_MODEL,
            config=types.LiveConnectConfig(
                response_modalities=["TEXT"],
                system_instruction=types.Content(
                    parts=[types.Part(text=req.system_prompt)]
                ),
            ),
        )

        sessions[session_id] = {
            "session": live_session,
            "system_prompt": req.system_prompt,
            "history": [],
        }

        return StartSessionResponse(session_id=session_id)
    except Exception as e:
        # Fall back to REST-based session if Live API fails
        sessions[session_id] = {
            "session": None,  # No live session — use REST fallback
            "system_prompt": req.system_prompt,
            "history": [],
        }
        return StartSessionResponse(session_id=session_id)


@app.post("/session/send", response_model=SendMessageResponse)
async def send_message(req: SendMessageRequest):
    """Send a message in an existing conversation session."""
    if req.session_id not in sessions:
        raise HTTPException(status_code=404, detail="Session not found")

    session_data = sessions[req.session_id]
    session_data["history"].append({"role": "user", "text": req.message})

    try:
        live_session = session_data.get("session")

        if live_session is not None:
            # Use Live API session
            async with live_session as session:
                await session.send(
                    input=types.LiveClientContent(
                        turns=[types.Content(
                            role="user",
                            parts=[types.Part(text=req.message)]
                        )],
                        turn_complete=True,
                    ),
                    end_of_turn=True,
                )

                response_text = ""
                async for msg in session.receive():
                    if msg.text:
                        response_text += msg.text

                session_data["history"].append({"role": "model", "text": response_text})
                return SendMessageResponse(response_text=response_text)
        else:
            # REST fallback
            return await _rest_conversation(session_data)

    except Exception as e:
        # Fall back to REST on any Live API error
        session_data["session"] = None
        return await _rest_conversation(session_data)


async def _rest_conversation(session_data):
    """Fallback: use REST generateContent with conversation history."""
    contents = []
    for entry in session_data["history"]:
        contents.append(types.Content(
            role=entry["role"],
            parts=[types.Part(text=entry["text"])],
        ))

    response = client.models.generate_content(
        model=REST_MODEL,
        contents=contents,
        config=types.GenerateContentConfig(
            system_instruction=session_data["system_prompt"],
            response_mime_type="application/json",
        ),
    )

    response_text = response.text
    session_data["history"].append({"role": "model", "text": response_text})
    return SendMessageResponse(response_text=response_text)


@app.post("/session/end")
async def end_session(session_id: str):
    """End a conversation session."""
    if session_id in sessions:
        del sessions[session_id]
    return {"status": "ok"}


@app.post("/analyze", response_model=AnalyzeImagesResponse)
async def analyze_images(req: AnalyzeImagesRequest):
    """Analyze images using Gemini multimodal (for Inspection Agent)."""
    import base64

    parts = [types.Part(text=req.user_message)]

    for img_b64 in req.images_base64:
        img_bytes = base64.b64decode(img_b64)
        parts.append(types.Part(
            inline_data=types.Blob(
                mime_type="image/jpeg",
                data=img_bytes,
            )
        ))

    response = client.models.generate_content(
        model=REST_MODEL,
        contents=[types.Content(role="user", parts=parts)],
        config=types.GenerateContentConfig(
            system_instruction=req.system_prompt,
            response_mime_type="application/json",
        ),
    )

    return AnalyzeImagesResponse(response_text=response.text)


@app.post("/generate", response_model=GenerateResponse)
async def generate(req: GenerateRequest):
    """Simple text generation (for Filing Agent)."""
    response = client.models.generate_content(
        model=REST_MODEL,
        contents=[types.Content(
            role="user",
            parts=[types.Part(text=req.user_message)],
        )],
        config=types.GenerateContentConfig(
            system_instruction=req.system_prompt,
            response_mime_type="application/json",
        ),
    )

    return GenerateResponse(response_text=response.text)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", 8080)))
