import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { act, create } from 'react-test-renderer';
import Reports from '../src/features/reports/Reports.jsx';
import useFinancialReport from '../src/hooks/useFinancialReport.js';

const report = (charged) => ({ summary: { charged, allocatedPayments: 0, outstanding: charged, poolRentalCost: 0, clubProfit: 0 }, subscriptions: [], unassignedCharges: [] });
const subscriptions = [{ id: 7, title: 'Ноябрь' }, { id: 8, title: null }];
const text = (node) => node.children.map((child) => typeof child === 'string' ? child : text(child)).join('');
const button = (renderer, label) => renderer.root.findAllByType('button').find((node) => text(node) === label);
const select = (renderer) => renderer.root.findByType('select');
const checked = (renderer) => renderer.root.findAllByProps({ type: 'checkbox' }).map((node) => node.props.checked);
const apply = (renderer) => renderer.root.findByType('form').props.onSubmit({ preventDefault() {} });
const mount = async (api) => {
    let current;
    const errors = [];
    function Harness({ visible = true }) {
        current = useFinancialReport(api, (error) => errors.push(error));
        return visible ? h(Reports, {
            reportState: current.financialReport, loading: current.reportLoading,
            loadReport: current.loadFinancialReport, subscriptions,
        }) : null;
    }
    let renderer;
    await act(async () => { renderer = create(h(Harness)); });
    return { renderer, Harness, current: () => current, errors };
};

test('report hook builds optional status plus repeated IDs and keeps successful applied filters on failure', async () => {
    let state;
    const urls = [];
    const errors = [];
    const api = async (url) => {
        urls.push(url);
        if (url.includes('DRAFT')) throw new Error('Не удалось загрузить');
        return report(100);
    };
    function Harness() { state = useFinancialReport(api, (error) => errors.push(error)); return null; }
    await act(async () => { create(h(Harness)); });
    await act(async () => state.loadFinancialReport());
    assert.equal(urls.at(-1), '/api/reports/financial');
    assert.deepEqual(state.financialReport.subscriptionIds, []);
    assert.equal(state.financialReport.status, '');
    const ids = [7, 8];
    await act(async () => state.loadFinancialReport(ids, 'COMPLETED'));
    ids.push(9);
    assert.equal(urls.at(-1), '/api/reports/financial?subscriptionId=7&subscriptionId=8&status=COMPLETED');
    assert.deepEqual(state.financialReport.subscriptionIds, [7, 8]);
    const previous = state.financialReport;
    await act(async () => state.loadFinancialReport([], 'DRAFT'));
    assert.equal(state.financialReport, previous);
    assert.equal(state.reportLoading, false);
    assert.equal(errors.at(-1), 'Не удалось загрузить');
    await act(async () => state.loadFinancialReport([], 'FINALIZED'));
    assert.equal(urls.at(-1), '/api/reports/financial?status=FINALIZED');
});

test('draft filters do not alter shown report or CSV until apply; export uses applied IDs and status', async (t) => {
    const oldDocument = globalThis.document;
    let downloaded;
    globalThis.document = { createElement: () => ({ click() {} }) };
    t.after(() => { globalThis.document = oldDocument; });
    t.mock.method(URL, 'createObjectURL', (blob) => { downloaded = blob; return 'blob:report'; });
    t.mock.method(URL, 'revokeObjectURL', () => {});
    const urls = [];
    const { renderer, current } = await mount(async (url) => { urls.push(url); return report(url.includes('FINALIZED') ? 200 : 100); });
    assert.deepEqual(select(renderer).findAllByType('option').map((option) => [option.props.value, text(option)]), [
        ['', 'Все'], ['COMPLETED', 'Завершённые'], ['FINALIZED', 'Рассчитанные'], ['DRAFT', 'Черновик'],
    ]);
    await act(async () => select(renderer).props.onChange({ target: { value: 'FINALIZED' } }));
    await act(async () => renderer.root.findAllByProps({ type: 'checkbox' })[1].props.onChange());
    assert.equal(urls.length, 1);
    assert.equal(current().financialReport.data.summary.charged, 100);
    await act(async () => button(renderer, 'Скачать CSV').props.onClick());
    let csv = await downloaded.text();
    assert.ok(csv.includes('"Абонементы","Все абонементы"'));
    assert.ok(csv.includes('"Статус абонементов","Все"'));
    await act(async () => apply(renderer));
    assert.equal(urls.at(-1), '/api/reports/financial?subscriptionId=8&status=FINALIZED');
    assert.equal(current().financialReport.data.summary.charged, 200);
    assert.ok(text(renderer.root).includes('Показано: Без названия · Статус: Рассчитанные'));
    await act(async () => select(renderer).props.onChange({ target: { value: 'COMPLETED' } }));
    await act(async () => button(renderer, 'Все абонементы').props.onClick());
    await act(async () => button(renderer, 'Скачать CSV').props.onClick());
    csv = await downloaded.text();
    assert.ok(csv.includes('"Абонементы","Без названия"'));
    assert.ok(csv.includes('"Статус абонементов","Рассчитанные"'));
    assert.ok(csv.includes('"Начислено","200"'));
    assert.doesNotMatch(csv, /Абонемент #8|Завершённые/);
});

test('failed report apply restores both drafts and keeps the last successful report and labels', async () => {
    const { renderer, current, errors } = await mount(async (url) => {
        if (url.includes('DRAFT')) throw new Error('Ошибка отчёта');
        return report(100);
    });
    await act(async () => select(renderer).props.onChange({ target: { value: 'COMPLETED' } }));
    await act(async () => renderer.root.findAllByProps({ type: 'checkbox' })[0].props.onChange());
    await act(async () => apply(renderer));
    const previous = current().financialReport;
    await act(async () => select(renderer).props.onChange({ target: { value: 'DRAFT' } }));
    await act(async () => renderer.root.findAllByProps({ type: 'checkbox' })[1].props.onChange());
    await act(async () => apply(renderer));
    assert.equal(select(renderer).props.value, 'COMPLETED');
    assert.deepEqual(checked(renderer), [true, false]);
    assert.equal(current().financialReport, previous);
    assert.equal(errors.at(-1), 'Ошибка отчёта');
    assert.ok(text(renderer.root).includes('Показано: Ноябрь · Статус: Завершённые'));
});

test('applied IDs and status persist when leaving reports and returning', async () => {
    const urls = [];
    const { renderer, Harness } = await mount(async (url) => { urls.push(url); return report(0); });
    await act(async () => select(renderer).props.onChange({ target: { value: 'DRAFT' } }));
    await act(async () => renderer.root.findAllByProps({ type: 'checkbox' })[0].props.onChange());
    await act(async () => apply(renderer));
    await act(async () => renderer.update(h(Harness, { visible: false })));
    await act(async () => renderer.update(h(Harness, { visible: true })));
    assert.equal(urls.at(-1), '/api/reports/financial?subscriptionId=7&status=DRAFT');
    assert.equal(select(renderer).props.value, 'DRAFT');
    assert.deepEqual(checked(renderer), [true, false]);
    assert.ok(text(renderer.root).includes('Абонементы не найдены.'));
});

test('loading disables status, ID controls, apply and export until the response completes', async () => {
    let resolveRequest;
    let calls = 0;
    const { renderer } = await mount(async () => {
        if (++calls === 1) return report(100);
        return new Promise((resolve) => { resolveRequest = resolve; });
    });
    await act(async () => select(renderer).props.onChange({ target: { value: 'FINALIZED' } }));
    let request;
    await act(async () => { request = apply(renderer); });
    assert.equal(select(renderer).props.disabled, true);
    assert.ok(renderer.root.findAllByProps({ type: 'checkbox' }).every((input) => input.props.disabled));
    assert.equal(button(renderer, 'Все абонементы').props.disabled, true);
    assert.equal(button(renderer, 'Показать').props.disabled, true);
    assert.equal(button(renderer, 'Скачать CSV').props.disabled, true);
    await act(async () => { resolveRequest(report(200)); await request; });
    assert.equal(select(renderer).props.disabled, false);
    assert.equal(button(renderer, 'Показать').props.disabled, false);
    assert.equal(button(renderer, 'Скачать CSV').props.disabled, false);
});
