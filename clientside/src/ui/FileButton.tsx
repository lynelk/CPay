import React from 'react';
import { UploadIcon } from './Icons';

/** iOS-styled file picker. Replaces rc-easyui FileButton. */
export function FileButton({
  onFiles,
  accept,
  multiple = false,
  children = 'Upload',
  variant = 'secondary',
}: {
  onFiles: (files: FileList) => void;
  accept?: string;
  multiple?: boolean;
  children?: React.ReactNode;
  variant?: 'primary' | 'secondary' | 'ghost';
}): React.ReactElement {
  const ref = React.useRef<HTMLInputElement>(null);
  return (
    <span className="ios-file">
      <button
        type="button"
        className={`ios-btn ios-btn--${variant} ios-btn--sm`}
        onClick={() => ref.current?.click()}
      >
        <UploadIcon size={16} />
        {children}
      </button>
      <input
        ref={ref}
        type="file"
        accept={accept}
        multiple={multiple}
        onChange={(e) => {
          if (e.target.files && e.target.files.length) onFiles(e.target.files);
          e.target.value = '';
        }}
      />
    </span>
  );
}
