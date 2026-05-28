from pydantic import BaseModel, Field
from typing import List


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, description="Natural language search query")
    top_k: int = Field(default=10, ge=1, le=100)
    album_ids: List[str] = Field(default_factory=list)


class SearchResultItem(BaseModel):
    item_id: str
    album_id: str
    score: float


class SearchResponse(BaseModel):
    success: bool
    message: str
    query: str
    top_k: int
    results: List[SearchResultItem]


class HealthResponse(BaseModel):
    status: str