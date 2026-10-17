import json
import httpx
from fastapi.testclient import TestClient
from service.app import create_app
from service.settings import Settings
from service.tests.test_pdf_evidence import pdf


def test_engineered_page_is_cached_unchecked_and_reports_missing_words(tmp_path):
    token = 'engineering-test-token-123456789012345'
    auth = {'Authorization': 'Bearer ' + token}
    calls = []
    def provider(request):
        calls.append(request)
        draft = {'title': 'Mitochondria', 'elements': [
            {'kind': 'PARAGRAPH', 'text': 'Mitochondria: production.', 'bounds': [30, 30, 350, 90], 'evidenceBlockIds': []},
            {'kind': 'FIGURE', 'text': '', 'bounds': [25, 95, 125, 205], 'evidenceBlockIds': []}], 'uncertainties': ['ATP unclear']}
        return httpx.Response(200, json={'candidates': [{'finishReason': 'STOP',
            'content': {'parts': [{'text': json.dumps(draft)}]}}]})
    settings = Settings(token, gemini_key='test', gemini_model='configured', data_root=str(tmp_path))
    with TestClient(create_app(settings, httpx.MockTransport(provider))) as api:
        document = api.post('/v1/documents', headers=auth, files={'file': ('x.pdf', pdf())}).json()['documentId']
        path = f'/v1/documents/{document}/pages/0/engineer'
        assert api.post(path).status_code == 401
        first = api.post(path, headers=auth)
        assert first.status_code == 200
        payload = first.json()
        assert payload['page']['status'] == 'UNCHECKED'
        assert payload['page']['corrections'] == {}
        assert 'ATP' in payload['fidelity']['missingEvidenceTokens']
        assert payload['fidelity']['uncertainties'] == ['ATP unclear']
        media = payload['page']['elements'][1]['mediaId']
        assert api.get(f'/v1/assets/{media}', headers=auth).status_code == 200
        assert api.post(path, headers=auth).json() == payload
        assert len(calls) == 1
    # Cache survives a service restart without another provider request.
    with TestClient(create_app(settings, httpx.MockTransport(provider))) as api:
        assert api.post(path, headers=auth).json() == payload
        assert len(calls) == 1
