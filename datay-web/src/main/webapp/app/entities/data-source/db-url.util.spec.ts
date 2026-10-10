import { describe, expect, it } from 'vitest';

import { buildSimpleUrl, isNetworkType, parseSimpleUrl, renderUrlTemplate } from './db-url.util';

describe('db-url.util', () => {
  describe('buildSimpleUrl', () => {
    it('builds oracle service name url', () => {
      expect(
        buildSimpleUrl('ORACLE', {
          hostname: '172.20.36.17',
          port: '1521',
          database: 'orapdb',
          oracleIdentifierType: 'service',
        }),
      ).toBe('jdbc:oracle:thin:@//172.20.36.17:1521/orapdb');
    });

    it('builds oracle sid url', () => {
      expect(
        buildSimpleUrl('ORACLE', {
          hostname: '172.20.36.17',
          port: '1521',
          database: 'orapdb',
          oracleIdentifierType: 'sid',
        }),
      ).toBe('jdbc:oracle:thin:@172.20.36.17:1521:orapdb');
    });

    it('builds mysql 8.x url with ssl', () => {
      expect(buildSimpleUrl('MYSQL', { hostname: 'localhost', port: '3306', database: 'test_db' }, '8.0')).toBe(
        'jdbc:mysql://localhost:3306/test_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=true',
      );
    });

    it('builds mysql 5.x url with utc and no ssl', () => {
      expect(buildSimpleUrl('MYSQL', { hostname: 'localhost', port: '3306', database: 'test_db' }, '5.7')).toBe(
        'jdbc:mysql://localhost:3306/test_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false',
      );
    });

    it('builds dm url without database', () => {
      expect(buildSimpleUrl('DM', { hostname: 'localhost', port: '5236', database: 'SYSDBA' })).toBe(
        'jdbc:dm://localhost:5236',
      );
    });
  });

  describe('parseSimpleUrl', () => {
    it('parses oracle service name url', () => {
      expect(parseSimpleUrl('ORACLE', 'jdbc:oracle:thin:@//172.20.36.17:1521/orapdb')).toEqual({
        hostname: '172.20.36.17',
        port: '1521',
        database: 'orapdb',
        oracleIdentifierType: 'service',
      });
    });

    it('parses oracle sid url', () => {
      expect(parseSimpleUrl('ORACLE', 'jdbc:oracle:thin:@172.20.36.17:1521:orapdb')).toEqual({
        hostname: '172.20.36.17',
        port: '1521',
        database: 'orapdb',
        oracleIdentifierType: 'sid',
      });
    });

    it('parses postgresql url', () => {
      expect(parseSimpleUrl('POSTGRESQL', 'jdbc:postgresql://localhost:5432/testdb')).toEqual({
        hostname: 'localhost',
        port: '5432',
        database: 'testdb',
      });
    });

    it('parses sqlserver url', () => {
      expect(parseSimpleUrl('SQLSERVER', 'jdbc:sqlserver://localhost:1433;databaseName=testdb')).toEqual({
        hostname: 'localhost',
        port: '1433',
        database: 'testdb',
      });
    });

    it('parses dm url without database', () => {
      expect(parseSimpleUrl('DM', 'jdbc:dm://localhost:5236')).toEqual({
        hostname: 'localhost',
        port: '5236',
        database: '',
      });
    });

    it('returns null for tns alias url', () => {
      expect(parseSimpleUrl('ORACLE', 'jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)))')).toBeNull();
    });
  });

  describe('renderUrlTemplate', () => {
    it('renders host and port', () => {
      expect(renderUrlTemplate('jdbc:dm://{host}:{port}', { host: 'localhost', port: '5236' })).toBe('jdbc:dm://localhost:5236');
    });

    it('drops optional port segment when empty', () => {
      expect(renderUrlTemplate('quack:{host}:{port}', { host: 'localhost', port: '' })).toBe('quack:localhost');
      expect(renderUrlTemplate('quack:{host}:{port}', { host: 'localhost', port: '9494' })).toBe('quack:localhost:9494');
    });

    it('keeps trailing colon for empty duckdb file (memory)', () => {
      expect(renderUrlTemplate('jdbc:duckdb:{file}', { file: '' })).toBe('jdbc:duckdb:');
      expect(renderUrlTemplate('jdbc:duckdb:{file}', { file: '/data/a.duckdb' })).toBe('jdbc:duckdb:/data/a.duckdb');
    });

    it('drops optional database segment when empty', () => {
      expect(renderUrlTemplate('jdbc:mysql://{host}:{port}/{database}', { host: 'h', port: '3306', database: '' })).toBe(
        'jdbc:mysql://h:3306',
      );
      expect(renderUrlTemplate('jdbc:mysql://{host}:{port}/{database}', { host: 'h', port: '3306', database: 'db' })).toBe(
        'jdbc:mysql://h:3306/db',
      );
    });
  });

  describe('isNetworkType', () => {
    it('treats regular databases as network types', () => {
      expect(isNetworkType('MYSQL')).toBe(true);
      expect(isNetworkType('ORACLE')).toBe(true);
      expect(isNetworkType('POSTGRESQL')).toBe(true);
    });

    it('excludes duckdb and ducklake', () => {
      expect(isNetworkType('DUCKDB')).toBe(false);
      expect(isNetworkType('DUCKLAKE')).toBe(false);
    });
  });
});
