from contextlib import asynccontextmanager
from pathlib import Path
import httpx
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from service.settings import Settings
from service.security import RequestGuard, ProviderBudget
from service.protocol.envelopes import MentorRequest, MentorResponse
from service.providers.gemini import GeminiMentor
from service.providers.groq import GroqTranscriber
from service.documents.evidence import PdfEvidenceStore
from service.documents.routes import document_routes
from service.documents.engineering import engineering_routes
from service.providers.page_engineer import GeminiPageEngineer


def create_app(settings: Settings | None = None, transport=None):
    config = settings or Settings.from_environment()

    @asynccontextmanager
    async def lifespan(app):
        async with httpx.AsyncClient(timeout=httpx.Timeout(60, connect=10), transport=transport,
                                     follow_redirects=False) as client:
            app.state.mentor = GeminiMentor(config, client)
            app.state.transcriber = GroqTranscriber(config, client)
            app.state.page_engineer = GeminiPageEngineer(config, client)
            yield

    app = FastAPI(title='Folio private native API', version='1.0.0', lifespan=lifespan,
                  docs_url=None, redoc_url=None, openapi_url=None)
    app.add_middleware(RequestGuard, token=config.access_token)
    budget = ProviderBudget(config.requests_per_minute, config.concurrent_requests)
    store = PdfEvidenceStore(Path(config.data_root))
    app.include_router(document_routes(store))
    app.include_router(engineering_routes(store, budget))

    @app.get('/healthz')
    async def health():
        return {'status': 'ok'}

    @app.get('/v1/status')
    async def status():
        return {'schemaVersion': 1, 'geminiConfigured': bool(config.gemini_key and config.gemini_model),
                'groqConfigured': bool(config.groq_key), 'speechModel': 'whisper-large-v3',
                'pageEngineeringAvailable': bool(config.gemini_key and config.gemini_model)}

    @app.post('/v1/mentor', response_model=MentorResponse)
    async def mentor(body: MentorRequest):
        async with budget.slot():
            return await app.state.mentor.respond(body)

    @app.post('/v1/transcribe')
    async def transcribe(file: UploadFile = File(...), language: str | None = Form(None)):
        suffix = Path(file.filename or '').suffix.lower().lstrip('.')
        if suffix not in {'flac', 'mp3', 'mp4', 'mpeg', 'mpga', 'm4a', 'ogg', 'wav', 'webm'}:
            raise HTTPException(415, 'Unsupported audio format')
        if language not in {None, 'en', 'hi'}:
            raise HTTPException(422, 'Use auto detection, en or hi')
        try:
            audio = await file.read(12 * 1024 * 1024 + 1)
        finally:
            await file.close()
        if not audio:
            raise HTTPException(422, 'Audio is empty')
        if len(audio) > 12 * 1024 * 1024:
            raise HTTPException(413, 'Audio exceeds 12 MB; record a shorter segment')
        async with budget.slot():
            return await app.state.transcriber.transcribe(audio, suffix, language)

    return app
