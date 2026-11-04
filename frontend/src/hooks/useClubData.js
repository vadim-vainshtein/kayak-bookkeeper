import { useCallback, useEffect, useState } from 'react';

export default function useClubData(api, authorization, setError) {
    const [people, setPeople] = useState([]);
    const [personBalances, setPersonBalances] = useState([]);
    const [subscriptions, setSubscriptions] = useState([]);
    const [rates, setRates] = useState([]);
    const [loading, setLoading] = useState(false);
    const refreshBalances = async () => setPersonBalances(await api('/api/people/balances'));
    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [nextPeople, nextBalances, nextSubscriptions, nextRates] = await Promise.all([
                api('/api/people'),
                api('/api/people/balances'),
                api('/api/subscriptions'),
                api('/api/pricing-rates'),
            ]);
            setPeople(nextPeople);
            setPersonBalances(nextBalances);
            setSubscriptions(nextSubscriptions);
            setRates(nextRates);
        } catch (error) {
            setError(error.message);
        } finally {
            setLoading(false);
        }
    }, [api, setError]);
    useEffect(() => {
        if (authorization) load();
    }, [authorization, load]);
    return { people, personBalances, subscriptions, rates, loading, load, refreshBalances };
}
