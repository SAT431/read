import httpx
from service.errors import ProviderFailure, check_provider_status


class GroqTranscriber:
    def __init__(self, settings, client: httpx.AsyncClient):
        self.settings, self.client = settings, client

    async def transcribe(self, audio: bytes, suffix: str, language: str | None):
        if not self.settings.groq_key:
            raise ProviderFailure(503, 'Groq is not configured on the private service')
        data = {'model': 'whisper-large-v3', 'response_format': 'verbose_json', 'temperature': '0',
                'prompt': 'NEET study conversation. Physics, Chemistry, Botany, Zoology. NCERT. PDF. Chapter. Page.'}
        if language:
            data['language'] = language
        try:
            response = await self.client.post('https://api.groq.com/openai/v1/audio/transcriptions',
                headers={'Authorization': 'Bearer ' + self.settings.groq_key}, data=data,
                files={'file': ('recording.' + suffix, audio, 'application/octet-stream')})
            check_provider_status(response)
            result = response.json()
            text = result['text']
            if not isinstance(text, str) or len(text) > 20_000:
                raise ValueError('Invalid transcription')
            # Silence is represented as an empty transcript, never an invented command.
            return {'schemaVersion': 1, 'text': text, 'language': result.get('language'),
                    'durationSeconds': result.get('duration')}
        except httpx.TimeoutException:
            raise ProviderFailure(504, 'Groq transcription timed out') from None
        except httpx.HTTPError:
            raise ProviderFailure(502, 'Groq is unreachable') from None
        except (ValueError, KeyError, TypeError):
            raise ProviderFailure(502, 'Groq returned an invalid transcription') from None
