-- Track face indexing per media item, mirroring the CLIP indexing lifecycle
-- (status / indexed-at / error), so face indexing can be incremental and the
-- admin UI can show the same metadata for both.

ALTER TABLE media_items ADD COLUMN IF NOT EXISTS face_indexing_status VARCHAR(30) NOT NULL DEFAULT 'NOT_INDEXED';
ALTER TABLE media_items ADD COLUMN IF NOT EXISTS face_indexed_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE media_items ADD COLUMN IF NOT EXISTS face_indexing_error VARCHAR(2000);

CREATE INDEX IF NOT EXISTS idx_media_face_indexing_status ON media_items (face_indexing_status);

-- Backfill from the old album-level flag: every active photo of an album that
-- was face-indexed is assumed face-indexed. Photos added to such an album after
-- its last face run may be marked INDEXED incorrectly, but the AI service
-- deduplicates per image, so re-running face indexing remains safe and cheap.
UPDATE media_items m
SET face_indexing_status = 'INDEXED',
    face_indexed_at = a.face_indexed_at
FROM albums a
WHERE m.album_id = a.id
  AND a.face_indexed_at IS NOT NULL
  AND m.deleted_in_drive = FALSE
  AND m.face_indexing_status = 'NOT_INDEXED';

UPDATE media_items SET face_indexing_status = 'DELETED' WHERE deleted_in_drive = TRUE;
