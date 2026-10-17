from fastapi import HTTPException


class ProviderFailure(HTTPException):
    """Only sanitized service errors reach the client; provider bodies never do."""

    def __init__(self, status: int, message: str, retry_after: str | None = None):
        headers = {'Retry-After': retry_after} if retry_after else None
        super().__init__(status, message, headers)


def check_provider_status(response):
    if response.status_code == 429:
        delay = response.headers.get('retry-after', '60')
        delay = delay if delay.isdigit() and 0 < int(delay) <= 3600 else '60'
        raise ProviderFailure(429, 'Provider quota reached. No alternate paid provider was called.', delay)
    if response.status_code != 200:
        raise ProviderFailure(502, 'Provider request failed. App state is unchanged.')
