import asyncio
from google import genai
from google.genai import types

async def main():
    client = genai.Client(api_key="AIzaSyD6vQ5cFp0pi8ICPc4URmTD6tCCyDLj1Jk")
    try:
        async with client.aio.live.connect(
            model="gemini-3.1-flash-live-preview",
            config=types.LiveConnectConfig(
                system_instruction=types.Content(parts=[types.Part.from_text("Be brief.")])
            )
        ) as session:
            print("Connected!")
            await session.send_client_content(
                turns=[
                    types.Content(
                        role="user",
                        parts=[types.Part.from_text("Say exactly 'APPLE'.")]
                    )
                ],
                turn_complete=True
            )
            async for response in session.receive():
                print("Got response:", type(response), response.server_content)
                if response.server_content is not None:
                    break
    except Exception as e:
        print("Error:", e)

asyncio.run(main())
