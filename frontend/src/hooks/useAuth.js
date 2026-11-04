import { useMemo, useState } from 'react';
import { createApi } from '../api.js';

export default function useAuth() {
    const [authorization, setAuthorization] = useState(null);
    const [account, setAccount] = useState(null);
    const api = useMemo(() => createApi(authorization, () => setAuthorization(null)), [authorization]);
    const login = (nextAuthorization, nextAccount) => {
        setAccount(nextAccount);
        setAuthorization(nextAuthorization);
    };
    return { authorization, account, user: account?.login, api, login, logout: () => setAuthorization(null), setAuthorization };
}
