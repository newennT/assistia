IF COL_LENGTH('dbo.ai_analyses', 'model_used') IS NULL
BEGIN
    ALTER TABLE dbo.ai_analyses
        ADD model_used NVARCHAR(100) NOT NULL
            CONSTRAINT df_ai_analyses_model_used DEFAULT 'unknown';
END;

ALTER TABLE dbo.ai_analyses
    ALTER COLUMN confidence_score DECIMAL(5,4) NULL;

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'ck_ai_analyses_confidence_range'
)
BEGIN
    ALTER TABLE dbo.ai_analyses
        ADD CONSTRAINT ck_ai_analyses_confidence_range
        CHECK (confidence_score IS NULL OR confidence_score BETWEEN 0 AND 1);
END;
