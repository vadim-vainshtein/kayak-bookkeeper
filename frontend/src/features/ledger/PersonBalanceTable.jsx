import { balanceClass, balanceLabel, signedBalance, money } from '../../shared/format.js';

export default function PersonBalanceTable({ personBalances, selectPerson }) {
    return (
        <section className="card">
            <div className="line">
                <h2>Каякеры и балансы</h2>
                <span className="muted">{personBalances.length}</span>
            </div>
            <div className="balance-table-wrap">
                <table className="balance-table">
                    <thead>
                        <tr>
                            <th>Каякер</th>
                            <th>Начислено</th>
                            <th>Оплачено</th>
                            <th>Не распределено</th>
                            <th>Баланс</th>
                        </tr>
                    </thead>
                    <tbody>
                        {personBalances.map((entry) => (
                            <tr key={entry.personId}>
                                <td>
                                    <button
                                        className="person-balance-button"
                                        onClick={() => selectPerson(entry.personId)}
                                    >
                                        {entry.fullName}
                                        <small>{entry.active ? 'активен' : 'неактивен'}</small>
                                    </button>
                                </td>
                                <td>{money(entry.charged)}</td>
                                <td>{money(entry.paid)}</td>
                                <td>{money(entry.unallocated)}</td>
                                <td
                                    className={balanceClass(entry.balance)}
                                    aria-label={balanceLabel(entry.balance)}
                                >
                                    {signedBalance(entry.balance)}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </section>
    );
}
