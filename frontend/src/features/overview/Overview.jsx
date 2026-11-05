import { activeRate } from '../../shared/format.js';

export default function Overview({ people, subscriptions, rates, go, readOnly = false }) {
    const drafts = subscriptions.filter((subscription) => subscription.status === 'DRAFT').length;
    return (
        <div className="grid">
            <section className="card">
                <h3>Каякеры</h3>
                <div className="metric">{people.filter((person) => person.active).length}</div>
                <p className="muted">активных</p>
                <button className="primary" onClick={() => go('people')}>
                    К каякерам
                </button>
            </section>
            <section className="card">
                <h3>Абонементы</h3>
                <div className="metric">{subscriptions.length}</div>
                <p className="muted">{drafts ? `${drafts} ждут расчёта` : 'все обработаны'}</p>
                <button className="primary" onClick={() => go('subscriptions')}>
                    К абонементам
                </button>
            </section>
            <section className="card">
                <h3>Тарифы</h3>
                <div className="metric">{rates.filter(activeRate).length}</div>
                <p className="muted">действуют сегодня</p>
                <button className="primary" onClick={() => go('rates')}>
                    К тарифам
                </button>
            </section>
            {!readOnly && <section className="card wide">
                <h2>Что сделать дальше</h2>
                <p className="muted">
                    {people.length === 0
                        ? 'Добавьте каякеров, затем создайте абонемент.'
                        : !rates.some(activeRate)
                          ? 'Настройте действующие тарифы, чтобы рассчитать абонемент.'
                          : drafts
                            ? 'Проверьте расписание и участников черновика, затем рассчитайте его.'
                            : 'Создайте следующий абонемент или проверьте журнал оплат.'}
                </p>
            </section>}
            {readOnly && <section className="card wide">
                <h2>Данные клуба</h2>
                <p className="muted">Вам доступны все данные клуба, включая начисления, оплаты и финансовую отчётность.</p>
                <button onClick={() => go('reports')}>К финансовым отчётам</button>
            </section>}
        </div>
    );
}
