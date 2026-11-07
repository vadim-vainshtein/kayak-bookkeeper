import { createContext, useContext, useEffect } from 'react';

export const NavigationLock = createContext(() => {});

export function useNavigationLock(active) {
    const setLocked = useContext(NavigationLock);
    useEffect(() => {
        setLocked(active);
        return () => setLocked(false);
    }, [active, setLocked]);
}
