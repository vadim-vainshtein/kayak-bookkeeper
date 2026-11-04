import { useCallback, useRef, useState } from 'react';
import { createMutationRunner } from './mutationRunner.js';

export default function useMutation() {
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const runner = useRef(null);
    if (!runner.current) runner.current = createMutationRunner({ setSubmitting, setError, setMessage });
    const submit = useCallback(async (form, action, success) => {
        const ok = await runner.current(action, success);
        if (ok) form.reset();
        return ok;
    }, []);
    return { submitting, error, message, setError, run: runner.current, submit };
}
