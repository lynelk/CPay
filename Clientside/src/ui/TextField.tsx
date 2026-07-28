import React from 'react';

interface TextFieldProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'onChange'> {
  id: string;
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  invalid?: boolean;
  inputRef?: React.Ref<HTMLInputElement>;
}

export function TextField({
  id,
  label,
  value,
  onValueChange,
  invalid = false,
  type = 'text',
  inputRef,
  className = '',
  ...rest
}: TextFieldProps): React.ReactElement {
  return (
    <div className="ios-field">
      <label className="ios-field__label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        ref={inputRef}
        type={type}
        value={value}
        onChange={(e) => onValueChange(e.target.value)}
        aria-invalid={invalid || undefined}
        className={`ios-input ${invalid ? 'ios-input--invalid' : ''} ${className}`.trim()}
        {...rest}
      />
    </div>
  );
}
