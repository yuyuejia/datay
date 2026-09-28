import { describe, expect, it } from 'vitest';

import { buildSimpleUrl, isNetworkType, parseSimpleUrl } from './db-url.util';

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

    it('returns null for tns alias url', () => {
      expect(parseSimpleUrl('ORACLE', 'jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)))')).toBeNull();
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
