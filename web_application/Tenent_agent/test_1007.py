import asyncio
from google import genai
from google.genai import types
import traceback
import base64

async def main():
    client = genai.Client(api_key="AIzaSyD6vQ5cFp0pi8ICPc4URmTD6tCCyDLj1Jk")
    try:
        config = types.LiveConnectConfig(
            system_instruction=types.Content(parts=[types.Part.from_text(text="Be brief.")]),
            response_modalities=["AUDIO"]
        )
        async with client.aio.live.connect(model="gemini-2.0-flash-exp", config=config) as session:
            print("Connected!")
            
            await session.send_client_content(
                turns=[types.Content(role="user", parts=[types.Part.from_text(text="Hello!")])],
                turn_complete=True
            )
            
            pixel = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")
            await session.send_realtime_input(
                media=[types.Blob(mime_type="image/jpeg", data=pixel)]
            )

            async for response in session.receive():
                pass
    except Exception as e:
        print("Error:", e)
        traceback.print_exc()

asyncio.run(main())
