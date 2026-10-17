import hashlib
import math
import os
import re
import threading
import uuid
import io
from pathlib import Path
import pymupdf


class PdfEvidenceStore:
    """Original bytes are immutable. Extraction/crops never invent textbook imagery."""
    def __init__(self, root: Path):
        self.root = root
        self.pdfs = root / 'pdfs'
        self.assets = root / 'source-assets'
        self.pdfs.mkdir(parents=True, exist_ok=True)
        self.assets.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()

    def import_pdf(self, data: bytes):
        return self.import_file(io.BytesIO(data))

    def import_file(self, source):
        pending = self.pdfs / (uuid.uuid4().hex + '.tmp')
        digest = hashlib.sha256(); size = 0
        try:
            with pending.open('xb') as output:
                while chunk := source.read(1024 * 1024):
                    if size == 0 and not chunk.startswith(b'%PDF-'):
                        raise ValueError('Use a PDF')
                    size += len(chunk)
                    if size > 100 * 1024 * 1024: raise ValueError('PDF exceeds 100 MB')
                    digest.update(chunk); output.write(chunk)
                output.flush(); os.fsync(output.fileno())
            if not size: raise ValueError('PDF is empty')
            with pymupdf.open(pending) as document:
                if document.needs_pass or not 1 <= len(document) <= 3000:
                    raise ValueError('PDF is encrypted or has an unsupported page count')
                pages = len(document)
            checksum = digest.hexdigest()
            with self.lock:
                destination = self.pdfs / f'{checksum}.pdf'
                if destination.exists(): self._path(checksum)
                else: os.replace(pending, destination)
            return {'documentId': checksum, 'checksum': checksum, 'pageCount': pages}
        finally:
            pending.unlink(missing_ok=True)

    def _path(self, document_id):
        if not re.fullmatch(r'[a-f0-9]{64}', document_id):
            raise ValueError('Invalid document identifier')
        path = self.pdfs / f'{document_id}.pdf'
        if not path.is_file():
            raise FileNotFoundError('PDF not found')
        with path.open('rb') as original:
            if hashlib.file_digest(original, 'sha256').hexdigest() != document_id:
                raise ValueError('Stored original PDF failed checksum verification')
        return path

    def evidence(self, document_id: str, page_index: int):
        with self.lock, pymupdf.open(self._path(document_id)) as document:
            page = self._page(document, page_index)
            original_rotation = page.rotation
            page.set_rotation(0)
            blocks = []
            for index, block in enumerate(page.get_text('dict', sort=True)['blocks']):
                identifier = f'{document_id}-p{page_index}-b{index}'
                bounds = list(block['bbox'])
                if block['type'] == 0:
                    spans = [{'text': span['text'], 'bounds': list(span['bbox']), 'font': span['font'],
                              'size': span['size']} for line in block['lines'] for span in line['spans']]
                    blocks.append({'id': identifier, 'kind': 'text', 'bounds': bounds,
                                   'text': '\n'.join(''.join(s['text'] for s in line['spans']) for line in block['lines']),
                                   'spans': spans})
                elif block['type'] == 1:
                    visible = pymupdf.Rect(bounds) & page.rect
                    if not visible.is_empty:
                        asset = self._crop(page, identifier, list(visible))
                        blocks.append({'id': identifier, 'kind': 'image', 'bounds': list(visible), 'mediaId': asset})
            original = self._crop(page, f'{document_id}-p{page_index}-original', list(page.rect))
            # Selected-page PDF preserves vector/text evidence for Gemini; original upload remains unchanged.
            selected = pymupdf.open()
            try:
                selected.insert_pdf(document, from_page=page_index, to_page=page_index)
                selected_bytes = selected.tobytes()
            finally:
                selected.close()
            return {'documentId': document_id, 'pageIndex': page_index, 'width': page.rect.width,
                    'height': page.rect.height, 'coordinateSystem': 'unrotated PDF page points, top-left origin',
                    'originalRotation': original_rotation, 'originalMediaId': original, 'blocks': blocks,
                    'textLayerPresent': any(b['kind'] == 'text' and b['text'].strip() for b in blocks)}, selected_bytes

    def describe(self, document_id):
        with self.lock, pymupdf.open(self._path(document_id)) as document:
            return {'documentId': document_id, 'checksum': document_id, 'pageCount': len(document)}

    def crop(self, document_id, page_index, bounds):
        with self.lock, pymupdf.open(self._path(document_id)) as document:
            page = self._page(document, page_index)
            page.set_rotation(0)
            if len(bounds) != 4 or any(type(n) not in (int, float) or not math.isfinite(n) for n in bounds):
                raise ValueError('Crop bounds must be four finite coordinates')
            region = pymupdf.Rect(bounds)
            if region.is_empty or region.is_infinite or not page.rect.contains(region):
                raise ValueError('Crop bounds must stay inside the page')
            identifier = f'{document_id}-p{page_index}-crop-{hashlib.sha256(repr(list(region)).encode()).hexdigest()[:16]}'
            return self._crop(page, identifier, list(region))

    def _page(self, document, index):
        if type(index) is not int or not 0 <= index < len(document):
            raise ValueError('Page outside PDF')
        page = document[index]
        if page.rect.width * page.rect.height > 20_000_000:
            raise ValueError('Page dimensions too large')
        return page

    def _crop(self, page, identifier, bounds):
        region = pymupdf.Rect(bounds)
        if region.is_empty or not page.rect.contains(region):
            raise ValueError('Invalid source bounds')
        scale = min(2, 1800 / max(region.width, region.height))
        # This opened page is normalized to rotation zero; its text/crop coordinates agree.
        pixmap = page.get_pixmap(matrix=pymupdf.Matrix(scale, scale), clip=region, alpha=False)
        name = identifier + '.png'
        self._write(self.assets / name, pixmap.tobytes('png'))
        return name

    def _write(self, path, data):
        if path.exists():
            return
        pending = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
        try:
            with pending.open('xb') as output:
                output.write(data); output.flush(); os.fsync(output.fileno())
            os.replace(pending, path)
        finally:
            pending.unlink(missing_ok=True)
