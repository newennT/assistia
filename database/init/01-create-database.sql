IF NOT EXISTS (
    SELECT name
    FROM sys.databases
    WHERE name = 'AssistIA'
)
BEGIN
    CREATE DATABASE AssistIA;
END