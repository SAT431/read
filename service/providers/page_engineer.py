import base64
import json
import math
from pathlib import Path
import httpx
from service.errors import ProviderFailure, check_provider_status
from service.protocol.page_draft import PageDraft


class GeminiPageEngineer:
    def __init__(self, settings, client):
        self.settings, self.client = settings, client
        self.prompt = (Path(__file__).resolve().parents[2] /
            'android/api-contracts/prompts/gemini-page-engineer.md').read_text(encoding='utf-8')
        self.prompt += '''
Return this provisional draft only: {"title":string,"elements":[{"kind":
"HEADING|PARAGRAPH|FIGURE|CAPTION|TABLE|LIST", "text":exact transcription,
"bounds":[left,top,right,bottom],"evidenceBlockIds":[supplied block IDs]}],
"uncertainties":[short descriptions]}. Element array order is logical reading order.
Coordinates use the normalized unrotated page, top-left origin, in PDF points.
For figures supply the whole original region and caption separately; code will crop it.
Do not return status, reviewed flags, IDs, corrections, assets, scripts or enrichments.
For missing/ambiguous words retain uncertainty; do not invent a plausible replacement.
'''

    async def reconstruct(self, evidence, selected_pdf):
        if not self.settings.gemini_key or not self.settings.gemini_model:
            raise ProviderFailure(503, 'Gemini page engineering is not configured')
        if len(selected_pdf) > 15 * 1024 * 1024 or len(json.dumps(evidence).encode()) > 500000:
            raise ProviderFailure(413, 'Selected-page evidence exceeds provider request limit')
        try:
            response = await self.client.post(
                f'https://generativelanguage.googleapis.com/v1beta/models/{self.settings.gemini_model}:generateContent',
                headers={'x-goog-api-key': self.settings.gemini_key}, json={
                    'systemInstruction': {'parts': [{'text': self.prompt}]},
                    'contents': [{'role': 'user', 'parts': [{'text': json.dumps(evidence)},
                        {'inlineData': {'mimeType': 'application/pdf', 'data': base64.b64encode(selected_pdf).decode()}}]}],
                    'generationConfig': {'responseMimeType': 'application/json', 'maxOutputTokens': 8192}})
            check_provider_status(response)
            candidate = response.json()['candidates'][0]
            if candidate.get('finishReason') != 'STOP': raise ValueError('Incomplete reconstruction')
            text = ''.join(p.get('text', '') for p in candidate['content']['parts'] if not p.get('thought'))
            draft = PageDraft.model_validate_json(text)
            known = {b['id'] for b in evidence['blocks']}
            for element in draft.elements:
                left, top, right, bottom = element.bounds
                if not all(math.isfinite(n) for n in element.bounds) or not (
                    0 <= left < right <= evidence['width'] and 0 <= top < bottom <= evidence['height']):
                    raise ValueError('Invalid source region')
                if not set(element.evidenceBlockIds) <= known: raise ValueError('Invented evidence reference')
            return draft
        except httpx.TimeoutException:
            raise ProviderFailure(504, 'Page engineering timed out') from None
        except httpx.HTTPError:
            raise ProviderFailure(502, 'Page engineering provider is unreachable') from None
        except (ValueError, KeyError, IndexError, TypeError):
            raise ProviderFailure(502, 'Page reconstruction failed validation; original PDF preserved') from None
