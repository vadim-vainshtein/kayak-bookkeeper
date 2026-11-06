import { useCallback, useEffect, useRef, useState } from 'react';

export default function useClubData(api, authorization, setError) {
    const [people, setPeople] = useState([]);
    const [personBalances, setPersonBalances] = useState([]);
    const [subscriptions, setSubscriptions] = useState([]);
    const [rates, setRates] = useState([]);
    const [loading, setLoading] = useState(false);
    const loadRevision = useRef(0);
    const refreshBalances = async () => setPersonBalances(await api('/api/people/balances'));
    const load = useCallback(async ({ quiet = false } = {}) => {
        const revision = ++loadRevision.current;
        if (!quiet) setLoading(true);
        try {
            const [nextPeople, nextBalances, nextSubscriptions, nextRates] = await Promise.all([
                api('/api/people'),
                api('/api/people/balances'),
                api('/api/subscriptions'),
                api('/api/pricing-rates'),
            ]);
            if (revision !== loadRevision.current) return;
            setPeople(nextPeople);
            setPersonBalances(nextBalances);
            setSubscriptions(nextSubscriptions);
            setRates(nextRates);
        } catch (error) {
            if (revision === loadRevision.current) setError(error.message);
        } finally {
            if (revision === loadRevision.current) setLoading(false);
        }
    }, [api, setError]);
    useEffect(() => {
        if (authorization) load();
    }, [authorization, load]);
    return { people, personBalances, subscriptions, rates, loading, load, refreshBalances };
}
