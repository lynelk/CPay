import React from 'react';

type Kind = 'date' | 'time' | 'datetime-local';

interface DateFieldProps {
  id: string;
  label?: string;
  value: string;
  onValueChange: (value: string) => void;
  kind?: Kind;
  invalid?: boolean;
  min?: string;
  max?: string;
}

/**
 * Native date / time / datetime input styled as an iOS field. Replaces
 * rc-easyui DateBox / TimeSpinner. `value` uses the native ISO-ish formats
 * (YYYY-MM-DD, HH:mm, YYYY-MM-DDTHH:mm).
 */
export function DateField({
  id,
  label,
  value,
  onValueChange,
  kind = 'date',
  invalid = false,
  min,
  max,
}: DateFieldProps): React.ReactElement {
  const input = (
    <input
      id={id}
      type={kind}
      value={value}
      min={min}
      max={max}
      onChange={(e) => onValueChange(e.target.value)}
      aria-invalid={invalid || undefined}
      className={`ios-input ${invalid ? 'ios-input--invalid' : ''}`.trim()}
    />
  );
  if (!label) return input;
  return (
    <div className="ios-field">
      <label className="ios-field__label" htmlFor={id}>
        {label}
      </label>
      {input}
    </div>
  );
}
