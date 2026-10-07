CREATE TABLE tickets (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    description NVARCHAR(MAX) NOT NULL,
    status NVARCHAR(50) NOT NULL,
    priority NVARCHAR(50) NOT NULL,
    category NVARCHAR(100),
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

INSERT INTO tickets (
    title,
    description,
    status,
    priority,
    category
)
VALUES (
    'Impossible de se connecter au VPN',
    'Le VPN ne fonctionne plus depuis ce matin.',
    'OPEN',
    'HIGH',
    'VPN'
);