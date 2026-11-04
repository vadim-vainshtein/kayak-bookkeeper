import { useEffect } from 'react';

export default function useInlineValidation() {
    useEffect(() => {
        let sequence = 0;
        const invalid = (event) => {
            const field = event.target;
            if (!(field instanceof HTMLInputElement || field instanceof HTMLSelectElement)) return;
            const id = field.dataset.validationId || `validation-${++sequence}`;
            field.dataset.validationId = id;
            let note = document.getElementById(id);
            if (!note) {
                note = document.createElement('small');
                note.id = id;
                note.className = 'validation-message';
                field.insertAdjacentElement('afterend', note);
            }
            note.textContent =
                field.type === 'number'
                    ? 'Введите целое число больше нуля.'
                    : field.type === 'date'
                      ? 'Выберите корректную дату.'
                      : 'Заполните это обязательное поле.';
            field.setAttribute('aria-invalid', 'true');
            field.setAttribute('aria-describedby', id);
        };
        const input = (event) => {
            const field = event.target;
            if (!field.dataset.validationId || !field.validity.valid) return;
            document.getElementById(field.dataset.validationId)?.remove();
            field.removeAttribute('aria-invalid');
            field.removeAttribute('aria-describedby');
        };
        document.addEventListener('invalid', invalid, true);
        document.addEventListener('input', input, true);
        return () => {
            document.removeEventListener('invalid', invalid, true);
            document.removeEventListener('input', input, true);
        };
    }, []);
}
