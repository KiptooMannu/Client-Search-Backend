-- Align documents table with entity mapping
-- Entity has: name field -> document_name column, no verified column
-- V1 schema has: name column (NOT NULL), verified column

-- Copy data from "name" to "document_name" only if BOTH columns exist
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'documents' AND column_name = 'document_name')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'documents' AND column_name = 'name') THEN
        UPDATE documents SET document_name = COALESCE(document_name, name) WHERE document_name IS NULL AND name IS NOT NULL;
    END IF;
END $$;

-- Drop the old "name" column since entity uses "document_name"
ALTER TABLE documents DROP COLUMN IF EXISTS name;

-- Drop the "verified" column - entity uses verifiedBy/verifiedAt instead
ALTER TABLE documents DROP COLUMN IF EXISTS verified;

-- Ensure document_name is nullable for documents inserted without explicit name
ALTER TABLE documents ALTER COLUMN document_name DROP NOT NULL;