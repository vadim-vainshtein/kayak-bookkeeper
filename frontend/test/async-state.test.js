import test from 'node:test';
import assert from 'node:assert/strict';
import { createMutationRunner } from '../src/hooks/mutationRunner.js';
import { createBalanceSelection } from '../src/features/ledger/balanceSelection.js';

const deferred = () => {
    let resolve, reject;
    const promise = new Promise((yes, no) => {
        resolve = yes;
        reject = no;
    });
    return { promise, resolve, reject };
};

test('shared mutation runner prevents duplicate submissions and unlocks after success or failure', async () => {
    const submitting = [],
        errors = [],
        messages = [];
    const run = createMutationRunner({
        setSubmitting: (v) => submitting.push(v),
        setError: (v) => errors.push(v),
        setMessage: (v) => messages.push(v),
    });
    const request = deferred();
    const first = run(() => request.promise, 'saved');
    let duplicateCalled = false;
    assert.equal(
        await run(() => {
            duplicateCalled = true;
        }, 'duplicate'),
        false,
    );
    assert.equal(duplicateCalled, false);
    request.resolve();
    assert.equal(await first, true);
    assert.equal(
        await run(() => {
            throw Error('failed');
        }),
        false,
    );
    assert.equal(await run(async () => {}, 'retried'), true);
    assert.deepEqual(submitting, [true, false, true, false, true, false]);
    assert.deepEqual(messages, ['saved', 'retried']);
    assert.ok(errors.includes('failed'));
});

test('changing balance selection suppresses out-of-order responses and errors', async () => {
    const requests = [deferred(), deferred(), deferred()];
    const balances = [],
        errors = [],
        people = [];
    let index = 0;
    const selection = createBalanceSelection({
        api: () => requests[index++].promise,
        setBalance: (v) => balances.push(v),
        setError: (v) => errors.push(v),
        setPersonId: (v) => people.push(v),
    });
    const first = selection.selectPerson(1);
    const second = selection.selectPerson(2);
    requests[1].resolve({ person: 2 });
    await second;
    requests[0].reject(Error('obsolete'));
    await first;
    assert.deepEqual(balances, [null, null, { person: 2 }]);
    assert.deepEqual(errors, []);
    const pending = selection.loadBalance(2);
    selection.showPeople();
    requests[2].resolve({ person: 2, stale: true });
    await pending;
    assert.equal(balances.at(-1), null);
    assert.deepEqual(people, [1, 2, null]);
});
