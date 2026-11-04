// Invalidate older requests whenever the selected person changes or the dialog closes.
export function createBalanceSelection({ api, setPersonId, setBalance, setError }) {
    let selectedPerson = null;
    let requestVersion = 0;
    const loadBalance = async (id, { keepCurrent = false } = {}) => {
        const version = ++requestVersion;
        if (selectedPerson !== id) return;
        if (!keepCurrent) setBalance(null);
        if (!id) return;
        try {
            const next = await api(`/api/people/${id}/balance`);
            if (version === requestVersion && selectedPerson === id) setBalance(next);
        } catch (error) {
            if (version === requestVersion && selectedPerson === id) setError(error.message);
            if (keepCurrent) throw error;
        }
    };
    const selectPerson = (id) => {
        selectedPerson = id;
        setPersonId(id);
        return loadBalance(id);
    };
    const showPeople = () => {
        selectedPerson = null;
        requestVersion += 1;
        setPersonId(null);
        setBalance(null);
    };
    return { loadBalance, selectPerson, showPeople };
}
