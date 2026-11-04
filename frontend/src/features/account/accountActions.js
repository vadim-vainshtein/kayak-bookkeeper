import { basicAuth } from '../../api.js';
export function createAccountActions({ api, submit, user, setAuthorization }) {
    const changePassword = (event) => {
        event.preventDefault();
        const target = event.currentTarget;
        const form = new FormData(target);
        const newPassword = form.get('newPassword');
        return submit(
            target,
            async () => {
                await api('/api/account/password', {
                    method: 'PUT',
                    body: JSON.stringify({ currentPassword: form.get('currentPassword'), newPassword }),
                });
                setAuthorization(basicAuth(user, newPassword));
            },
            'Пароль обновлён. Он не сохраняется в браузере.',
        );
    };
    return { changePassword };
}
