import json
import httpx
import pytest
from fastapi.testclient import TestClient
from service.app import create_app
from service.settings import Settings

TOKEN = 'test-access-token-not-a-secret-123456789'
AUTH = {'Authorization': 'Bearer ' + TOKEN}


def request():
    return {'schemaVersion': 1, 'requestId': 'request-1', 'utterance': 'Open Botany',
            'capabilityContext': {'schemaVersion': 1, 'revision': 42,
                'actions': [{'id': 'subject.open', 'enabled': True}, {'id': 'pdf.next', 'enabled': True}],
                'chapters': [], 'pdfs': []}}


def answer(commands=None):
    return {'schemaVersion': 1, 'requestId': 'request-1', 'message': 'Opening Botany.',
            'batch': {'schemaVersion': 1, 'requestId': 'request-1', 'expectedRevision': 42,
                      'commands': commands or [{'type': 'subject.open', 'subject': 'BOTANY'}]}}


def client(handler, **settings):
    return TestClient(create_app(Settings(TOKEN, gemini_key='test-gemini', groq_key='test-groq',
                                          gemini_model='configured-model', **settings),
                                 httpx.MockTransport(handler)))


def gemini_response(payload):
    return httpx.Response(200, json={'candidates': [{'finishReason': 'STOP',
        'content': {'parts': [{'text': json.dumps(payload)}]}}]})


def test_authentication_precedes_provider_or_body_parsing():
    def never(_):
        pytest.fail('Unauthorized request reached provider')
    with client(never) as api:
        assert api.get('/healthz').json() == {'status': 'ok'}
        assert api.post('/v1/mentor', content=b'invalid-json').status_code == 401
        assert api.get('/v1/status', headers={'Authorization': 'Bearer wrong'}).status_code == 401
        status = api.get('/v1/status', headers=AUTH)
        assert status.json()['pageEngineeringAvailable'] is True
        assert 'test-gemini' not in status.text


def test_gemini_receives_context_and_server_credentials_and_returns_revision_bound_tools():
    def provider(wire):
        assert wire.url.host == 'generativelanguage.googleapis.com'
        assert wire.headers['x-goog-api-key'] == 'test-gemini'
        assert 'test-gemini' not in str(wire.url)
        body = json.loads(wire.content)
        assert json.loads(body['contents'][0]['parts'][0]['text']) == {
            **request(), 'sourceContext': {}, 'executionResults': []}
        assert body['generationConfig']['responseMimeType'] == 'application/json'
        assert 'tools' not in body  # no code-execution tools
        return gemini_response(answer())
    with client(provider) as api:
        response = api.post('/v1/mentor', headers=AUTH, json=request())
        assert response.status_code == 200
        assert response.json() == answer()


@pytest.mark.parametrize('mutation', ['revision', 'request', 'unknown', 'disabled', 'fields', 'type', 'entity'])
def test_invalid_provider_actions_are_rejected_atomically(mutation):
    payload = answer()
    if mutation == 'revision': payload['batch']['expectedRevision'] = 41
    if mutation == 'request': payload['requestId'] = 'another-request'
    if mutation == 'unknown': payload['batch']['commands'] = [{'type': 'run_script', 'code': 'bad'}]
    if mutation == 'disabled': payload['batch']['commands'] = [{'type': 'app.back'}]
    if mutation == 'fields': payload['batch']['commands'][0]['extra'] = 'bad'
    if mutation == 'type': payload['batch']['commands'][0]['subject'] = 1
    if mutation == 'entity': payload['batch']['commands'][0]['subject'] = 'INVENTED'
    with client(lambda _: gemini_response(payload)) as api:
        response = api.post('/v1/mentor', headers=AUTH, json=request())
        assert response.status_code == 502
        assert 'batch' not in response.json()


def test_clarification_without_actions_is_valid():
    payload = {**answer(), 'message': 'Which PDF do you want?', 'batch': None}
    with client(lambda _: gemini_response(payload)) as api:
        assert api.post('/v1/mentor', headers=AUTH, json=request()).json() == payload


@pytest.mark.parametrize('enabled,expected', [(True, 200), (False, 200), ('true', 502)])
def test_voice_mode_action_requires_boolean_and_advertised_capability(enabled, expected):
    payload = answer([{'type': 'voice.auto_send', 'enabled': enabled}, {'type': 'pdf.next'}])
    context = request()
    context['capabilityContext']['actions'].append({'id': 'voice.auto_send', 'enabled': True})
    with client(lambda _: gemini_response(payload)) as api:
        response = api.post('/v1/mentor', headers=AUTH, json=context)
        assert response.status_code == expected
        if expected == 200:
            assert response.json()['batch']['commands'] == payload['batch']['commands']
        else:
            assert 'batch' not in response.json()


@pytest.mark.parametrize('status,expected', [(429, 429), (403, 502), (500, 502)])
def test_provider_errors_do_not_leak_or_retry(status, expected):
    calls = []
    def provider(wire):
        calls.append(wire)
        return httpx.Response(status, text='private-provider-body', headers={'retry-after': '120'})
    with client(provider) as api:
        response = api.post('/v1/mentor', headers=AUTH, json=request())
        assert response.status_code == expected
        assert 'private-provider-body' not in response.text
        assert len(calls) == 1
        if status == 429: assert response.headers['retry-after'] == '120'


def test_truncated_response_is_not_executed():
    response = gemini_response(answer())
    data = response.json(); data['candidates'][0]['finishReason'] = 'MAX_TOKENS'
    with client(lambda _: httpx.Response(200, json=data)) as api:
        assert api.post('/v1/mentor', headers=AUTH, json=request()).status_code == 502


def test_transcription_uses_whisper_large_v3_and_does_not_translate():
    def provider(wire):
        assert wire.url.path == '/openai/v1/audio/transcriptions'
        assert wire.headers['authorization'] == 'Bearer test-groq'
        assert b'whisper-large-v3' in wire.content
        assert b'name="language"' not in wire.content  # auto detection by default
        return httpx.Response(200, json={'text': 'Agla page kholo', 'language': 'hindi', 'duration': 3.2})
    with client(provider) as api:
        response = api.post('/v1/transcribe', headers=AUTH, files={'file': ('voice.m4a', b'audio')})
        assert response.status_code == 200
        assert response.json()['text'] == 'Agla page kholo'


def test_audio_validation_and_missing_configuration():
    with TestClient(create_app(Settings(TOKEN))) as api:
        assert api.post('/v1/mentor', headers=AUTH, json=request()).status_code == 503
        assert api.post('/v1/transcribe', headers=AUTH, files={'file': ('x.exe', b'audio')}).status_code == 415
        assert api.post('/v1/transcribe', headers=AUTH, files={'file': ('x.wav', b'')}).status_code == 422
        assert api.post('/v1/transcribe', headers=AUTH, files={'file': ('x.wav', b'audio')}).status_code == 503


def test_request_budget_prevents_more_provider_calls():
    with client(lambda _: gemini_response(answer()), requests_per_minute=1) as api:
        assert api.post('/v1/mentor', headers=AUTH, json=request()).status_code == 200
        assert api.post('/v1/mentor', headers=AUTH, json=request()).status_code == 429


def test_body_limit_catches_chunked_request():
    with client(lambda _: gemini_response(answer())) as api:
        chunks = (b'x' * 1024 * 1024 for _ in range(17))
        assert api.post('/v1/mentor', headers=AUTH, content=chunks).status_code == 413


def test_settings_fail_closed_and_repr_redacts_secrets():
    with pytest.raises(ValueError): Settings('short')
    with pytest.raises(ValueError): Settings(TOKEN, gemini_model='../arbitrary-url')
    assert TOKEN not in repr(Settings(TOKEN, gemini_key='secret-gemini'))
    assert 'secret-gemini' not in repr(Settings(TOKEN, gemini_key='secret-gemini'))


@pytest.mark.parametrize('mutation', ['version', 'context_version', 'availability'])
def test_boolean_versions_and_malformed_availability_are_rejected(mutation):
    body = request()
    if mutation == 'version': body['schemaVersion'] = True
    if mutation == 'context_version': body['capabilityContext']['schemaVersion'] = True
    if mutation == 'availability': body['capabilityContext']['actions'][0]['enabled'] = 'true'
    with client(lambda _: pytest.fail('Invalid context reached provider')) as api:
        assert api.post('/v1/mentor', headers=AUTH, json=body).status_code == 422
