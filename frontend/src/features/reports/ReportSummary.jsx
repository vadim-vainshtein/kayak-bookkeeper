import { money } from '../../shared/format.js';

export default function ReportSummary({ report }) {
    return (
        <section className="report-summary">
            <article>
                <span>Начислено</span>
                <strong>{money(report.summary.charged)}</strong>
            </article>
            <article>
                <span>Оплачено</span>
                <strong>{money(report.summary.allocatedPayments)}</strong>
            </article>
            <article>
                <span>Недостача</span>
                <strong
                    className={
                        report.summary.outstanding > 0
                            ? 'debt'
                            : report.summary.outstanding < 0
                              ? 'credit'
                              : ''
                    }
                >
                    {money(report.summary.outstanding)}
                </strong>
            </article>
            <article>
                <span>Аренда бассейна</span>
                <strong>{money(report.summary.poolRentalCost)}</strong>
            </article>
            <article>
                <span>Прибыль клуба</span>
                <strong className={report.summary.clubProfit < 0 ? 'debt' : 'credit'}>
                    {money(report.summary.clubProfit)}
                </strong>
            </article>
            <article>
                <span>Касса сейчас</span>
                <strong className={report.summary.cashBalance < 0 ? 'debt' : 'credit'}>
                    {money(report.summary.cashBalance)}
                </strong>
            </article>
        </section>
    );
}
