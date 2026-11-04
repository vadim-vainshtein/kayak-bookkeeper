import { useLayoutEffect, useRef, useState } from 'react';
import Modal from '../../components/Modal.jsx';
import { activeRate, formatDate, money, today } from '../../shared/format.js';
import { rateLabels } from '../../shared/labels.js';

export default function Rates({ rates, create, end, submitting, readOnly = false }) {
    const [creating, setCreating] = useState(false);
    const [ending, setEnding] = useState(null);
    const [from, setFrom] = useState(today());
    const [to, setTo] = useState('');
    const [createDateError, setCreateDateError] = useState('');
    const [endDateError, setEndDateError] = useState('');
    const [endDate, setEndDate] = useState('');
    const lastEndingId = useRef(null);
    useLayoutEffect(() => {
        if (ending && lastEndingId.current !== ending.id) {
            lastEndingId.current = ending.id;
            setEndDate('');
            setEndDateError('');
        }
    }, [ending]);

    async function createSubmit(event) {
        if (to && to < from) {
            event.preventDefault();
            setCreateDateError('Дата окончания не может быть раньше даты начала.');
            return;
        }
        const ok = await create(event);
        if (ok) {
            setCreating(false);
            setFrom(today());
            setTo('');
            setCreateDateError('');
        }
    }

    async function endSubmit(event) {
        if (endDate < ending.effectiveFrom) {
            event.preventDefault();
            setEndDateError('Дата окончания не может быть раньше даты начала.');
            return;
        }
        const ok = await end(event, ending);
        if (ok) {
            setEnding(null);
            setEndDate('');
            setEndDateError('');
        }
    }

    return (
        <section className="card">
            <div className="line">
                <h2>Тарифы</h2>
                {!readOnly && <button
                    className="primary"
                    onClick={() => {
                        setCreateDateError('');
                        setCreating(true);
                    }}
                >
                    Добавить тариф
                </button>}
            </div>
            {rates.length ? (
                <ul className="list">
                    {rates.map((rate) => (
                        <li key={rate.id}>
                            <div className="line">
                                <strong>{rateLabels[rate.code]}</strong>
                                <span>{money(rate.amount)}</span>
                            </div>
                            <small className="muted">
                                {formatDate(rate.effectiveFrom)} — {formatDate(rate.effectiveTo)}{' '}
                                {activeRate(rate) && <span className="status">действует</span>}
                            </small>
                            {!readOnly && !rate.effectiveTo && (
                                <div className="list-actions">
                                    <button
                                        onClick={() => {
                                            setEndDateError('');
                                            setEnding(rate);
                                        }}
                                    >
                                        Завершить действие
                                    </button>
                                </div>
                            )}
                        </li>
                    ))}
                </ul>
            ) : (
                <p className="muted">Тарифов пока нет.</p>
            )}
            {!readOnly && <><Modal open={creating} onClose={() => setCreating(false)} title="Новый тариф">
                <form onSubmit={createSubmit}>
                    <label>
                        Услуга <span className="required">*</span>
                        <select name="code" defaultValue="POOL_RENTAL">
                            {Object.entries(rateLabels).map(([code, label]) => (
                                <option key={code} value={code}>
                                    {label}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label>
                        Сумма, ₸ <span className="required">*</span>
                        <input name="amount" type="number" min="0" required />
                    </label>
                    <div className="form-columns">
                        <label>
                            Действует с <span className="required">*</span>
                            <input
                                name="effectiveFrom"
                                type="date"
                                value={from}
                                onChange={(event) => {
                                    setFrom(event.target.value);
                                    setCreateDateError(
                                        to && to < event.target.value
                                            ? 'Дата окончания не может быть раньше даты начала.'
                                            : '',
                                    );
                                }}
                                required
                            />
                        </label>
                        <label>
                            По дату включительно
                            <input
                                name="effectiveTo"
                                type="date"
                                min={from}
                                value={to}
                                aria-invalid={Boolean(createDateError)}
                                aria-describedby={createDateError ? 'rate-date-error' : undefined}
                                onChange={(event) => {
                                    setTo(event.target.value);
                                    setCreateDateError(
                                        event.target.value && event.target.value < from
                                            ? 'Дата окончания не может быть раньше даты начала.'
                                            : '',
                                    );
                                }}
                            />
                            {createDateError && (
                                <small id="rate-date-error" className="validation-message" role="alert">
                                    {createDateError}
                                </small>
                            )}
                        </label>
                    </div>
                    <div className="dialog-actions">
                        <button className="primary" disabled={submitting}>
                            Добавить тариф
                        </button>
                    </div>
                </form>
            </Modal>
            <Modal open={Boolean(ending)} onClose={() => setEnding(null)} title="Закончить действие тарифа">
                {ending && (
                    <form onSubmit={endSubmit}>
                        <p className="muted">Тариф перестанет применяться после выбранной даты.</p>
                        <label>
                            Дата окончания <span className="required">*</span>
                            <input
                                name="effectiveTo"
                                type="date"
                                min={ending.effectiveFrom}
                                value={endDate}
                                onChange={(event) => {
                                    setEndDate(event.target.value);
                                    setEndDateError(
                                        event.target.value && event.target.value < ending.effectiveFrom
                                            ? 'Дата окончания не может быть раньше даты начала.'
                                            : '',
                                    );
                                }}
                                required
                                aria-invalid={Boolean(endDateError)}
                                aria-describedby={endDateError ? 'rate-end-error' : undefined}
                            />
                            {endDateError && (
                                <small id="rate-end-error" className="validation-message" role="alert">
                                    {endDateError}
                                </small>
                            )}
                        </label>
                        <div className="dialog-actions">
                            <button className="primary" disabled={submitting}>
                                Установить дату
                            </button>
                        </div>
                    </form>
                )}
            </Modal></>}
        </section>
    );
}
