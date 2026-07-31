import React from 'react';

export function Checkbox({
  checked,
  onCheckedChange,
  label,
  disabled,
  ariaLabel,
}: {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  label?: React.ReactNode;
  disabled?: boolean;
  /** Accessible name for icon/label-less checkboxes (e.g. a bare "select all" header checkbox). */
  ariaLabel?: string;
}): React.ReactElement {
  return (
    <label className="ios-checkbox">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        aria-label={label == null ? ariaLabel : undefined}
        onChange={(e) => onCheckedChange(e.target.checked)}
      />
      {label != null ? <span>{label}</span> : null}
    </label>
  );
}
