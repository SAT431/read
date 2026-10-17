import asyncio
import secrets
import time
from collections import deque
from contextlib import asynccontextmanager
from fastapi import HTTPException, Request
from starlette.responses import JSONResponse


class RequestGuard:
    """Authenticate before buffering bounded requests. No token/body logging."""
    def __init__(self, app, token: str, limit: int = 16 * 1024 * 1024):
        self.app, self.token, self.limit = app, token, limit

    async def __call__(self, scope, receive, send):
        if scope['type'] != 'http' or scope['path'] == '/healthz':
            return await self.app(scope, receive, send)
        headers = dict(scope['headers'])
        supplied = headers.get(b'authorization', b'')
        if not secrets.compare_digest(supplied, ('Bearer ' + self.token).encode('ascii')):
            return await JSONResponse({'detail': 'Authentication required'}, 401,
                                      headers={'WWW-Authenticate': 'Bearer'})(scope, receive, send)
        if scope['path'] == '/v1/documents' and scope['method'] == 'POST':
            # PDF multipart parsing spools to disk rather than copying 100 MB into memory.
            received = 0
            async def upload_receive():
                nonlocal received
                event = await receive()
                received += len(event.get('body', b''))
                if received > 101 * 1024 * 1024:
                    raise HTTPException(413, 'PDF upload exceeds size limit')
                return event
            return await self.app(scope, upload_receive, send)
        # Streaming count also catches omitted/incorrect Content-Length.
        body = bytearray()
        while True:
            event = await receive()
            if event['type'] == 'http.disconnect':
                return
            chunk = event.get('body', b'')
            if len(body) + len(chunk) > self.limit:
                return await JSONResponse({'detail': 'Request exceeds size limit'}, 413)(scope, receive, send)
            body.extend(chunk)
            if not event.get('more_body', False):
                break
        delivered = False

        async def bounded_receive():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {'type': 'http.request', 'body': bytes(body), 'more_body': False}
            return await receive()
        await self.app(scope, bounded_receive, send)


class ProviderBudget:
    """Single-user, single-worker rolling budget and bounded provider concurrency."""
    def __init__(self, per_minute: int, concurrency: int):
        self.per_minute = per_minute
        self.times = deque()
        self.semaphore = asyncio.Semaphore(concurrency)

    @asynccontextmanager
    async def slot(self):
        now = time.monotonic()
        while self.times and now - self.times[0] >= 60:
            self.times.popleft()
        if len(self.times) >= self.per_minute:
            raise HTTPException(429, 'Service request limit reached', headers={'Retry-After': '60'})
        self.times.append(now)
        try:
            await asyncio.wait_for(self.semaphore.acquire(), timeout=2)
        except TimeoutError:
            raise HTTPException(429, 'Service is busy', headers={'Retry-After': '2'}) from None
        try:
            yield
        finally:
            self.semaphore.release()
