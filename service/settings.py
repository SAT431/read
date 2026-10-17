import os
import re
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Settings:
    access_token: str = field(repr=False)
    gemini_key: str = field(default='', repr=False)
    groq_key: str = field(default='', repr=False)
    gemini_model: str = ''
    requests_per_minute: int = 12
    concurrent_requests: int = 2
    data_root: str = 'data/native-service'

    def __post_init__(self):
        if len(self.access_token) < 32 or not all(33 <= ord(c) <= 126 for c in self.access_token):
            raise ValueError('FOLIO_ACCESS_TOKEN must contain at least 32 characters')
        if self.gemini_model and not re.fullmatch(r'[a-zA-Z0-9._-]{1,100}', self.gemini_model):
            raise ValueError('Invalid GEMINI_MODEL identifier')
        if not 1 <= self.requests_per_minute <= 120 or not 1 <= self.concurrent_requests <= 8:
            raise ValueError('Invalid service request limits')

    @classmethod
    def from_environment(cls):
        return cls(access_token=os.getenv('FOLIO_ACCESS_TOKEN', ''),
                   gemini_key=os.getenv('GEMINI_API_KEY', ''), groq_key=os.getenv('GROQ_API_KEY', ''),
                   gemini_model=os.getenv('GEMINI_MODEL', ''),
                   requests_per_minute=int(os.getenv('FOLIO_REQUESTS_PER_MINUTE', '12')),
                   data_root=os.getenv('FOLIO_DATA_ROOT', 'data/native-service'))
