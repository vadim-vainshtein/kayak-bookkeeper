import test from 'node:test';
import assert from 'node:assert/strict';
import { basicAuth, createApi } from '../src/api.js';

test('basic authentication encodes Unicode credentials as UTF-8', () => {
    const value = basicAuth('каякер', 'пароль:123');
    assert.equal(Buffer.from(value.slice(6), 'base64').toString('utf8'), 'каякер:пароль:123');
});

test('API preserves request options and headers, handles JSON and empty responses', async (t) => {
    const requests = [];
    t.mock.method(globalThis, 'fetch', async (...args) => {
        requests.push(args);
        return new Response('{"ok":true}');
    });
    const api = createApi('Basic credentials', () => assert.fail('unexpected 401'));
    assert.deepEqual(await api('/api/people', { method: 'POST', body: '{}', headers: { 'X-Test': 'one' } }), {
        ok: true,
    });
    assert.deepEqual(requests[0], [
        '/api/people',
        {
            method: 'POST',
            body: '{}',
            headers: {
                'Content-Type': 'application/json',
                Authorization: 'Basic credentials',
                'X-Test': 'one',
            },
        },
    ]);
    globalThis.fetch = async () => new Response(null, { status: 204 });
    assert.equal(await api('/empty'), null);
});

test('API expires unauthorized sessions and propagates domain or fallback errors', async (t) => {
    let unauthorized = 0;
    t.mock.method(globalThis, 'fetch', async () => new Response(null, { status: 401 }));
    const api = createApi('Basic invalid', () => unauthorized++);
    await assert.rejects(api('/denied'), /Сессия завершена/);
    assert.equal(unauthorized, 1);
    globalThis.fetch = async () => new Response('{"message":"Domain error"}', { status: 400 });
    await assert.rejects(api('/invalid'), /Domain error/);
    globalThis.fetch = async () => new Response('bad gateway', { status: 502 });
    await assert.rejects(api('/failed'), /Не удалось выполнить запрос/);
});
