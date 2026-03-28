import asyncio
from google import genai
from google.genai import types

async def main():
    client = genai.Client(api_key="AIzaSyD6vQ5cFp0pi8ICPc4URmTD6tCCyDLj1Jk")
    try:
        async with client.aio.live.connect(
            model="gemini-3.1-flash-live-preview",
            config=types.LiveConnectConfig(
                system_instruction=types.Content(parts=[types.Part.from_text(text="Be brief.")]),
                response_modalities=[types.LiveConnectConfigResponseModalities.AUDIO],
            )
        ) as session:
            print("Connected!")
            await session.send_client_content(
                turns=[types.Content(role="user", parts=[types.Part.from_text(text="Hi")])],
                turn_complete=True
            )
            async for response in session.receive():
                if response.server_content and response.server_content.model_turn:
                    for part in response.server_content.model_turn.parts:
                        if part.inline_data:
                            print("Got audio!", len(part.inline_data.data))
                            break
                        if part.text:
                            print("Got text:", part.text)
                break
    except Exception as e:
        print("Error:", e)

asyncio.run(main())
