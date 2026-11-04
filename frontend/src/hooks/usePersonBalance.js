import { useRef, useState } from 'react';
import { createBalanceSelection } from '../features/ledger/balanceSelection.js';

export default function usePersonBalance(api, setError) {
    const [personId, setPersonId] = useState(null);
    const [balance, setBalance] = useState(null);
    const apiRef = useRef(api);
    apiRef.current = api;
    const selection = useRef(null);
    if (!selection.current)
        selection.current = createBalanceSelection({
            api: (...args) => apiRef.current(...args),
            setPersonId,
            setBalance,
            setError,
        });
    return { personId, balance, ...selection.current };
}
