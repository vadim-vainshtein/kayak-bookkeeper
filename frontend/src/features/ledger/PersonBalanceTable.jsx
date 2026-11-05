import { Fragment, useId, useState } from 'react';
import { balanceClass, balanceLabel, signedBalance, money } from '../../shared/format.js';
import { balanceFilterOptions, filteredPersonBalances, subscriptionKey, subscriptionTitle } from './subscriptionBalances.js';

const BalanceCell = ({ value }) => (
    <td className={balanceClass(value)} aria-label={balanceLabel(value)}>{signedBalance(value)}</td>
);

export default function PersonBalanceTable({ personBalances, subscriptions = [], selectPerson }) {
    const [expanded, setExpanded] = useState([]);
    const [selectedKeys, setSelectedKeys] = useState([]);
    const [search, setSearch] = useState('');
    const tableId = useId();
    const filtered = selectedKeys.length > 0;
    const options = balanceFilterOptions(subscriptions, personBalances);
    const matches = options.filter((option) => option.title.toLocaleLowerCase('ru').includes(search.trim().toLocaleLowerCase('ru')));
    const rows = filteredPersonBalances(personBalances, selectedKeys);
    const toggleSelection = (key) => setSelectedKeys((keys) => keys.includes(key) ? keys.filter((value) => value !== key) : [...keys, key]);
    const toggleExpanded = (id) => setExpanded((ids) => ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id]);
    const selectedLabel = !filtered ? 'Все абонементы' : selectedKeys.length === 1
        ? options.find((option) => option.key === selectedKeys[0])?.title || 'Выбранный абонемент'
        : `Выбрано: ${selectedKeys.length}`;
    return (
        <section className="card">
            <div className="line">
                <h2>Каякеры и балансы</h2>
                <span className="muted" aria-live="polite">{rows.length}</span>
            </div>
            <details className="balance-subscription-picker report-subscription-picker">
                <summary>Абонементы: <strong>{selectedLabel}</strong></summary>
                <div className="balance-filter-content">
                    <label>
                        Поиск абонемента
                        <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} />
                    </label>
                    <button type="button" disabled={!filtered} onClick={() => { setSelectedKeys([]); setSearch(''); }}>Все абонементы</button>
                    <div className="balance-filter-options">
                        {matches.map((option) => (
                            <label className="checkbox" key={option.key}>
                                <input type="checkbox" checked={selectedKeys.includes(option.key)} onChange={() => toggleSelection(option.key)} />
                                {option.title}
                            </label>
                        ))}
                        {!matches.length && <p className="muted">Абонементы не найдены.</p>}
                    </div>
                </div>
            </details>
            <p className="muted balance-table-help" id={`${tableId}-help`}>
                «Оплачено»: в общем итоге — все полученные оплаты, по абонементам и начислениям без абонемента — только зачтённые средства.
                {filtered && ' Показаны итоги по выбранным абонементам; нераспределённые оплаты в них не входят.'}
            </p>
            <div className="balance-table-wrap">
                <table className="balance-table" aria-describedby={`${tableId}-help`}>
                    <thead>
                        <tr>
                            <th scope="col">Каякер</th>
                            <th scope="col">Начислено</th>
                            <th scope="col">Оплачено</th>
                            <th scope="col">Не распределено</th>
                            <th scope="col">Баланс</th>
                        </tr>
                    </thead>
                    {rows.map((entry) => {
                        const groups = entry.subscriptionBalances || [];
                        const open = expanded.includes(entry.personId);
                        const hasFree = !filtered && entry.unallocated > 0;
                        const canExpand = groups.length > 0 || hasFree;
                        const detailId = `${tableId}-person-${entry.personId}`;
                        return (
                            <Fragment key={entry.personId}>
                                <tbody>
                                    <tr className="balance-person-row">
                                        <td>
                                            <div className="balance-person-heading">
                                                <button type="button" className="balance-expand" disabled={!canExpand}
                                                    aria-label={`${open ? 'Свернуть' : 'Развернуть'} баланс: ${entry.fullName}`}
                                                    aria-expanded={canExpand && open} aria-controls={canExpand ? detailId : undefined}
                                                    onClick={() => toggleExpanded(entry.personId)}>
                                                    <span aria-hidden="true">{open && canExpand ? '▾' : '▸'}</span>
                                                </button>
                                                <button type="button" className="person-balance-button" onClick={() => selectPerson(entry.personId)}>
                                                    {entry.fullName}
                                                    <small>{entry.active ? 'активен' : 'неактивен'}</small>
                                                    {filtered && <small>По выбранным абонементам</small>}
                                                </button>
                                            </div>
                                            {filtered && entry.unallocated > 0 && <small className="balance-free-context muted">На счёте не распределено: {money(entry.unallocated)}</small>}
                                        </td>
                                        <td>{money(entry.charged)}</td>
                                        <td>{money(entry.paid)}</td>
                                        <td>{filtered ? '—' : money(entry.unallocated)}</td>
                                        <BalanceCell value={entry.balance} />
                                    </tr>
                                </tbody>
                                {canExpand && <tbody id={detailId} hidden={!open} className="balance-breakdown">
                                    {groups.map((group) => (
                                        <tr className="balance-subscription-row" key={subscriptionKey(group.subscriptionId)}>
                                            <td>{subscriptionTitle(group)}</td>
                                            <td>{money(group.charged)}</td>
                                            <td>{money(group.allocated)}</td>
                                            <td>—</td>
                                            <BalanceCell value={group.balance} />
                                        </tr>
                                    ))}
                                    {hasFree && <tr className="balance-unallocated-row">
                                        <td>Нераспределённые оплаты</td>
                                        <td>—</td>
                                        <td>{money(entry.unallocated)}</td>
                                        <td>{money(entry.unallocated)}</td>
                                        <BalanceCell value={-entry.unallocated} />
                                    </tr>}
                                </tbody>}
                            </Fragment>
                        );
                    })}
                    {!rows.length && <tbody><tr><td colSpan={5} className="muted">{filtered ? 'Нет начислений по выбранным абонементам.' : 'Каякеров пока нет.'}</td></tr></tbody>}
                </table>
            </div>
        </section>
    );
}
