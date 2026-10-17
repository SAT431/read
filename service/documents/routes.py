import re
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from starlette.concurrency import run_in_threadpool
from pydantic import Field
from service.protocol.envelopes import StrictModel


class CropRequest(StrictModel):
    bounds: list[float] = Field(min_length=4, max_length=4)


def document_routes(store):
    router = APIRouter(prefix='/v1')

    async def operation(function, *args):
        try:
            return await run_in_threadpool(function, *args)
        except FileNotFoundError:
            raise HTTPException(404, 'PDF not found') from None
        except (ValueError, RuntimeError):
            raise HTTPException(422, 'PDF or source region is invalid') from None

    @router.post('/documents')
    async def upload(file: UploadFile = File(...)):
        try:
            return await operation(store.import_file, file.file)
        finally:
            await file.close()

    @router.get('/documents/{document_id}/pages/{page_index}/evidence')
    async def evidence(document_id: str, page_index: int):
        result, _ = await operation(store.evidence, document_id, page_index)
        return result

    @router.get('/documents/{document_id}')
    async def describe(document_id: str):
        return await operation(store.describe, document_id)

    @router.post('/documents/{document_id}/pages/{page_index}/crops')
    async def crop(document_id: str, page_index: int, body: CropRequest):
        return {'mediaId': await operation(store.crop, document_id, page_index, body.bounds)}

    @router.get('/assets/{media_id}')
    async def asset(media_id: str):
        if not re.fullmatch(r'[a-f0-9]{64}-p[0-9]+-(?:original|b[0-9]+|crop-[a-f0-9]{16})\.png', media_id):
            raise HTTPException(422, 'Invalid source asset ID')
        path = store.assets / media_id
        if not path.is_file(): raise HTTPException(404, 'Source asset not found')
        return FileResponse(path, media_type='image/png', headers={'Cache-Control': 'private, max-age=3600'})

    return router
