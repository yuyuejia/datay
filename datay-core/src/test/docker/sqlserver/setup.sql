-- SQL Server CDC 本地验证环境初始化脚本
-- 由 start.sh 通过 sqlcmd 执行；需要 sysadmin 权限（sa）

IF DB_ID(N'datay_cdc') IS NULL
BEGIN
    CREATE DATABASE datay_cdc;
END;
GO

USE datay_cdc;
GO

-- 1. 开启数据库 CDC
IF (SELECT is_cdc_enabled FROM sys.databases WHERE database_id = DB_ID()) = 0
BEGIN
    EXEC sys.sp_cdc_enable_db;
END;
GO

-- 2. 创建采集账号并授予 db_owner（本地验证用；生产环境请按文档授予最小权限）
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'datay')
BEGIN
    CREATE LOGIN datay WITH PASSWORD = N'Datay123456!', CHECK_POLICY = OFF;
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'datay')
BEGIN
    CREATE USER datay FOR LOGIN datay;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.database_role_members drm
    JOIN sys.database_principals r ON r.principal_id = drm.role_principal_id
    JOIN sys.database_principals u ON u.principal_id = drm.member_principal_id
    WHERE r.name = N'db_owner' AND u.name = N'datay'
)
BEGIN
    ALTER ROLE db_owner ADD MEMBER datay;
END;
GO

-- 3. 重建测试表（重复执行时先关闭 CDC）
IF EXISTS (
    SELECT 1
    FROM cdc.change_tables ct
    JOIN sys.tables t ON t.object_id = ct.source_object_id
    JOIN sys.schemas s ON s.schema_id = t.schema_id
    WHERE s.name = N'dbo' AND t.name = N'CDC_DEMO'
)
BEGIN
    EXEC sys.sp_cdc_disable_table
        @source_schema = N'dbo',
        @source_name = N'CDC_DEMO',
        @capture_instance = N'all';
END;
GO

IF OBJECT_ID(N'dbo.CDC_DEMO', N'U') IS NOT NULL
BEGIN
    DROP TABLE dbo.CDC_DEMO;
END;
GO

CREATE TABLE dbo.CDC_DEMO (
    ID          INT PRIMARY KEY,
    NAME        NVARCHAR(100),
    AMOUNT      DECIMAL(10,2),
    UPDATED_AT  DATETIME2(3)
);
GO

EXEC sys.sp_cdc_enable_table
    @source_schema = N'dbo',
    @source_name = N'CDC_DEMO',
    @role_name = NULL,
    @supports_net_changes = 0;
GO

IF EXISTS (
    SELECT 1
    FROM cdc.change_tables ct
    JOIN sys.tables t ON t.object_id = ct.source_object_id
    JOIN sys.schemas s ON s.schema_id = t.schema_id
    WHERE s.name = N'dbo' AND t.name = N'CDC_TYPES'
)
BEGIN
    EXEC sys.sp_cdc_disable_table
        @source_schema = N'dbo',
        @source_name = N'CDC_TYPES',
        @capture_instance = N'all';
END;
GO

IF OBJECT_ID(N'dbo.CDC_TYPES', N'U') IS NOT NULL
BEGIN
    DROP TABLE dbo.CDC_TYPES;
END;
GO

CREATE TABLE dbo.CDC_TYPES (
    ID              INT PRIMARY KEY,
    C_TINYINT       TINYINT,
    C_SMALLINT      SMALLINT,
    C_INT           INT,
    C_BIGINT        BIGINT,
    C_DECIMAL       DECIMAL(18,4),
    C_NUMERIC       NUMERIC(18,4),
    C_MONEY         MONEY,
    C_FLOAT         FLOAT,
    C_REAL          REAL,
    C_BIT           BIT,
    C_CHAR          CHAR(10),
    C_VARCHAR       VARCHAR(100),
    C_NCHAR         NCHAR(10),
    C_NVARCHAR      NVARCHAR(100),
    C_DATE          DATE,
    C_TIME          TIME(0),
    C_DATETIME2     DATETIME2(3),
    C_DATETIMEOFFSET DATETIMEOFFSET(3),
    C_UUID          UNIQUEIDENTIFIER,
    C_VARBINARY     VARBINARY(100)
);
GO

EXEC sys.sp_cdc_enable_table
    @source_schema = N'dbo',
    @source_name = N'CDC_TYPES',
    @role_name = NULL,
    @supports_net_changes = 0;
GO

-- 4. 校验
-- 注意：sp_cdc_enable_table 会自动创建并启动 capture/cleanup 作业，
-- 不要再次执行 sp_cdc_start_job，否则 Agent 会返回“job already has a pending request”。
SELECT name, is_cdc_enabled
FROM sys.databases
WHERE name = N'datay_cdc';
GO

SELECT s.name AS schema_name, t.name AS table_name, t.is_tracked_by_cdc
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE s.name = N'dbo' AND t.name IN (N'CDC_DEMO', N'CDC_TYPES');
GO

EXEC sys.sp_cdc_help_jobs;
GO
