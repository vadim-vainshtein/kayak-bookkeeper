// One shared guard covers mutations across all screens, including rapid double clicks.
export function createMutationRunner({ setSubmitting, setError, setMessage }) {
    let locked = false;
    return async (action, success) => {
        if (locked) return false;
        locked = true;
        setSubmitting(true);
        setError('');
        try {
            await action();
            setMessage(success);
            return true;
        } catch (error) {
            setError(error.message);
            return false;
        } finally {
            locked = false;
            setSubmitting(false);
        }
    };
}
