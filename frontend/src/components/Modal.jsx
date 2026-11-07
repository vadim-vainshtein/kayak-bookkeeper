import { useId, useLayoutEffect, useRef } from 'react';

export default function Modal({ open, onClose, title, children, closeDisabled = false }) {
    const ref = useRef(null);
    const stateClosePending = useRef(false);
    const titleId = `modal-title-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;

    useLayoutEffect(() => {
        const dialog = ref.current;
        if (!dialog) return;
        if (open && !dialog.open) {
            dialog.showModal();
        } else if (!open && dialog.open) {
            // A state change owns this close.  Do not let the native `close` event
            // subsequently reopen an older dialog state via its onClose callback.
            stateClosePending.current = true;
            dialog.close();
        }
    }, [open]);

    const close = () => {
        if (closeDisabled) return;
        if (ref.current?.open) ref.current.close();
    };

    const handleClose = (event) => {
        // React makes the native non-bubbling `close` event bubble through its
        // synthetic tree. Without this, closing an inner dialog also invokes the
        // parent dialog's onClose handler and tears down the entire modal stack.
        event.stopPropagation();
        if (stateClosePending.current) {
            stateClosePending.current = false;
            return;
        }
        onClose();
    };

    return (
        <dialog
            ref={ref}
            className="modal-dialog"
            aria-labelledby={titleId}
            onClose={handleClose}
            onCancel={(event) => {
                if (closeDisabled) event.preventDefault();
            }}
            onClick={(event) => {
                if (event.target !== event.currentTarget || closeDisabled) return;
                // Native backdrop clicks target the dialog too. Padding and the
                // unused area of a full-screen dialog are still inside the form.
                const bounds = event.currentTarget.getBoundingClientRect();
                if (event.clientX < bounds.left || event.clientX > bounds.right
                    || event.clientY < bounds.top || event.clientY > bounds.bottom) close();
            }}
        >
            <div className="modal-header">
                <h2 id={titleId}>{title}</h2>
                <button type="button" onClick={close} disabled={closeDisabled} aria-label="Закрыть окно">
                    Закрыть
                </button>
            </div>
            <div>{children}</div>
        </dialog>
    );
}
