import asyncio
from fastapi import APIRouter, HTTPException, Request
from starlette.concurrency import run_in_threadpool
from service.documents.compile_page import compile_page
from service.documents.page_cache import PageCache


def engineering_routes(store, budget):
    router = APIRouter(prefix='/v1')
    cache = PageCache(store.root)
    # One worker serializes duplicate requests without an unbounded per-document lock map.
    jobs = asyncio.Lock()

    @router.post('/documents/{document_id}/pages/{page_index}/engineer')
    async def engineer(document_id: str, page_index: int, request: Request):
        async with jobs:
            try:
                # Check original checksum before returning cached content.
                await run_in_threadpool(store._path, document_id)
                cached = await run_in_threadpool(cache.find, document_id, page_index, 1)
                if cached is not None: return cached
                evidence, selected = await run_in_threadpool(store.evidence, document_id, page_index)
            except FileNotFoundError:
                raise HTTPException(404, 'PDF not found') from None
            except (ValueError, RuntimeError):
                raise HTTPException(422, 'PDF or page is invalid') from None
            async with budget.slot():
                draft = await request.app.state.page_engineer.reconstruct(evidence, selected)
            result = await run_in_threadpool(compile_page, store, evidence, draft)
            await run_in_threadpool(cache.save, document_id, page_index, 1, result)
            return result

    return router
