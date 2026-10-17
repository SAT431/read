from typing import Literal
from pydantic import Field
from service.protocol.envelopes import StrictModel


class DraftElement(StrictModel):
    kind: Literal['HEADING', 'PARAGRAPH', 'FIGURE', 'CAPTION', 'TABLE', 'LIST']
    text: str = Field(max_length=100000)
    bounds: list[float] = Field(min_length=4, max_length=4)
    evidenceBlockIds: list[str] = Field(default_factory=list, max_length=200)


class PageDraft(StrictModel):
    title: str = Field(min_length=1, max_length=300)
    elements: list[DraftElement] = Field(min_length=1, max_length=1000)
    uncertainties: list[str] = Field(default_factory=list, max_length=300)
