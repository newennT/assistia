IF COL_LENGTH('dbo.users', 'password_hash') IS NULL
BEGIN
    ALTER TABLE users ADD password_hash NVARCHAR(100) NULL;
END;
