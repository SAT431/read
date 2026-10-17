import asyncio
import json
import base64
import httpx
import pytest
from service.providers.page_engineer import GeminiPageEngineer
from service.settings import Settings
from service.errors import ProviderFailure
from service.documents.evidence import PdfEvidenceStore
from service.tests.test_pdf_evidence import pdf


@pytest.mark.parametrize('invalid', [None, 'bounds', 'reference', 'reviewed'])
def test_pdf_provider_input_and_draft_validation(tmp_path, invalid):
    store = PdfEvidenceStore(tmp_path)
    reference = store.import_pdf(pdf())
    evidence, selected = store.evidence(reference['documentId'], 0)
    draft = {'title': 'Mitochondria', 'elements': [{'kind': 'PARAGRAPH',
        'text': 'Mitochondria: ATP production.', 'bounds': [30, 30, 350, 90],
        'evidenceBlockIds': [evidence['blocks'][0]['id']]}], 'uncertainties': []}
    if invalid == 'bounds': draft['elements'][0]['bounds'] = [-1, 0, 350, 90]
    if invalid == 'reference': draft['elements'][0]['evidenceBlockIds'] = ['invented']
    if invalid == 'reviewed': draft['status'] = 'REVIEWED'
    def provider(request):
        body = json.loads(request.content)
        parts = body['contents'][0]['parts']
        assert base64.b64decode(parts[1]['inlineData']['data']) == selected
        assert parts[1]['inlineData']['mimeType'] == 'application/pdf'
        assert json.loads(parts[0]['text'])['documentId'] == reference['documentId']
        return httpx.Response(200, json={'candidates': [{'finishReason': 'STOP',
            'content': {'parts': [{'text': json.dumps(draft)}]}}]})
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(provider)) as client:
            engineer = GeminiPageEngineer(Settings('test-token-1234567890123456789012345',
                gemini_key='test-key', gemini_model='configured-model'), client)
            if invalid:
                with pytest.raises(ProviderFailure): await engineer.reconstruct(evidence, selected)
            else:
                result = await engineer.reconstruct(evidence, selected)
                assert result.elements[0].text == 'Mitochondria: ATP production.'
    asyncio.run(run())
