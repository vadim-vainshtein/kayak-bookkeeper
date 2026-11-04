export function basicAuth(login, password) {
    return `Basic ${btoa(String.fromCharCode(...new TextEncoder().encode(`${login}:${password}`)))}`;
}

export function createApi(authorization, onUnauthorized) {
    return async (path, options = {}) => {
        const response = await fetch(path, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                Authorization: authorization,
                ...(options.headers || {}),
            },
        });

        if (response.status === 401) {
            onUnauthorized();
            throw new Error('Сессия завершена.');
        }
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.message || 'Не удалось выполнить запрос.');
        }
        return response.status === 204 ? null : response.json();
    };
}
