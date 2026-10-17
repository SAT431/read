import json
from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)

    @field_validator('schemaVersion', mode='before', check_fields=False)
    @classmethod
    def integer_version(cls, value):
        if type(value) is not int:
            raise ValueError('Schema version must be an integer')
        return value


class MentorRequest(StrictModel):
    schemaVersion: Literal[1] = 1
    requestId: str = Field(min_length=1, max_length=100, pattern=r'^[a-zA-Z0-9_-]+$')
    utterance: str = Field(min_length=1, max_length=4000)
    capabilityContext: dict[str, Any]
    sourceContext: dict[str, Any] = Field(default_factory=dict)
    executionResults: list[dict[str, Any]] = Field(default_factory=list, max_length=8)

    @field_validator('capabilityContext')
    @classmethod
    def context_valid(cls, value):
        if type(value.get('schemaVersion')) is not int or value['schemaVersion'] != 1 or type(value.get('revision')) is not int or value['revision'] < 0:
            raise ValueError('Invalid capability context version/revision')
        if not isinstance(value.get('actions'), list) or len(value['actions']) > 200:
            raise ValueError('Invalid capability actions')
        for action in value['actions']:
            if not isinstance(action, dict) or not isinstance(action.get('id'), str):
                raise ValueError('Invalid capability action')
            if 'enabled' in action and type(action['enabled']) is not bool:
                raise ValueError('Invalid capability availability')
        if len(json.dumps(value).encode()) > 200_000:
            raise ValueError('Capability context too large')
        return value

    @field_validator('sourceContext', 'executionResults')
    @classmethod
    def source_bounded(cls, value):
        if len(json.dumps(value).encode()) > 200_000:
            raise ValueError('Source/result context too large')
        return value


class AppBatch(StrictModel):
    schemaVersion: Literal[1] = 1
    requestId: str
    expectedRevision: int = Field(ge=0)
    commands: list[dict[str, Any]] = Field(min_length=1, max_length=24)


class MentorResponse(StrictModel):
    schemaVersion: Literal[1] = 1
    requestId: str
    message: str = Field(min_length=1, max_length=12000)
    batch: AppBatch | None
