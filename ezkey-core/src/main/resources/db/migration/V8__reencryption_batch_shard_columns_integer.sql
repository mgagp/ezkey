-- Align ezkey_reencryption_batch.shard_* with JPA Integer (PostgreSQL INTEGER / int4).
-- Older V3 revisions used SMALLINT (int2), which fails Hibernate schema validation against Integer fields.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'ezkey_reencryption_batch'
      AND column_name = 'shard_count'
      AND data_type = 'smallint'
  ) THEN
    ALTER TABLE ezkey_reencryption_batch
      ALTER COLUMN shard_count TYPE INTEGER USING shard_count::integer,
      ALTER COLUMN shard_index TYPE INTEGER USING shard_index::integer;
  END IF;
END $$;
