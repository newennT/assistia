IF OBJECT_ID('dbo.users', 'U') IS NULL
BEGIN
    CREATE TABLE users (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        email NVARCHAR(255) NOT NULL UNIQUE,
        first_name NVARCHAR(100) NOT NULL,
        last_name NVARCHAR(100) NOT NULL,
        role NVARCHAR(30) NOT NULL,
        department NVARCHAR(100) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
END;

IF COL_LENGTH('dbo.tickets', 'creator_id') IS NULL
BEGIN
    ALTER TABLE tickets ADD creator_id BIGINT NULL;
END;

IF COL_LENGTH('dbo.tickets', 'assignee_id') IS NULL
BEGIN
    ALTER TABLE tickets ADD assignee_id BIGINT NULL;
END;

IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'system@assistia.local')
BEGIN
    INSERT INTO users (email, first_name, last_name, role, department)
    VALUES ('system@assistia.local', 'System', 'Administrator', 'ADMIN', 'IT');
END;

EXEC('UPDATE tickets
SET creator_id = (SELECT TOP (1) id FROM users WHERE email = ''system@assistia.local'')
WHERE creator_id IS NULL;');

ALTER TABLE tickets
    ALTER COLUMN creator_id BIGINT NOT NULL;

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'fk_tickets_creator'
)
BEGIN
    ALTER TABLE tickets
        ADD CONSTRAINT fk_tickets_creator FOREIGN KEY (creator_id) REFERENCES users(id);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'fk_tickets_assignee'
)
BEGIN
    ALTER TABLE tickets
        ADD CONSTRAINT fk_tickets_assignee FOREIGN KEY (assignee_id) REFERENCES users(id);
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'ck_tickets_status'
)
BEGIN
    ALTER TABLE tickets
        ADD CONSTRAINT ck_tickets_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED'));
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'ck_tickets_priority'
)
BEGIN
    ALTER TABLE tickets
        ADD CONSTRAINT ck_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'ck_users_role'
)
BEGIN
    ALTER TABLE users
        ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'TECHNICIAN', 'ADMIN'));
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_tickets_creator' AND object_id = OBJECT_ID('dbo.tickets'))
BEGIN
    CREATE INDEX idx_tickets_creator ON tickets (creator_id);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_tickets_assignee' AND object_id = OBJECT_ID('dbo.tickets'))
BEGIN
    CREATE INDEX idx_tickets_assignee ON tickets (assignee_id);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_tickets_status' AND object_id = OBJECT_ID('dbo.tickets'))
BEGIN
    CREATE INDEX idx_tickets_status ON tickets (status);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_tickets_priority' AND object_id = OBJECT_ID('dbo.tickets'))
BEGIN
    CREATE INDEX idx_tickets_priority ON tickets (priority);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_tickets_category' AND object_id = OBJECT_ID('dbo.tickets'))
BEGIN
    CREATE INDEX idx_tickets_category ON tickets (category);
END;

IF OBJECT_ID('dbo.comments', 'U') IS NULL
BEGIN
    CREATE TABLE comments (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        ticket_id BIGINT NOT NULL,
        author_id BIGINT NOT NULL,
        content NVARCHAR(MAX) NOT NULL,
        is_internal BIT NOT NULL DEFAULT 0,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id),
        CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users(id)
    );
END;

IF OBJECT_ID('dbo.attachments', 'U') IS NULL
BEGIN
    CREATE TABLE attachments (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        ticket_id BIGINT NOT NULL,
        file_name NVARCHAR(255) NOT NULL,
        storage_key NVARCHAR(512) NOT NULL,
        mime_type NVARCHAR(100) NULL,
        file_size_bytes BIGINT NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id)
    );
END;

IF OBJECT_ID('dbo.knowledge_documents', 'U') IS NULL
BEGIN
    CREATE TABLE knowledge_documents (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        title NVARCHAR(255) NOT NULL,
        content NVARCHAR(MAX) NOT NULL,
        category NVARCHAR(30) NOT NULL,
        source_reference NVARCHAR(255) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
END;

IF OBJECT_ID('dbo.ai_analyses', 'U') IS NULL
BEGIN
    CREATE TABLE ai_analyses (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        ticket_id BIGINT NOT NULL,
        summary NVARCHAR(MAX) NOT NULL,
        suggested_category NVARCHAR(40) NULL,
        suggested_priority NVARCHAR(30) NULL,
        possible_causes NVARCHAR(MAX) NULL,
        proposed_actions NVARCHAR(MAX) NULL,
        confidence_score DECIMAL(5,2) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_ai_analyses_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id)
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_comments_ticket' AND object_id = OBJECT_ID('dbo.comments'))
BEGIN
    CREATE INDEX idx_comments_ticket ON comments (ticket_id);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_comments_author' AND object_id = OBJECT_ID('dbo.comments'))
BEGIN
    CREATE INDEX idx_comments_author ON comments (author_id);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_attachments_ticket' AND object_id = OBJECT_ID('dbo.attachments'))
BEGIN
    CREATE INDEX idx_attachments_ticket ON attachments (ticket_id);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_kd_category' AND object_id = OBJECT_ID('dbo.knowledge_documents'))
BEGIN
    CREATE INDEX idx_kd_category ON knowledge_documents (category);
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_ai_analyses_ticket' AND object_id = OBJECT_ID('dbo.ai_analyses'))
BEGIN
    CREATE INDEX idx_ai_analyses_ticket ON ai_analyses (ticket_id);
END;
