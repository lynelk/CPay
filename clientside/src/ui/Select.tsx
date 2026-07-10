import React from 'react';

export interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'onChange'> {
  id: string;
  label?: string;
  value: string;
  options: SelectOption[];
  onValueChange: (value: string) => void;
  placeholder?: string;
  invalid?: boolean;
}

export function Select({
  id,
  label,
  value,
  options,
  onValueChange,
  placeholder,
  invalid = false,
  className = '',
  ...rest
}: SelectProps): React.ReactElement {
  const field = (
    <div className="ios-select__wrap">
      <select
        id={id}
        value={value}
        onChange={(e) => onValueChange(e.target.value)}
        aria-invalid={invalid || undefined}
        className={`ios-input ios-select ${invalid ? 'ios-input--invalid' : ''} ${className}`.trim()}
        {...rest}
      >
        {placeholder ? (
          <option value="" disabled>
            {placeholder}
          </option>
        ) : null}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </div>
  );
  if (!label) return field;
  return (
    <div className="ios-field">
      <label className="ios-field__label" htmlFor={id}>
        {label}
      </label>
      {field}
    </div>
  );
}
