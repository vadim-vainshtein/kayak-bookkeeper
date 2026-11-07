import { useId, useLayoutEffect, useRef } from 'react';
import Icon from './Icon.jsx';

export default function DetailPage({ title, backLabel, onBack, busy = false, children, className = '' }) {
    const id = useId();
    const heading = useRef(null);
    useLayoutEffect(() => { heading.current?.focus(); }, []);
    return <section className={`detail-page ${className}`} aria-labelledby={id}>
        <button type="button" className="back-button" onClick={onBack} disabled={busy}><Icon name="back" />{backLabel}</button>
        <h2 id={id} ref={heading} tabIndex={-1} className="detail-page-title">{title}</h2>
        {children}
    </section>;
}
