"""
TenantShield Cloud Run Proxy — uses Google Agent Development Kit (ADK)
with Gemini Live API via ADC. No API keys needed on Cloud Run.
"""

import os

# Set Vertex AI env vars BEFORE importing ADK
os.environ["GOOGLE_GENAI_USE_VERTEXAI"] = "TRUE"
os.environ["GOOGLE_CLOUD_PROJECT"] = os.environ.get("GOOGLE_CLOUD_PROJECT", "crucial-bucksaw-371623")
os.environ["GOOGLE_CLOUD_LOCATION"] = os.environ.get("GOOGLE_CLOUD_REGION", "us-central1")

import uuid
import traceback
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List

from google.adk.agents import Agent
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
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
MODEL = "gemini-3-flash-preview"
APP_NAME = "tenantshield_proxy"

# Session service for managing conversation state
session_service = InMemorySessionService()

# Store agent instances per session (each may have different system prompts)
agent_runners = {}


class StartSessionRequest(BaseModel):
    system_prompt: str


class StartSessionResponse(BaseModel):
    session_id: str


class SendMessageRequest(BaseModel):
    session_id: str
    message: str


class SendMessageResponse(BaseModel):
    response_text: str


class GenerateRequest(BaseModel):
    system_prompt: str
    user_message: str


class GenerateResponse(BaseModel):
    response_text: str


class AnalyzeImagesRequest(BaseModel):
    system_prompt: str
    user_message: str
    images_base64: List[str]


class AnalyzeImagesResponse(BaseModel):
    response_text: str


@app.get("/health")
def health():
    return {"status": "ok", "project": PROJECT_ID, "region": REGION, "model": MODEL}


@app.post("/session/start", response_model=StartSessionResponse)
async def start_session(req: StartSessionRequest):
    """Start a new ADK agent session with the given system prompt."""
    session_id = str(uuid.uuid4())
    user_id = f"user_{session_id[:8]}"

    try:
        # Create an ADK agent with the system prompt
        agent = Agent(
            name="tenantshield_agent",
            model=MODEL,
            instruction=req.system_prompt,
            generate_content_config=types.GenerateContentConfig(
                response_mime_type="application/json",
            ),
        )

        # Create a runner for this agent
        runner = Runner(
            agent=agent,
            app_name=APP_NAME,
            session_service=session_service,
        )

        # Create a session
        session = await session_service.create_session(
            app_name=APP_NAME,
            user_id=user_id,
        )

        agent_runners[session_id] = {
            "runner": runner,
            "user_id": user_id,
            "session_id": session.id,
        }

        return StartSessionResponse(session_id=session_id)

    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/session/send", response_model=SendMessageResponse)
async def send_message(req: SendMessageRequest):
    """Send a message using the ADK runner."""
    if req.session_id not in agent_runners:
        raise HTTPException(status_code=404, detail="Session not found")

    runner_data = agent_runners[req.session_id]
    runner = runner_data["runner"]
    user_id = runner_data["user_id"]
    adk_session_id = runner_data["session_id"]

    try:
        # Create user message content
        user_content = types.Content(
            role="user",
            parts=[types.Part(text=req.message)],
        )

        # Run the agent
        response_text = ""
        async for event in runner.run_async(
            user_id=user_id,
            session_id=adk_session_id,
            new_message=user_content,
        ):
            if event.is_final_response():
                if event.content and event.content.parts:
                    for part in event.content.parts:
                        if part.text:
                            response_text += part.text

        if not response_text:
            response_text = '{"complete": false, "response_text": "I didn\'t get a response. Could you try again?"}'

        return SendMessageResponse(response_text=response_text)

    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/session/end")
async def end_session(session_id: str):
    """End and clean up a session."""
    if session_id in agent_runners:
        del agent_runners[session_id]
    return {"status": "ok"}


@app.post("/generate", response_model=GenerateResponse)
async def generate(req: GenerateRequest):
    """One-shot generation (for Filing Agent / explanation)."""
    try:
        agent = Agent(
            name="tenantshield_generate",
            model=MODEL,
            instruction=req.system_prompt,
            generate_content_config=types.GenerateContentConfig(
                response_mime_type="application/json",
            ),
        )

        runner = Runner(
            agent=agent,
            app_name=APP_NAME,
            session_service=session_service,
        )

        user_id = f"oneshot_{uuid.uuid4().hex[:8]}"
        session = await session_service.create_session(
            app_name=APP_NAME,
            user_id=user_id,
        )

        user_content = types.Content(
            role="user",
            parts=[types.Part(text=req.user_message)],
        )

        response_text = ""
        async for event in runner.run_async(
            user_id=user_id,
            session_id=session.id,
            new_message=user_content,
        ):
            if event.is_final_response():
                if event.content and event.content.parts:
                    for part in event.content.parts:
                        if part.text:
                            response_text += part.text

        return GenerateResponse(response_text=response_text)

    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/analyze", response_model=AnalyzeImagesResponse)
async def analyze_images(req: AnalyzeImagesRequest):
    """Multimodal image analysis (for Inspection Agent)."""
    import base64

    try:
        agent = Agent(
            name="tenantshield_inspect",
            model=MODEL,
            instruction=req.system_prompt,
            generate_content_config=types.GenerateContentConfig(
                response_mime_type="application/json",
            ),
        )

        runner = Runner(
            agent=agent,
            app_name=APP_NAME,
            session_service=session_service,
        )

        user_id = f"inspect_{uuid.uuid4().hex[:8]}"
        session = await session_service.create_session(
            app_name=APP_NAME,
            user_id=user_id,
        )

        # Build multimodal content
        parts = [types.Part(text=req.user_message)]
        for img_b64 in req.images_base64:
            img_bytes = base64.b64decode(img_b64)
            parts.append(types.Part(
                inline_data=types.Blob(
                    mime_type="image/jpeg",
                    data=img_bytes,
                )
            ))

        user_content = types.Content(role="user", parts=parts)

        response_text = ""
        async for event in runner.run_async(
            user_id=user_id,
            session_id=session.id,
            new_message=user_content,
        ):
            if event.is_final_response():
                if event.content and event.content.parts:
                    for part in event.content.parts:
                        if part.text:
                            response_text += part.text

        return AnalyzeImagesResponse(response_text=response_text)

    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", 8080)))
