from typing import List, Optional

from pydantic import BaseModel, Field


# ── CLIP semantic search ──────────────────────────────────────────────────────

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


# ── Batch indexing ────────────────────────────────────────────────────────────

class BatchIndexItemResult(BaseModel):
    image_id: str
    success: bool
    message: str


class BatchIndexResponse(BaseModel):
    success: bool
    message: str
    results: List[BatchIndexItemResult]


class FaceBatchIndexItemResult(BaseModel):
    image_id: str
    success: bool
    face_count: int
    message: str


class FaceBatchIndexResponse(BaseModel):
    success: bool
    message: str
    results: List[FaceBatchIndexItemResult]


# ── Face indexing / search ────────────────────────────────────────────────────

class FaceSearchResultItem(BaseModel):
    item_id: str
    album_id: str
    score: float
    matched_bbox: List[int]  # [x1, y1, x2, y2]


class FaceSearchResponse(BaseModel):
    success: bool
    message: str
    results: List[FaceSearchResultItem]


# ── Health ────────────────────────────────────────────────────────────────────

class HealthResponse(BaseModel):
    status: str
