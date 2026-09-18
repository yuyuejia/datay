import { describe, expect, it } from 'vitest';
import { parseCurl, tokenizeCurl } from './curl';

describe('tokenizeCurl', () => {
  it('should split plain arguments', () => {
    expect(tokenizeCurl('curl -X POST https://a.com')).toEqual(['curl', '-X', 'POST', 'https://a.com']);
  });

  it('should keep quoted values as a single token', () => {
    expect(tokenizeCurl(`curl -H 'Content-Type: application/json' "https://a.com"`)).toEqual([
      'curl',
      '-H',
      'Content-Type: application/json',
      'https://a.com',
    ]);
  });

  it('should support line continuations', () => {
    expect(tokenizeCurl('curl -X POST \\\n  -H "A: b" \\\n  https://a.com')).toEqual(['curl', '-X', 'POST', '-H', 'A: b', 'https://a.com']);
  });

  it('should keep an empty quoted argument', () => {
    expect(tokenizeCurl(`curl -d '' https://a.com`)).toEqual(['curl', '-d', '', 'https://a.com']);
  });
});

describe('parseCurl', () => {
  it('should parse a simple GET request', () => {
    const result = parseCurl('curl https://api.example.com/users');
    expect(result.url).toBe('https://api.example.com/users');
    expect(result.method).toBe('GET');
    expect(result.headers).toEqual({});
  });

  it('should parse a POST request with headers and body', () => {
    const result = parseCurl(
      `curl -X POST 'https://api.example.com/users' -H 'Content-Type: application/json' -H "Authorization: Bearer abc" -d '{"name":"tom"}'`,
    );
    expect(result.url).toBe('https://api.example.com/users');
    expect(result.method).toBe('POST');
    expect(result.headers).toEqual({ 'Content-Type': 'application/json', Authorization: 'Bearer abc' });
    expect(result.body).toBe('{"name":"tom"}');
  });

  it('should infer POST when data is provided without -X', () => {
    const result = parseCurl(`curl https://a.com -d 'a=1'`);
    expect(result.method).toBe('POST');
    expect(result.body).toBe('a=1');
  });

  it('should join multiple data arguments with ampersand', () => {
    const result = parseCurl(`curl https://a.com -d 'a=1' -d 'b=2'`);
    expect(result.body).toBe('a=1&b=2');
  });

  it('should handle --json shorthand', () => {
    const result = parseCurl(`curl https://a.com --json '{"a":1}'`);
    expect(result.method).toBe('POST');
    expect(result.body).toBe('{"a":1}');
    expect(result.headers['Content-Type']).toBe('application/json');
    expect(result.headers.Accept).toBe('application/json');
  });

  it('should support inline --header=value syntax', () => {
    const result = parseCurl(`curl --request=PUT --header=X-Token:123 https://a.com`);
    expect(result.method).toBe('PUT');
    expect(result.headers['X-Token']).toBe('123');
  });

  it('should parse basic auth', () => {
    const result = parseCurl(`curl -u user:pass https://a.com`);
    expect(result.headers.Authorization).toBe(`Basic ${btoa('user:pass')}`);
  });

  it('should move data to query string when -G is used', () => {
    const result = parseCurl(`curl -G https://a.com/search -d 'q=abc' -d 'p=1'`);
    expect(result.method).toBe('GET');
    expect(result.url).toBe('https://a.com/search?q=abc&p=1');
    expect(result.body).toBeUndefined();
  });

  it('should disable ssl verification with -k', () => {
    const result = parseCurl(`curl -k https://a.com`);
    expect(result.sslVerify).toBe(false);
  });

  it('should parse timeouts in seconds to milliseconds', () => {
    expect(parseCurl(`curl --max-time 5 https://a.com`).timeout).toBe(5000);
    expect(parseCurl(`curl --connect-timeout 2 https://a.com`).timeout).toBe(2000);
    expect(parseCurl(`curl --connect-timeout 2 --max-time 5 https://a.com`).timeout).toBe(5000);
  });

  it('should warn about unsupported form data', () => {
    const result = parseCurl(`curl -F 'file=@a.txt' https://a.com`);
    expect(result.warnings.length).toBe(1);
  });

  it('should handle multi-line commands', () => {
    const result = parseCurl(`curl -X POST 'https://a.com' \\
      -H 'Content-Type: application/json' \\
      -d '{"a":1}'`);
    expect(result.url).toBe('https://a.com');
    expect(result.method).toBe('POST');
    expect(result.body).toBe('{"a":1}');
  });

  it('should return empty result for empty input', () => {
    const result = parseCurl('   ');
    expect(result.url).toBeUndefined();
    expect(result.method).toBeUndefined();
  });
});
