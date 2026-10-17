from fastapi.testclient import TestClient
from service.app import create_app
from service.settings import Settings
from service.tests.test_pdf_evidence import pdf

TOKEN = 'document-route-test-token-1234567890'
AUTH = {'Authorization': 'Bearer ' + TOKEN}


def test_auth_upload_evidence_crops_and_assets(tmp_path):
    with TestClient(create_app(Settings(TOKEN, data_root=str(tmp_path)))) as api:
        assert api.post('/v1/documents', files={'file': ('x.pdf', pdf())}).status_code == 401
        response = api.post('/v1/documents', headers=AUTH, files={'file': ('x.pdf', pdf())})
        assert response.status_code == 200
        document_id = response.json()['documentId']
        metadata = api.get(f'/v1/documents/{document_id}', headers=AUTH)
        assert metadata.status_code == 200 and metadata.json()['checksum'] == document_id
        evidence = api.get(f'/v1/documents/{document_id}/pages/0/evidence', headers=AUTH)
        assert evidence.status_code == 200 and evidence.json()['textLayerPresent']
        media = evidence.json()['originalMediaId']
        assert api.get(f'/v1/assets/{media}').status_code == 401
        image = api.get(f'/v1/assets/{media}', headers=AUTH)
        assert image.status_code == 200 and image.content.startswith(b'\x89PNG')
        crop = api.post(f'/v1/documents/{document_id}/pages/0/crops', headers=AUTH, json={'bounds': [25, 95, 125, 205]})
        assert crop.status_code == 200
        assert api.get(f'/v1/documents/{document_id}/pages/0/evidence', headers=AUTH).json()['documentId'] == document_id
        assert api.get(f'/v1/documents/{document_id}/pages/1/evidence', headers=AUTH).status_code == 422
        assert api.get('/v1/assets/invalid.png', headers=AUTH).status_code == 422


def test_failed_import_cleans_partial_files_and_preserves_library(tmp_path):
    with TestClient(create_app(Settings(TOKEN, data_root=str(tmp_path)))) as api:
        assert api.post('/v1/documents', headers=AUTH, files={'file': ('x.pdf', b'not-pdf')}).status_code == 422
        assert not list((tmp_path / 'pdfs').iterdir())
