-- AC is represented only by the AC amenity to keep one source of truth.
-- V1 seeds AC and its room type assignments before this schema cleanup runs.
ALTER TABLE room_types
    DROP COLUMN has_air_conditioner;
