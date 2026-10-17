import json
from pathlib import Path
import httpx
from service.errors import ProviderFailure, check_provider_status
from service.protocol.envelopes import MentorRequest, MentorResponse
from service.protocol.validation import validate_response, COMMAND_FIELDS, WORKSPACE_FIELDS
from service.protocol.tool_help import ACTIVITY_HELP

PROMPT = Path(__file__).resolve().parents[2] / 'android/api-contracts/prompts/gemini-orchestrator.md'


class GeminiMentor:
    def __init__(self, settings, client: httpx.AsyncClient):
        self.settings, self.client = settings, client
        self.prompt = PROMPT.read_text(encoding='utf-8') + '''
Wire envelope: {"schemaVersion":1,"requestId": supplied requestId,"message": plain text,
"batch": null OR {"schemaVersion":1,"requestId":supplied requestId,
"expectedRevision":capabilityContext.revision,"commands":[{"type":action ID, exact arguments}]}}.
For clarification, unavailable capabilities or answers needing no UI changes, batch is null.
sourceContext contains supplied source/activity evidence; executionResults describe actual results.
Do not claim successful execution before receiving a successful execution result.
'''
        tools = {k: {field: kind.__name__ for field, kind in v.items()} for k, v in COMMAND_FIELDS.items()}
        workspace = {k: {field: kind.__name__ for field, kind in v.items()} for k, v in WORKSPACE_FIELDS.items()}
        self.prompt += '\nExact command fields (all required): ' + json.dumps(tools)
        self.prompt += '\nNested workspace.command fields: ' + json.dumps(workspace)
        self.prompt += ACTIVITY_HELP

    async def respond(self, request: MentorRequest) -> MentorResponse:
        if not self.settings.gemini_key or not self.settings.gemini_model:
            raise ProviderFailure(503, 'Gemini is not configured on the private service')
        url = f'https://generativelanguage.googleapis.com/v1beta/models/{self.settings.gemini_model}:generateContent'
        try:
            response = await self.client.post(url, headers={'x-goog-api-key': self.settings.gemini_key}, json={
                'systemInstruction': {'parts': [{'text': self.prompt}]},
                'contents': [{'role': 'user', 'parts': [{'text': request.model_dump_json()}]}],
                'generationConfig': {'responseMimeType': 'application/json', 'maxOutputTokens': 8192},
            })
            check_provider_status(response)
            result = response.json()
            candidate = result['candidates'][0]
            if candidate.get('finishReason') != 'STOP':
                raise ValueError('Incomplete or blocked response')
            text = ''.join(p.get('text', '') for p in candidate['content']['parts'] if not p.get('thought'))
            parsed = MentorResponse.model_validate_json(text)
            validate_response(parsed, request)
            return parsed
        except httpx.TimeoutException:
            raise ProviderFailure(504, 'Gemini timed out. App state is unchanged.') from None
        except httpx.HTTPError:
            raise ProviderFailure(502, 'Gemini is unreachable. App state is unchanged.') from None
        except (ValueError, KeyError, IndexError, TypeError):
            raise ProviderFailure(502, 'Gemini returned invalid actions. App state is unchanged.') from None
