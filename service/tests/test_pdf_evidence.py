import hashlib
import pymupdf
import pytest
from service.documents.evidence import PdfEvidenceStore


def pdf(rotation=0, scan=False):
    with pymupdf.open() as document:
        page = document.new_page(width=400, height=600)
        if scan:
            pixmap = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, 40, 40), False)
            pixmap.clear_with(150)
            page.insert_image(pymupdf.Rect(20, 20, 200, 200), stream=pixmap.tobytes('png'))
        else:
            page.insert_text((40, 60), 'Mitochondria: ATP production.')
            page.draw_rect(pymupdf.Rect(30, 100, 120, 200), color=(0, 1, 0))
        page.set_rotation(rotation)
        return document.tobytes()


def test_original_bytes_are_immutable_and_imports_deduplicate(tmp_path):
    store = PdfEvidenceStore(tmp_path)
    data = pdf()
    first = store.import_pdf(data)
    assert first == store.import_pdf(data)
    assert first['checksum'] == hashlib.sha256(data).hexdigest()
    assert (tmp_path / 'pdfs' / (first['documentId'] + '.pdf')).read_bytes() == data


def test_selected_page_contains_exact_text_and_original_vector_crop(tmp_path):
    store = PdfEvidenceStore(tmp_path)
    imported = store.import_pdf(pdf())
    evidence, selected = store.evidence(imported['documentId'], 0)
    assert evidence['width'] == 400 and evidence['height'] == 600
    assert evidence['textLayerPresent']
    assert any('Mitochondria: ATP production.' in block.get('text', '') for block in evidence['blocks'])
    with pymupdf.open(stream=selected, filetype='pdf') as page_pdf:
        assert len(page_pdf) == 1
        assert 'Mitochondria' in page_pdf[0].get_text()
    asset = store.crop(imported['documentId'], 0, [25, 95, 125, 205])
    image = pymupdf.Pixmap(str(tmp_path / 'source-assets' / asset))
    assert image.width == 200 and image.height == 220
    edge = image.pixel(10, 110)
    assert edge[1] > edge[0] + 100  # the original green vector border, not empty white pixels


def test_rotated_page_uses_consistent_normalized_coordinates(tmp_path):
    store = PdfEvidenceStore(tmp_path)
    data = pdf(rotation=90)
    imported = store.import_pdf(data)
    evidence, selected = store.evidence(imported['documentId'], 0)
    assert evidence['originalRotation'] == 90
    assert evidence['width'] == 400 and evidence['height'] == 600
    with pymupdf.open(stream=selected, filetype='pdf') as selected_pdf:
        assert selected_pdf[0].rotation == 0
    assert (tmp_path / 'pdfs' / (imported['documentId'] + '.pdf')).read_bytes() == data


def test_scan_exposes_original_image_evidence_without_fake_text(tmp_path):
    store = PdfEvidenceStore(tmp_path)
    imported = store.import_pdf(pdf(scan=True))
    evidence, _ = store.evidence(imported['documentId'], 0)
    assert not evidence['textLayerPresent']
    assert any(block['kind'] == 'image' and block['mediaId'] for block in evidence['blocks'])


@pytest.mark.parametrize('bounds', [[-1, 0, 100, 100], [0, 0, 900, 900], [0, 0, 0, 1], [0, 0, float('nan'), 1]])
def test_invalid_crops_and_navigation_never_escape_source(tmp_path, bounds):
    store = PdfEvidenceStore(tmp_path)
    imported = store.import_pdf(pdf())
    with pytest.raises(ValueError): store.crop(imported['documentId'], 0, bounds)
    with pytest.raises(ValueError): store.evidence(imported['documentId'], 1)
    with pytest.raises(ValueError): store.evidence('../escape', 0)
