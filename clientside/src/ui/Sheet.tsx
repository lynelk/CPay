import React from 'react';
import { CloseIcon } from './Icons';

type SheetSize = 'sm' | 'md' | 'lg' | 'xl';

interface SheetProps {
  open: boolean;
  onClose: () => void;
  title?: React.ReactNode;
  size?: SheetSize;
  footer?: React.ReactNode;
  children: React.ReactNode;
  /** Disable close-on-overlay / Escape (e.g. required flows). */
  dismissable?: boolean;
}

/**
 * iOS sheet / modal dialog. Replaces rc-easyui Dialog + Window. Handles overlay
 * + Escape dismissal, scroll-locked body, and a scrollable content region with
 * a pinned footer.
 */
export function Sheet({
  open,
  onClose,
  title,
  size = 'sm',
  footer,
  children,
  dismissable = true,
}: SheetProps): React.ReactElement | null {
  React.useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape' && dismissable) onClose();
    }
    document.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, onClose, dismissable]);

  if (!open) return null;

  return (
    <div
      className="ios-scrim-layer"
      onMouseDown={(e) => {
        if (dismissable && e.target === e.currentTarget) onClose();
      }}
    >
      <div className={`ios-sheet ios-sheet--${size}`} role="dialog" aria-modal="true">
        {(title || dismissable) && (
          <div className="ios-sheet__header">
            {title ? <h2 className="ios-sheet__title">{title}</h2> : <span />}
            {dismissable ? (
              <button type="button" className="ios-sheet__close" onClick={onClose} aria-label="Close">
                <CloseIcon size={16} />
              </button>
            ) : null}
          </div>
        )}
        <div className="ios-sheet__body">{children}</div>
        {footer ? <div className="ios-sheet__footer">{footer}</div> : null}
      </div>
    </div>
  );
}
