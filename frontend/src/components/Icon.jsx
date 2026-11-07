const paths = {
    waves: 'M2 6c3-4 5 4 8 0s5 4 8 0 4 0 4 0M2 12c3-4 5 4 8 0s5 4 8 0 4 0 4 0M2 18c3-4 5 4 8 0s5 4 8 0 4 0 4 0',
    overview: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
    subscriptions: 'M4 5h16v5a2 2 0 0 0 0 4v5H4v-5a2 2 0 0 0 0-4zM14 5v3m0 3v2m0 3v3',
    people: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.9M15 3.1a4 4 0 0 1 0 7.8M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
    ledger: 'M20 8V5a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h16v11H5a3 3 0 0 1-3-3V6M21 12h-6v4h6',
    reports: 'M4 3v18h17M9 16v-5M14 16V7M19 16v-9',
    rates: 'M3 3h8l10 10-8 8L3 11zM7 7h.01',
    more: 'M4 6h16M4 12h16M4 18h16',
    back: 'M19 12H5m7-7-7 7 7 7',
    arrow: 'M5 12h14m-7-7 7 7-7 7',
    plus: 'M12 5v14M5 12h14',
    account: 'M20 21a8 8 0 0 0-16 0M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
};

export default function Icon({ name, className = '' }) {
    return <svg className={`icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor"
        strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d={paths[name] || paths.arrow} />
    </svg>;
}
